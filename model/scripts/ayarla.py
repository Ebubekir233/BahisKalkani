# -*- coding: utf-8 -*-
"""Optuna ile hiperparametre araması (İSTEĞE BAĞLI adım).

Çalıştırma (Colab, train.py'den ÖNCE):
    !pip -q install optuna
    !python model/scripts/ayarla.py            # varsayılan 40 deneme
    !python model/scripts/ayarla.py 60         # deneme sayısını değiştirmek için

Çıktı: model/cikti/en_iyi_ayarlar.json — train.py bu dosyayı bulursa
otomatik kullanır (bulamazsa varsayılanlarla eğitir; komutlarınız değişmez).

Neden çapraz doğrulama: arama tek doğrulama bölmesiyle yapılırsa seçilen
ayarlar o bölmeye "ezber" yapar (eşik seçiminde düzelttiğimiz seçim
sapmasının aynısı). Bu yüzden her deneme 3-katlı stratified CV ile ölçülür;
maliyet 3 katı ama A100'de deneme başına ~1 dk sürer. Ortanca altında kalan
denemeler erken kesilir (MedianPruner).
"""

import json
import random
import sys
from pathlib import Path

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

import numpy as np
import tensorflow as tf

sys.path.insert(0, str(Path(__file__).parent))
from train import (  # noqa: E402
    CIKTI, SEED, VOCAB_CHARS, augment, build_model, egitim_callbacks,
    to_xy, veri_yukle,
)

try:
    import optuna
except ImportError:
    sys.exit("optuna kurulu değil: pip install optuna")

DENEME = int(sys.argv[1]) if len(sys.argv) > 1 else 40
KAT = 3          # çapraz doğrulama kat sayısı
EPOCHS = 25      # arama sırasında kısa eğitim (ES zaten erken keser)


def katlar(rows: list[dict], k: int, rng: random.Random):
    """Stratified k-fold: (egitim, dogrulama) satır listeleri üretir."""
    pos = [r for r in rows if r["label"] == 1]
    neg = [r for r in rows if r["label"] == 0]
    rng.shuffle(pos)
    rng.shuffle(neg)
    for i in range(k):
        va = pos[i::k] + neg[i::k]
        tr = [r for j in range(k) if j != i for r in pos[j::k]] + \
             [r for j in range(k) if j != i for r in neg[j::k]]
        yield tr, va


def objective(trial: "optuna.Trial", rows: list[dict]) -> float:
    # v8: veri büyüdü — kapasite arama uzayı genişletildi (10 MB / 20 ms
    # bütçesi güvende: en büyük konfig bile ~250K param ≈ <1 MB kuantalı,
    # gecikme hâlâ ms altı). Küçük konfiglar da uzayda kalır; Optuna karar verir.
    a = {
        "embed_dim": trial.suggest_categorical("embed_dim", [24, 32, 48, 64]),
        "filtreler": trial.suggest_categorical("filtreler", [64, 96, 128, 160]),
        "cekirdekler": list(map(int, trial.suggest_categorical(
            "cekirdekler", ["2,3,4", "2,3,4,5", "3,4,5", "2,3,4,5,6"]).split(","))),
        "spatial_dropout": trial.suggest_float("spatial_dropout", 0.0, 0.35),
        "dropout1": trial.suggest_float("dropout1", 0.2, 0.55),
        "dropout2": trial.suggest_float("dropout2", 0.1, 0.45),
        "dense_birim": trial.suggest_categorical("dense_birim", [48, 64, 96, 128]),
        "l2": trial.suggest_float("l2", 1e-6, 1e-3, log=True),
        "lr": trial.suggest_float("lr", 3e-4, 3e-3, log=True),
        "label_smoothing": trial.suggest_categorical("label_smoothing", [0.0, 0.03, 0.05, 0.1]),
        "batch": trial.suggest_categorical("batch", [32, 64]),
        # v6: focal loss kalibrasyonu iyileştirir ve kolay örnek baskınlığını
        # kırar — Optuna bce ile karşılaştırıp karar versin
        "kayip": trial.suggest_categorical("kayip", ["focal", "bce"]),
        "focal_gamma": trial.suggest_float("focal_gamma", 1.0, 3.0),
    }
    rng = random.Random(SEED)
    vocab_size = len(VOCAB_CHARS) + 2
    aucs = []
    for kat_no, (tr, va) in enumerate(katlar(rows, KAT, rng)):
        tf.keras.backend.clear_session()
        tf.random.set_seed(SEED + kat_no)
        x_tr, y_tr = to_xy(augment(tr, rng))
        x_va, y_va = to_xy(va)
        n_pos = float(y_tr.sum())
        n_neg = float(len(y_tr) - n_pos)
        cw = {0: (n_pos + n_neg) / (2 * n_neg), 1: (n_pos + n_neg) / (2 * n_pos)}
        model = build_model(vocab_size, a)
        model.fit(x_tr, y_tr, validation_data=(x_va, y_va), epochs=EPOCHS,
                  batch_size=a["batch"], class_weight=cw,
                  callbacks=egitim_callbacks(patience=4), verbose=0)
        auc = float(model.evaluate(x_va, y_va, verbose=0, return_dict=True)["auc"])
        aucs.append(auc)
        trial.report(float(np.mean(aucs)), kat_no)
        if trial.should_prune():
            raise optuna.TrialPruned()
    return float(np.mean(aucs))


def main() -> None:
    rows = veri_yukle()
    print(f"Optuna araması: {DENEME} deneme × {KAT}-katlı CV (deneme başına ~1-2 dk GPU'da)")
    study = optuna.create_study(
        direction="maximize",
        sampler=optuna.samplers.TPESampler(seed=SEED),
        pruner=optuna.pruners.MedianPruner(n_warmup_steps=1),
    )
    study.optimize(lambda t: objective(t, rows), n_trials=DENEME, show_progress_bar=True)

    en_iyi = dict(study.best_params)
    en_iyi["cekirdekler"] = list(map(int, en_iyi["cekirdekler"].split(",")))
    CIKTI.mkdir(exist_ok=True)
    (CIKTI / "en_iyi_ayarlar.json").write_text(
        json.dumps({"ayarlar": en_iyi, "cv_auc": round(study.best_value, 4),
                    "denemeler": len(study.trials), "kat": KAT},
                   ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"\nEn iyi CV AUC: {study.best_value:.4f}")
    print(f"en_iyi_ayarlar.json yazıldı. Şimdi: python model/scripts/train.py")


if __name__ == "__main__":
    main()

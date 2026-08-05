# -*- coding: utf-8 -*-
"""BahisKalkanı tespit modeli — eğitim + TFLite dönüşümü.

Girdi:  model/data/egitim.jsonl (sentetik) + model/data/gercek.jsonl (varsa)
Çıktı:  model/cikti/model.tflite, model_vocab.json, egitim_raporu.json

Çalıştırma (Colab):  python model/scripts/train.py
İsteğe bağlı ön adım: python model/scripts/ayarla.py  (Optuna hiperparametre
araması; sonucu cikti/en_iyi_ayarlar.json'a yazar, train.py varsa onu kullanır)

Aşırı öğrenme (overfitting) önlemleri — 13 Temmuz güncellemesi:
- SpatialDropout1D (embedding kanallarını topluca düşürür — küçük veri için etkili)
- L2 ağırlık cezası (conv + dense), etiket yumuşatma (label smoothing)
- EarlyStopping (val_auc) + ReduceLROnPlateau
- Çoğaltma yalnız eğitim bölmesine (bölme ÇOĞALTMADAN ÖNCE — varyant sızıntısı yok)
- Rapora eğitim/doğrulama farkı yazılır ("asiri_ogrenme_farki") — fark
  büyükse (>~%4-5) ezber var demektir, ayarla.py koşulmalı
"""

import hashlib
import json
import os
import random
import re
import shutil
import sys
import unicodedata
from collections import Counter
from pathlib import Path

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

import numpy as np
import tensorflow as tf

# ---------------------------------------------------------------- sabitler

MODEL_DIR = Path(__file__).resolve().parent.parent
DATA = MODEL_DIR / "data" / "egitim.jsonl"          # sentetik set (kontrollü kapsam)
GERCEK = MODEL_DIR / "data" / "gercek.jsonl"        # gerçek set (varsa eklenir)
KALIBRASYON = MODEL_DIR / "data" / "kalibrasyon.jsonl"  # saha-temsili eşik seçim seti
ALANLAR = MODEL_DIR / "data" / "mesru_alanlar.json"     # meşru alan adları (allowlist)
ESIK_KARARI = MODEL_DIR / "esik_karari.json"        # insan-kararı çalışma eşiği (kalıcı)
CIKTI = MODEL_DIR / "cikti"
AYAR_DOSYASI = CIKTI / "en_iyi_ayarlar.json"        # ayarla.py (Optuna) çıktısı

MAX_LEN = 192          # kodpoint cinsinden; spec/ON_ISLEME.md ile senkron
PAD, OOV = 0, 1
SEED = 42
# Boru hattı duman testi (BK_SMOKE=1): 2 tohum × 3 epoch × alt-örneklem —
# kod yolunu uçtan uca dener, SONUÇ MODELİ DEĞİLDİR (gerçek eğitim Colab'da).
SMOKE = os.environ.get("BK_SMOKE") == "1"

# ---- URL kanalı (v2, saha yanlış alarmı düzeltmesi — bkz. spec/ON_ISLEME.md §URL)
# Model "https://" karakter dizisini teşvik sinyali olarak ezberlemişti.
# Çözüm (sektör standardı, Google Messages/Chrome deseni): URL'ler modele ham
# verilmez — meşru alan adları metinden SİLİNİR, kalan URL'ler tek 🔗 jetonuna
# indirgenir. Kotlin tarafı (TfLiteDetector) birebir aynı kuralı uygular.
URL_TOKEN = "🔗"
URL_RE = re.compile(
    r"(?i)(?:https?://|www\.|t\.me/)\S+"                                   # protokollü / www / t.me
    r"|\b[a-z0-9çğıöşü-]{2,}(?:\.[a-z0-9-]+)*\."
    r"(?:com|net|org|info|biz|xyz|bet|tv|club|site|online|top|io|me|mobi)"
    r"(?:\.tr)?\b(?:/[^\s]*)?"                                             # çıplak alan adı
    r"|\b[a-z0-9-]{2,}\.(?:gov|edu|bel|pol|av|k12)\.tr\b(?:/[^\s]*)?"      # TR kurumsal
)


def _mesru_alanlar() -> set[str]:
    if ALANLAR.exists():
        return set(json.loads(ALANLAR.read_text(encoding="utf-8"))["alanlar"])
    return set()


MESRU_ALANLAR = _mesru_alanlar()


def _alan_adi(url: str) -> str:
    """Eşleşen URL'den karşılaştırılabilir alan adını çıkarır."""
    d = re.sub(r"(?i)^https?://", "", url)
    d = re.sub(r"(?i)^www\.", "", d)
    return d.split("/")[0].split("?")[0].strip().lower()


def normalize_urls(text: str) -> str:
    """Meşru alan adlarını siler, diğer URL'leri 🔗 jetonuna çevirir."""
    def repl(m: re.Match) -> str:
        return " " if _alan_adi(m.group(0)) in MESRU_ALANLAR else f" {URL_TOKEN} "
    return URL_RE.sub(repl, text)

# ayarla.py bulursa üzerine yazar; bulamazsa bu değerlerle eğitilir
VARSAYILAN_AYARLAR = {
    "embed_dim": 32,
    "filtreler": 64,
    "cekirdekler": [2, 3, 4, 5],
    "spatial_dropout": 0.15,
    "dropout1": 0.3,
    "dropout2": 0.2,
    "dense_birim": 64,
    "l2": 1e-4,
    "lr": 1e-3,
    "label_smoothing": 0.05,
    "batch": 32,
    # v6: focal loss — kolay örneklerin (link yoğun bariz teşvikler) eğitimi
    # domine etmesini keser ve skorları daha kalibre üretir (Mukhoti 2020).
    # "bce" seçilirse label_smoothing devreye girer; Optuna ikisini de arar.
    "kayip": "focal",
    "focal_gamma": 2.0,
}

# Sözlük ON_ISLEME.md'deki tanımın tek kaynağı burasıdır; model_vocab.json
# olarak dışa yazılır ve Kotlin tarafı aynı dosyayı okur. Sıra ÖNEMLİDİR
# (id'ler sıradan türetilir) — yeni karakter eklemek gerekirse SONA ekle.
VOCAB_CHARS_V2 = (
    "abcçdefgğhıijklmnoöprsştuüvyzqwx"   # Türk alfabesi + q w x
    "0123456789"
    " .,!?;:'\"/\\()[]{}<>@#$%^&*+-_=~|₺€"
    "💚🔥🎰💰⚽🎁✅⭐📲"                  # bahis paylaşımlarında sık görülen emojiler
    "🔗"                                 # v2: URL jetonu — SONA eklendi, id'ler kaymaz
)
# v3 (18 Tem, v10.3 OOV analizi): spam'de sık 10 emoji SONA eklendi — v2
# id'leri KAYMAZ, Kotlin sözlüğü model_vocab.json'dan dinamik okuduğu için
# kod değişmez. DİKKAT: öğretmen (v8) v2 sözlükle eğitildi; öğretmen
# skorlaması CHAR2ID_V2 + preprocess_ogretmen ile yapılır (yeni id'ler v8
# embedding aralığını taşırır).
VOCAB_CHARS = VOCAB_CHARS_V2 + "😂😎🚀⚡💸👇🎉🎯➡😅"
CHAR2ID = {c: i + 2 for i, c in enumerate(VOCAB_CHARS)}       # 0=pad, 1=oov
CHAR2ID_V2 = {c: i + 2 for i, c in enumerate(VOCAB_CHARS_V2)}

# v3 tipografik katlama (spec §FOLD): ekran metinlerindeki kıvrık tırnak /
# uzun tire / NBSP / şapkalı harf varyantları sözlükteki karşılıklarına
# indirgenir; U+FE0F (emoji varyasyon seçicisi) sinyalsiz gürültüdür, silinir.
# Kotlin TfLiteDetector.fold() ile BİREBİR aynı tutulur. Büyük Â/Î/Û doğrudan
# küçük hedefe gider (fold, küçük-harf adımından ÖNCE koşar — inceleme bulgusu).
FOLD_MAP = {
    "️": "",            # emoji varyasyon seçicisi
    " ": " ",           # NBSP
    "’": "'", "‘": "'", "´": "'", "`": "'",
    "“": '"', "”": '"', "«": '"', "»": '"', "„": '"',
    "–": "-", "—": "-",
    "…": "...",
    "•": ".", "·": ".",
    "â": "a", "î": "i", "û": "u", "Â": "a", "Î": "i", "Û": "u",
}
_FOLD_TABLE = {ord(k): v for k, v in FOLD_MAP.items()}


def fold(text: str) -> str:
    """spec §FOLD (v3) — idempotenttir."""
    return text.translate(_FOLD_TABLE)

# Sansür ikamesi (KeywordDetector'dakiyle aynı harita) — burada TERSİNE,
# veri çoğaltma için kullanılır: temiz pozitiflerden sansürlü varyant üretir.
CENSOR = {"o": "0", "i": "1", "e": "3", "a": "4", "s": "5", "t": "7"}
CENSOR_EXTRA = {"a": "@", "s": "$"}
ASCII_FOLD = {"ç": "c", "ğ": "g", "ı": "i", "ö": "o", "ş": "s", "ü": "u"}
SPACED_KEYWORDS = [
    "bahis", "iddaa", "casino", "kumar", "rulet", "bonus",
    "çevrim", "freebet", "jackpot", "kazino",
]

# ---------------------------------------------------------------- ön işleme


def turkish_lower(text: str) -> str:
    """Kotlin'deki lowercase(Locale("tr")) ile birebir aynı sonucu verir.

    Python'un .lower()'ı 'I'→'i' yapar (Türkçede 'ı' olmalı) ve 'İ'→'i̇'
    (i + U+0307) bırakır; ikisi de elle düzeltilir.
    """
    text = text.replace("İ", "i").replace("I", "ı")
    text = text.lower()
    return text.replace("̇", "")


def preprocess(text: str) -> list[int]:
    """Metin → id dizisi. Spec: spec/ON_ISLEME.md (Kotlin ile adım adım aynı).

    v3 sırası: NFC → fold (tipografik katlama + U+FE0F silme) → URL
    normalizasyonu (meşru alan sil / diğerini 🔗 yap) → Türkçe küçük harf
    → kodpoint→id (sözlük v3) → 192'ye kes/doldur.
    """
    text = unicodedata.normalize("NFC", text)
    text = fold(text)
    text = normalize_urls(text)
    text = turkish_lower(text)
    ids = [CHAR2ID.get(ch, OOV) for ch in text][:MAX_LEN]
    return ids + [PAD] * (MAX_LEN - len(ids))


def preprocess_ogretmen(text: str) -> list[int]:
    """v2 ön işleme (fold YOK, sözlük v2) — YALNIZ öğretmen (v8) skorlaması
    için: ogretmen_v8.tflite bu düzenle eğitildi; v3'ün yeni emoji id'leri
    (88-97) v8'in embedding aralığını (88) taşırırdı."""
    text = unicodedata.normalize("NFC", text)
    text = normalize_urls(text)
    text = turkish_lower(text)
    ids = [CHAR2ID_V2.get(ch, OOV) for ch in text][:MAX_LEN]
    return ids + [PAD] * (MAX_LEN - len(ids))


# ---------------------------------------------------------------- veri


def veri_yukle() -> list[dict]:
    """Sentetik + (varsa) gerçek eğitim verisini yükler. ayarla.py de kullanır."""
    rows = [json.loads(line) for line in DATA.read_text(encoding="utf-8").splitlines() if line.strip()]
    print(f"Sentetik eğitim verisi: {len(rows)} örnek "
          f"({sum(r['label'] for r in rows)} pozitif)")
    if GERCEK.exists():
        gercek = [json.loads(l) for l in GERCEK.read_text(encoding="utf-8").splitlines() if l.strip()]
        rows += [
            {"text": r["text"], "label": r["label"],
             **({"agirlik": r["agirlik"]} if "agirlik" in r else {})}
            for r in gercek
        ]
        print(f"Gerçek eğitim verisi: {len(gercek)} örnek eklendi "
              f"({sum(r['label'] for r in gercek)} pozitif) — toplam {len(rows)}")
    return rows


def ayarlari_yukle() -> dict:
    a = dict(VARSAYILAN_AYARLAR)
    if AYAR_DOSYASI.exists():
        kayit = json.loads(AYAR_DOSYASI.read_text(encoding="utf-8"))
        a.update(kayit["ayarlar"])
        cv = kayit.get("cv_auc")
        print(f"Ayarlar: Optuna araması (cv_auc={cv:.4f}, " if cv is not None
              else "Ayarlar: Optuna araması (cv_auc=?, ", end="")
        print(f"{kayit.get('denemeler')} deneme) — {AYAR_DOSYASI.name}")
    else:
        print("Ayarlar: varsayılan (isteğe bağlı arama için: python model/scripts/ayarla.py)")
    return a


# ---------------------------------------------------------------- veri çoğaltma


def censor_variant(text: str, rng: random.Random) -> str:
    out = []
    for ch in text:
        lower = ch.lower()
        if lower in CENSOR and rng.random() < 0.45:
            if lower in CENSOR_EXTRA and rng.random() < 0.3:
                out.append(CENSOR_EXTRA[lower])
            else:
                out.append(CENSOR[lower])
        else:
            out.append(ch)
    return "".join(out)


def ascii_variant(text: str) -> str:
    return "".join(ASCII_FOLD.get(ch, ASCII_FOLD.get(ch.lower(), ch)) for ch in text)


def spaced_variant(text: str, rng: random.Random) -> str | None:
    lower = turkish_lower(text)
    for kw in SPACED_KEYWORDS:
        idx = lower.find(kw)
        if idx >= 0:
            sep = rng.choice([" ", ".", "-"])
            word = text[idx : idx + len(kw)]
            return text[:idx] + sep.join(word) + text[idx + len(kw) :]
    return None


# Karşı-olgusal enjeksiyon için meşru-görünümlü ama allowlist DIŞI adresler
# (🔗 jetonuna dönüşürler): model "link var = teşvik" bağıntısı kuramasın diye
# negatiflere de link eklenir, pozitiflerin linksiz kopyaları korunur.
ENJEKSIYON_URLLERI = [
    "https://ornek-blog.net", "www.tarifdefteri-ornek.com", "https://habercim-ornek.xyz",
    "gezirehberi-ornek.net", "https://teknoblog-ornek.site", "www.oyunhaber-ornek.club",
]


def augment(rows: list[dict], rng: random.Random) -> list[dict]:
    """Pozitiflere yoğun, negatiflere hafif gürültü ekler.

    Negatiflere de sansür/ascii varyantı eklenir ki model "rakamlı yazım =
    bahis" gibi sahte bir bağıntı öğrenmesin (oyuncu dili 'n00b' masumdur).
    v6 karşı-olgusal kuralları: URL'li pozitifin URL'siz kopyası pozitif
    kalır (teşvik dilden öğrenilsin), URL'siz metne link eklenmiş kopya
    etiketini KORUR (link varlığı tek başına sinyal olmasın).
    """
    out = list(rows)
    for r in rows:
        text, label = r["text"], r["label"]
        agirlik = r.get("agirlik", 1.0)
        # --- v6 karşı-olgusal çoğaltma (her iki sınıf)
        if URL_RE.search(text):
            if label == 1 and rng.random() < 0.7:
                urlsuz = " ".join(URL_RE.sub(" ", text).split())
                if len(urlsuz) >= 5:
                    out.append({"text": urlsuz, "label": 1, "agirlik": agirlik})
        elif rng.random() < (0.30 if label == 0 else 0.25) and len(text) < 170:
            out.append({"text": f"{text} {rng.choice(ENJEKSIYON_URLLERI)}",
                        "label": label, "agirlik": agirlik})
        if label == 1:
            if rng.random() < 0.55:
                out.append({"text": censor_variant(text, rng), "label": 1, "agirlik": agirlik})
            if rng.random() < 0.60:
                out.append({"text": ascii_variant(text), "label": 1, "agirlik": agirlik})
            if rng.random() < 0.50:
                sp = spaced_variant(text, rng)
                if sp:
                    out.append({"text": sp, "label": 1, "agirlik": agirlik})
        else:
            # negatif tarafta hafif tutulur; "aksansız/rakamlı yazım = bahis"
            # sahte bağıntısına karşı asıl çözüm organik aksansız örnekler
            if rng.random() < 0.15:
                out.append({"text": censor_variant(text, rng), "label": 0, "agirlik": agirlik})
            if rng.random() < 0.30:
                out.append({"text": ascii_variant(text), "label": 0, "agirlik": agirlik})
    # çoğaltma sonrası tekilleştir
    seen, dedup = set(), []
    for r in out:
        key = " ".join(turkish_lower(r["text"]).split())
        if key and key not in seen:
            seen.add(key)
            dedup.append(r)
    return dedup


def to_xy(rs: list[dict]):
    x = np.array([preprocess(r["text"]) for r in rs], dtype=np.int32)
    y = np.array([r["label"] for r in rs], dtype=np.float32)
    return x, y


def to_w(rs: list[dict], class_weight: dict) -> np.ndarray:
    """Örnek ağırlığı = satırdaki 'agirlik' (kazılmış zor negatif > 1)
    × sınıf dengesi ağırlığı; Keras'a tek kanaldan (sample_weight) verilir."""
    return np.array(
        [r.get("agirlik", 1.0) * class_weight[r["label"]] for r in rs],
        dtype=np.float32,
    )


# ---------------------------------------------------------------- eşik seçimi


def en_uzun_plato(adaylar: list[tuple[float, float]]) -> tuple[float, tuple[float, float]]:
    """(eşik, metrik) listesinde metriği maksimum yapan EN UZUN BİTİŞİK bloğu
    bulur; (blok ortası eşik, (blok başı, blok sonu)) döner.

    Maksimum kümesi kopuk olabilir (az örnekte metrik eşiğe göre monoton
    değildir); bitişik bloğun ortası, küçük skor oynamalarına en dayanıklı
    eşiği verir.
    """
    best = max(m for _, m in adaylar)
    en_iyi = None
    i = 0
    while i < len(adaylar):
        if adaylar[i][1] == best:
            j = i
            while j + 1 < len(adaylar) and adaylar[j + 1][1] == best:
                j += 1
            if en_iyi is None or (j - i) > (en_iyi[1] - en_iyi[0]):
                en_iyi = (i, j)
            i = j + 1
        else:
            i += 1
    i, j = en_iyi
    return adaylar[(i + j) // 2][0], (adaylar[i][0], adaylar[j][0])


# ---------------------------------------------------------------- model


def focal_bce(gamma: float):
    """İkili focal loss (Lin 2017 / kalibrasyon: Mukhoti 2020), YUMUŞAK-ETİKET
    destekli form: y·(1-p)^γ·(-log p) + (1-y)·p^γ·(-log(1-p)). Sert y'de (0/1)
    klasik focal'a iner. Eski pt-formu churn çıpasının yumuşak hedeflerinde
    (ör. y=0.79) iç optimumsuzdu — kayıp p'yi 0/1 uca itiyordu (kod-inceleme
    bulgusu); bu form yumuşak hedefte de doğru optimuma sahiptir."""
    def loss(y_true, y_pred):
        y_pred = tf.clip_by_value(y_pred, 1e-7, 1.0 - 1e-7)
        poz = y_true * tf.pow(1.0 - y_pred, gamma) * (-tf.math.log(y_pred))
        neg = (1.0 - y_true) * tf.pow(y_pred, gamma) * (-tf.math.log(1.0 - y_pred))
        return poz + neg
    return loss


class SertMetrik(tf.keras.metrics.Metric):
    """İç metriğe y_true'yu 0.5'te SERTLEŞTİREREK iletir — churn çıpasının
    yumuşak hedefleri gösterge metriklerini bozmasın (kayıp yumuşak kalır)."""

    def __init__(self, ic_metrik):
        super().__init__(name=ic_metrik.name)
        self.ic = ic_metrik

    def update_state(self, y_true, y_pred, sample_weight=None):
        return self.ic.update_state(
            tf.cast(y_true >= 0.5, tf.float32), y_pred, sample_weight)

    def result(self):
        return self.ic.result()

    def reset_state(self):
        self.ic.reset_state()


def build_model(vocab_size: int, a: dict, ad: str | None = None) -> tf.keras.Model:
    # ad: topluluk üyeleri için BENZERSİZ model adı ZORUNLU — clear_session
    # ad sayaçlarını sıfırladığından adsız 5 üye de "functional" olur ve
    # topluluk_kur Keras ad-teklik denetiminde ValueError ile düşer
    # (kod-inceleme bulgusu, TF 2.21/Keras 3'te koşarak doğrulandı).
    l2 = tf.keras.regularizers.l2(a["l2"]) if a["l2"] else None
    inp = tf.keras.Input(shape=(MAX_LEN,), dtype="int32", name="metin_idleri")
    x = tf.keras.layers.Embedding(vocab_size, a["embed_dim"], name="karakter_embedding")(inp)
    x = tf.keras.layers.SpatialDropout1D(a["spatial_dropout"])(x)
    pools = []
    for k in a["cekirdekler"]:
        c = tf.keras.layers.Conv1D(a["filtreler"], k, activation="relu",
                                   kernel_regularizer=l2, name=f"conv_k{k}")(x)
        pools.append(tf.keras.layers.GlobalMaxPooling1D()(c))
    x = tf.keras.layers.Concatenate()(pools) if len(pools) > 1 else pools[0]
    x = tf.keras.layers.Dropout(a["dropout1"])(x)
    x = tf.keras.layers.Dense(a["dense_birim"], activation="relu", kernel_regularizer=l2)(x)
    x = tf.keras.layers.Dropout(a["dropout2"])(x)
    out = tf.keras.layers.Dense(1, activation="sigmoid", name="tesvik_skoru")(x)
    model = tf.keras.Model(inp, out, name=ad)
    if a.get("kayip", "focal") == "focal":
        kayip = focal_bce(a.get("focal_gamma", 2.0))
    else:
        kayip = tf.keras.losses.BinaryCrossentropy(label_smoothing=a["label_smoothing"])
    model.compile(
        optimizer=tf.keras.optimizers.Adam(a["lr"]),
        loss=kayip,
        # Sert-etiket sarmalayıcıları: churn çıpası eğitim hedeflerini
        # YUMUŞATIR (0.7·etiket+0.3·öğretmen); ham metrikler yumuşak hedefle
        # anlamsız görünür (acc≈0 kozmetiği). y_true 0.5'te sertleştirilir —
        # kayıp yumuşak kalır, gösterge doğru olur.
        metrics=[
            SertMetrik(tf.keras.metrics.BinaryAccuracy(name="acc")),
            SertMetrik(tf.keras.metrics.AUC(name="auc")),
            SertMetrik(tf.keras.metrics.Precision(name="kesinlik")),
            SertMetrik(tf.keras.metrics.Recall(name="duyarlilik")),
        ],
    )
    return model


# ---------------------------------------------------------------- v10: kararlılık + churn koruması
# v9 dersi: karşı-negatif eklerken recall çöktü ve kapı sonucu artefakta
# özgü/kırılgandı. Üç önlem (kaynaklar YOL_HARITASI v10 bölümünde):
#  1. Öğretmen çıpası (churn distilasyonu): v8'in DOĞRU bildiği örneklerde
#     hedef, sert etiket yerine α·etiket+(1-α)·v8_skoru olur — BCE hedefte
#     doğrusal olduğundan bu, kayba KL-çıpa eklemekle birebir aynıdır.
#  2. SWA: son epoch'ların ağırlık ortalaması — koşudan koşuya varyansı düşürür.
#  3. Çoklu tohum: TOHUMLAR ile eğit, seçimi KALİBRASYON setinde yap
#     (kapı setinde ASLA — kazananın laneti), kazananı dışa aktar.

TOHUMLAR = [42, 133, 7, 2025, 777]   # v10.2: 5 üyeli TOPLULUK (ensemble)
DISTIL_ALPHA = 0.7          # sert etiket payı; (1-α) öğretmen payı
RECALL_HEDEF = 0.90         # eşik seçimi: sistem recall'u bunun altına düşemez
SWA_SON_ORAN = 0.25         # son %25 epoch ortalanır
OGRETMEN = CIKTI / "ogretmen_v8.tflite"   # sabit çıpa (her koşuda değişmesin)


class SWACallback(tf.keras.callbacks.Callback):
    """SWA v2 (kod-inceleme bulguları üzerine yeniden tasarım; BatchNorm yok →
    istatistik tazeleme gerekmez).

    Eski hâlin iki kusuru vardı: (1) on_train_end, EarlyStopping'in geri
    yüklediği en-iyi ağırlıkları SORGUSUZ eziyordu; (2) pencere PLANLANAN 60
    epoch'a çıpalıydı — erken durmada ya hiç dolmuyordu (sessiz devre dışı,
    üyeler arası tutarsız rejim) ya da tamamı zirve-sonrası epoch'lardan
    oluşuyordu. Yeni tasarım: her epoch anlık görüntü + val_auc kaydedilir;
    eğitim bitince en-iyi epoch'ta BİTEN ~%25'lik (fiili epoch sayısına göre)
    pencerenin ortalaması alınır ve YALNIZ doğrulama AUC'si geri yüklenen
    en-iyi ağırlıklardan kötü değilse uygulanır. Hangi rejimin kazandığı
    loglanır ve `durum` üzerinden rapora yazılır."""

    def __init__(self, x_va, y_va):
        super().__init__()
        self.x_va, self.y_va = x_va, y_va
        self.anlik, self.aucs = [], []
        self.durum = {"uygulandi": False, "fiili_epoch": 0, "pencere": None,
                      "val_auc_swa": None, "val_auc_en_iyi": None}

    def on_epoch_end(self, epoch, logs=None):
        self.anlik.append([w.copy() for w in self.model.get_weights()])
        self.aucs.append(float((logs or {}).get("val_auc", 0.0)))

    def _val_auc(self) -> float:
        m = tf.keras.metrics.AUC()
        m.update_state(self.y_va, self.model.predict(self.x_va, verbose=0).ravel())
        return float(m.result())

    def on_train_end(self, logs=None):
        # Bu noktada EarlyStopping en-iyi ağırlıkları çoktan geri yüklemiştir.
        fiili = len(self.anlik)
        self.durum["fiili_epoch"] = fiili
        if fiili < 3:
            print(f"SWA: UYGULANMADI (fiili epoch {fiili} < 3) — en-iyi ağırlıklar korundu")
            return
        en_iyi = int(np.argmax(self.aucs))
        k = max(2, int(round(fiili * SWA_SON_ORAN)))
        bas = max(0, en_iyi - k + 1)
        pencere = self.anlik[bas : en_iyi + 1]
        if len(pencere) < 2:
            print("SWA: UYGULANMADI (en iyi epoch başta, pencere < 2) — en-iyi ağırlıklar korundu")
            return
        onceki = self.model.get_weights()
        auc_en_iyi = self._val_auc()
        ort = [np.mean([w[i] for w in pencere], axis=0).astype("float32")
               for i in range(len(pencere[0]))]
        self.model.set_weights(ort)
        auc_swa = self._val_auc()
        self.durum.update(val_auc_swa=round(auc_swa, 4), val_auc_en_iyi=round(auc_en_iyi, 4))
        if auc_swa >= auc_en_iyi:
            self.durum.update(uygulandi=True, pencere=[bas + 1, en_iyi + 1])
            print(f"SWA: epoch {bas+1}-{en_iyi+1} ({len(pencere)}) ortalandı ve UYGULANDI "
                  f"(val_auc {auc_en_iyi:.4f}→{auc_swa:.4f})")
        else:
            self.model.set_weights(onceki)
            print(f"SWA: ortalama en-iyiden kötü (val_auc {auc_swa:.4f} < {auc_en_iyi:.4f}) "
                  "— en-iyi ağırlıklar korundu")


def ogretmen_skorlari(texts: list[str]) -> "np.ndarray | None":
    """v8 öğretmen modelinin skorları (yoksa None → distilasyon atlanır).

    Çıpa SABİTTİR: eski koddaki cikti/model.tflite yedeği kaldırıldı —
    model kendi çıktısıyla damıtılınca çıpa kayar (self-distillation drift,
    kod-inceleme bulgusu). Skorlama preprocess_ogretmen (v2) ile yapılır:
    v8, v2 sözlüğüyle eğitildi; v3 id'leri embedding aralığını taşırır."""
    if not OGRETMEN.exists():
        print("### UYARI: ogretmen_v8.tflite YOK — churn distilasyonu ATLANDI (çıpasız koşu!) ###")
        return None
    # model_content: Windows'ta Türkçe karakterli yol TFLite dosya açıcısını
    # düşürüyor; baytları Python okuyup içerikten yüklemek her yerde çalışır
    interp = tf.lite.Interpreter(model_content=OGRETMEN.read_bytes())
    interp.resize_tensor_input(interp.get_input_details()[0]["index"], [1, MAX_LEN])
    interp.allocate_tensors()
    inp, out = interp.get_input_details()[0], interp.get_output_details()[0]
    s = np.empty(len(texts), dtype=np.float32)
    for i, t in enumerate(texts):
        interp.set_tensor(inp["index"], np.array([preprocess_ogretmen(t)], dtype=inp["dtype"]))
        interp.invoke()
        s[i] = interp.get_tensor(out["index"])[0][0]
    print(f"Öğretmen ({OGRETMEN.name}, v2 ön işleme): {len(texts)} örnek skorlandı")
    return s


def _kelime_fn():
    try:
        from kelime import keyword_isbetting
        return keyword_isbetting
    except Exception:
        return lambda _t: False


def sistem_esik_recall_kisitli(p, rows):
    """ÜRETİM SİSTEMİ (kesin-kelime VEYA model) üzerinde eşik seçimi:
    sistem recall'u RECALL_HEDEF altına düşmeden en yüksek precision'ı veren
    eşik (v9 dersi: karşı-negatif eklenince önsel kayar; recall kısıtı
    'Bahisleri kaçırmayın' tipi bariz pozitiflerin kaçmasını yapısal engeller).
    p: rows ile hizalı 0..1 model skorları — Keras VEYA kuantalı TFLite'tan
    gelebilir (eşik nihai olarak ÜRETİM ARTEFAKTININ skorlarıyla seçilir,
    kod-inceleme bulgusu: float eşik kuantalı modelde doğrulanmıyordu).
    Döndürür: (esik, tarama, uygun_mu)."""
    kwf = _kelime_fn()
    yv = np.array([r["label"] for r in rows])
    kw = np.array([kwf(r["text"]) for r in rows])
    tarama, uygunlar = [], []
    for e in [round(0.30 + i * 0.02, 2) for i in range(36)]:
        pred = kw | (p >= e)
        tp = int((pred & (yv == 1)).sum())
        fp = int((pred & (yv == 0)).sum())
        fn = int((~pred & (yv == 1)).sum())
        rec = tp / max(1, tp + fn)
        prec = tp / max(1, tp + fp)
        fpr = fp / max(1, int((yv == 0).sum()))
        tarama.append((e, fpr, 1 - rec, prec))
        if rec >= RECALL_HEDEF:
            uygunlar.append((prec, e))
    if uygunlar:
        prec, esik = max(uygunlar)          # eşitlikte yüksek eşik (precision tarafı)
        return esik, tarama, True
    # hiçbir eşik recall hedefini tutmuyorsa en yüksek recall'lu eşiğe düş
    esik = min(tarama, key=lambda s: s[2])[0]
    return esik, tarama, False


def topluluk_kur(uyeler: list[tf.keras.Model]) -> tf.keras.Model:
    """v10.2: 5 tohumun skor ORTALAMASINI alan tek model (tek TFLite dosyası).

    Neden: tek-tohum artefaktında karar sınırı her eğitimde hafifçe kayıyor
    ve sıfır-tolerans kapıyı her turda FARKLI tek örnek deviriyordu
    (köstebek-vurmaca). Ortalama, tohum varyansını ~1/√k küçültür; sınır
    örnekleri (0.6-0.8 bandı) kararlılaşır (underspecification literatürü).
    Boyut: 5 × ~108 KB ≈ 550 KB — 10 MB sınırının çok altında; gecikme
    ~0.5 ms — 20 ms bütçesinin çok altında. Kotlin tarafı DEĞİŞMEZ (tek dosya).
    """
    inp = tf.keras.Input(shape=(MAX_LEN,), dtype="int32", name="metin_idleri")
    ciktilar = [m(inp) for m in uyeler]
    ort = tf.keras.layers.Average(name="topluluk_ortalama")(ciktilar)
    return tf.keras.Model(inp, ort, name="topluluk")


def tohum_sec(adaylar: list[dict], secim_rows: list[dict]):
    """Tohum seçimi KALİBRASYON setinde (kapı setlerinde ASLA — kazananın
    laneti): her aday kendi recall-kısıtlı eşiğinde değerlendirilir; önce
    recall kısıtını tutanlar, sonra en yüksek precision."""
    en_iyi = None
    for a in adaylar:
        p = a["model"].predict(to_xy(secim_rows)[0], verbose=0).ravel()
        esik, tarama, uygun = sistem_esik_recall_kisitli(p, secim_rows)
        prec = next(s[3] for s in tarama if s[0] == esik)
        fpr = next(s[1] for s in tarama if s[0] == esik)
        skor = (1 if uygun else 0, prec, -fpr)
        a["secim"] = {"esik": esik, "uygun": uygun, "precision": round(prec, 4), "fp_orani": round(fpr, 4)}
        if en_iyi is None or skor > en_iyi[0]:
            en_iyi = (skor, a)
    kazanan = en_iyi[1]
    neden = (f"kalibrasyonda recall≥{RECALL_HEDEF} kısıtı altında en yüksek precision "
             f"({kazanan['secim']['precision']}, eşik {kazanan['secim']['esik']})")
    return kazanan["model"], {"tohum": kazanan["tohum"], "neden": neden,
                              "adaylar": [{"tohum": a["tohum"], **a["secim"],
                                           "val_acc": round(a["val"]["acc"], 4)} for a in adaylar]}


def sizinti_kontrol(rows: list[dict]) -> None:
    """Kod düzeyinde kapı: eğitim ∩ (kabul setleri + kalibrasyon) BOŞ olmalı.
    Sızıntı, kapı sonuçlarını sessizce şişirir — bulunursa eğitim durdurulur."""
    def anah(t: str) -> str:
        return " ".join(turkish_lower(unicodedata.normalize("NFC", t)).split())

    egit = {anah(r["text"]) for r in rows}
    for ad in ["kabul_testi.jsonl", "kabul_gercek.jsonl", "kabul_saha.jsonl",
               "kalibrasyon.jsonl"]:
        p = MODEL_DIR / "data" / ad
        if not p.exists():
            continue
        test = {anah(json.loads(l)["text"])
                for l in p.read_text(encoding="utf-8").splitlines() if l.strip()}
        kes = egit & test
        if kes:
            ornek = next(iter(kes))
            sys.exit(f"### SIZINTI: eğitim ∩ {ad} = {len(kes)} örnek "
                     f"(ör. {ornek[:60]!r}) — EĞİTİM DURDURULDU ###")
    print("Sızıntı kontrolü: eğitim ∩ test/kalibrasyon = 0 ✓")


def veri_parmak_izi() -> str:
    """Eğitim+kalibrasyon verisinin sha256 kısa özeti — artefakt↔veri eşlemesi
    için rapora yazılır (tekrarlanabilirlik)."""
    h = hashlib.sha256()
    for p in [DATA, GERCEK, KALIBRASYON]:
        if p.exists():
            h.update(p.read_bytes())
    return h.hexdigest()[:12]


def egitim_callbacks(patience: int = 8) -> list:
    return [
        tf.keras.callbacks.EarlyStopping(
            monitor="val_auc", mode="max", patience=patience, restore_best_weights=True,
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_auc", mode="max", factor=0.5, patience=3, min_lr=1e-5,
        ),
    ]


# ---------------------------------------------------------------- akış


def main() -> None:
    # Paket sürüm damgası — YANLIŞ/ESKİ zip'le koşmayı anında görünür kılar
    # (iki kez yaşandı: Colab'da eski model_colab.zip açılıp bayat paket eğitildi)
    paket = MODEL_DIR / "paket_bilgisi.json"
    if paket.exists():
        pb = json.loads(paket.read_text(encoding="utf-8"))
        print(f"### PAKET: {pb['surum']} ({pb['olusturma']}) — beklenen eğitim toplamı {pb['beklenen_egitim_toplami']} ###")
    else:
        print("### UYARI: paket_bilgisi.json YOK — büyük olasılıkla ESKİ zip açıldı! ###")

    if SMOKE:
        print("### DUMAN TESTİ (BK_SMOKE=1): 2 tohum × 3 epoch × alt-örneklem — "
              "SONUÇ MODELİ DEĞİLDİR ###")

    rng = random.Random(SEED)
    np.random.seed(SEED)

    rows = veri_yukle()
    if paket.exists() and len(rows) != pb["beklenen_egitim_toplami"]:
        print(f"### UYARI: eğitim toplamı {len(rows)} ≠ beklenen {pb['beklenen_egitim_toplami']} — "
              "veri dosyaları paketle uyumsuz (eski zip karışmış olabilir)! ###")
    sizinti_kontrol(rows)
    ayarlar = ayarlari_yukle()

    # katmanlı (stratified) bölme: %85 eğitim, %15 doğrulama — ÇOĞALTMADAN ÖNCE
    # bölünür ki aynı cümlenin varyantları iki tarafa sızmasın. Bölme tüm
    # tohumlar için AYNI tutulur (adil karşılaştırma).
    pos = [r for r in rows if r["label"] == 1]
    neg = [r for r in rows if r["label"] == 0]
    rng.shuffle(pos)
    rng.shuffle(neg)
    if SMOKE:
        pos, neg = pos[:300], neg[:300]
    cut_p, cut_n = int(len(pos) * 0.85), int(len(neg) * 0.85)
    train_rows = augment(pos[:cut_p] + neg[:cut_n], rng)
    val_rows = pos[cut_p:] + neg[cut_n:]
    rng.shuffle(train_rows)
    print(f"Çoğaltma sonrası eğitim: {len(train_rows)}, doğrulama: {len(val_rows)}")

    x_tr, y_tr = to_xy(train_rows)
    x_va, y_va = to_xy(val_rows)

    # --- v10 churn çıpası: v8'in doğru bildiği örneklerde yumuşak hedef
    t_tr = ogretmen_skorlari([r["text"] for r in train_rows])
    if t_tr is not None:
        dogru = (t_tr >= 0.5) == (y_tr >= 0.5)
        y_egitim = np.where(dogru, DISTIL_ALPHA * y_tr + (1 - DISTIL_ALPHA) * t_tr, y_tr)
        print(f"Churn çıpası aktif: {int(dogru.sum())}/{len(y_tr)} örnekte yumuşak hedef "
              f"(α={DISTIL_ALPHA}); öğretmenin yanıldığı {int((~dogru).sum())} örnek sert etiketle")
    else:
        y_egitim = y_tr

    n_pos = float(y_tr.sum())
    n_neg = float(len(y_tr) - n_pos)
    class_weight = {0: (n_pos + n_neg) / (2 * n_neg), 1: (n_pos + n_neg) / (2 * n_pos)}
    w_tr = to_w(train_rows, class_weight)  # sınıf dengesi × kazılmış-zorluk ağırlığı

    vocab_size = len(VOCAB_CHARS) + 2

    kal_rows = None
    if KALIBRASYON.exists():
        kal_rows = [json.loads(l) for l in KALIBRASYON.read_text(encoding="utf-8").splitlines() if l.strip()]

    # --- v10: çoklu tohum — her tohum eğitilir, seçim KALİBRASYON setinde
    # (kapı setlerinde ASLA) yapılır; kazanan dışa aktarılır.
    EPOCHS = 3 if SMOKE else 60
    tohumlar_etkin = TOHUMLAR[:2] if SMOKE else TOHUMLAR
    adaylar_tohum = []
    for tohum in tohumlar_etkin:
        tf.keras.backend.clear_session()
        tf.random.set_seed(tohum)
        # ad=f"uye_{tohum}" ZORUNLU: clear_session ad sayaçlarını sıfırlar,
        # adsız üyelerin hepsi "functional" olur ve topluluk_kur ValueError
        # ile düşerdi (kod-inceleme bulgusu, koşarak doğrulandı)
        model = build_model(vocab_size, ayarlar, ad=f"uye_{tohum}")
        if tohum == tohumlar_etkin[0]:
            model.summary()
        print(f"\n=== Tohum {tohum} eğitiliyor…")
        swa = SWACallback(x_va, y_va)
        model.fit(
            x_tr, y_egitim,
            sample_weight=w_tr,
            validation_data=(x_va, y_va),
            epochs=EPOCHS,
            batch_size=ayarlar["batch"],
            callbacks=egitim_callbacks() + [swa],
            verbose=2,
        )
        vm = {k: float(v) for k, v in model.evaluate(x_va, y_va, verbose=0, return_dict=True).items()}
        adaylar_tohum.append({"tohum": tohum, "model": model, "val": vm, "swa": swa.durum})
        print(f"Tohum {tohum}: val acc {vm['acc']:.4f}, auc {vm['auc']:.4f}")

    # v10.2: seçim YOK — 5 üyenin ortalaması NİHAİ model (tek-tohum
    # varyansının kapıyı her turda farklı örnekle devirmesine son verir)
    model = topluluk_kur([a["model"] for a in adaylar_tohum])
    model.compile(loss="binary_crossentropy",
                  metrics=[SertMetrik(tf.keras.metrics.BinaryAccuracy(name="acc")),
                           SertMetrik(tf.keras.metrics.AUC(name="auc")),
                           SertMetrik(tf.keras.metrics.Precision(name="kesinlik")),
                           SertMetrik(tf.keras.metrics.Recall(name="duyarlilik"))])
    secim_bilgi = {"yontem": f"{len(adaylar_tohum)}-uyeli topluluk ortalamasi (secim yok)",
                   "uyeler": [{"tohum": a["tohum"], "val_acc": round(a["val"]["acc"], 4),
                               "val_auc": round(a["val"]["auc"], 4),
                               "swa": a["swa"]} for a in adaylar_tohum]}
    print(f"\nTopluluk kuruldu: {len(adaylar_tohum)} üye ortalaması (tek TFLite)")

    val_metrics = {k: float(v) for k, v in model.evaluate(x_va, y_va, verbose=0, return_dict=True).items()}
    tr_metrics = {k: float(v) for k, v in model.evaluate(x_tr, y_tr, verbose=0, return_dict=True).items()}
    fark = tr_metrics["acc"] - val_metrics["acc"]
    print("Doğrulama:", val_metrics)
    print(f"Aşırı öğrenme farkı (eğitim acc − doğrulama acc): {fark:+.3f} "
          f"{'— YÜKSEK, ayarla.py önerilir' if fark > 0.05 else '(makul)'}")

    # --- doğrulama hata-kaynak raporu: hangi veri ailesi hâlâ zorluyor?
    p_va = model.predict(x_va, verbose=0).ravel()
    hata_kaynaklari = Counter(
        r.get("kaynak", "sentetik")
        for r, p in zip(val_rows, p_va) if (p >= 0.5) != (r["label"] == 1))
    if hata_kaynaklari:
        print("Doğrulamada en çok hata alan kaynaklar:")
        for k, n in hata_kaynaklari.most_common(10):
            print(f"  {k}: {n}")

    # --- TFLite dönüşümü (Keras 3 ile en sağlam yol: SavedModel üzerinden).
    # Dönüşüm eşik seçiminden ÖNCE yapılır: eşik, üretimde koşacak KUANTALI
    # artefaktın skorlarıyla seçilir — float Keras eşiği kuantalamada kayıyor
    # ve hiç doğrulanmıyordu (kod-inceleme bulgusu).
    CIKTI.mkdir(exist_ok=True)
    saved = CIKTI / "_saved_model"
    model.export(str(saved))
    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved))
    converter.optimizations = [tf.lite.Optimize.DEFAULT]  # dinamik aralık kuantalama
    tflite = converter.convert()
    (CIKTI / "model.tflite").write_bytes(tflite)
    shutil.rmtree(saved, ignore_errors=True)  # ara ürün; çıktı paketine girmesin
    tflite_kb_ = len(tflite) / 1024
    print(f"model.tflite (topluluk): {tflite_kb_:.0f} KB (sınır: 10 MB)")
    assert tflite_kb_ <= 10 * 1024, f"BOYUT SINIRI AŞILDI: {tflite_kb_:.0f} KB > 10 MB"

    # --- v10 eşik seçimi: ÜRETİM SİSTEMİ üzerinde, RECALL-KISITLI —
    # "sistem recall'u ≥ RECALL_HEDEF iken en yüksek precision" (Menon
    # logit-adjustment bulgusunun pratik hali: veri karışımı değişince önsel
    # kayar, sabit eşik recall'u sessizce düşürür; kısıt bunu yapısal engeller).
    secim_seti = kal_rows if kal_rows else val_rows
    x_secim = to_xy(secim_seti)[0]
    p_float = model.predict(x_secim, verbose=0).ravel()
    interp = tf.lite.Interpreter(model_content=tflite)
    interp.resize_tensor_input(interp.get_input_details()[0]["index"],
                               [len(secim_seti), MAX_LEN])
    interp.allocate_tensors()
    t_inp, t_out = interp.get_input_details()[0], interp.get_output_details()[0]
    interp.set_tensor(t_inp["index"], x_secim.astype(t_inp["dtype"]))
    interp.invoke()
    p_tflite = interp.get_tensor(t_out["index"]).ravel()
    kuantalama_sapmasi = float(np.max(np.abs(p_tflite - p_float)))

    onerilen_esik, tarama, recall_uygun = sistem_esik_recall_kisitli(p_tflite, secim_seti)
    esik_float, _, _ = sistem_esik_recall_kisitli(p_float, secim_seti)
    esik_kaynagi = (f"KUANTALI TFLite skorlarıyla sistem, recall≥{RECALL_HEDEF} kısıtlı "
                    f"precision-maks; {'kalibrasyon' if kal_rows else 'doğrulama'} "
                    f"{len(secim_seti)} örnek")
    print(f"Float↔TFLite maks skor sapması: {kuantalama_sapmasi:.4f} "
          f"(float eşik önerisi {esik_float}, kuantalı {onerilen_esik})")
    if not recall_uygun:
        print(f"UYARI: hiçbir eşik sistem recall'unu {RECALL_HEDEF} üstünde tutamıyor — "
              "en yüksek recall'lu eşiğe düşüldü; veri/eğitim gözden geçirilmeli.")
    print(f"Otomatik önerilen eşik ({esik_kaynagi}): {onerilen_esik}")
    print("Sistem eşik taraması — kuantalı model (eşik: FP_oranı / FN_oranı / precision):")
    for e, fpr, fnr, prec in tarama:
        if abs((e * 100) % 5) < 1e-6:
            print(f"  {e:.2f}: {fpr:.3f} / {fnr:.3f} / {prec:.3f}")
    # İnsan-kararı eşik (esik_karari.json) doluysa otomatiği EZER; v10'da
    # esik=null bırakıldı → otomatik recall-kısıtlı seçim geçerli olur ve
    # eğitim sonrası insan kararıyla sabitlenir.
    otomatik_esik = onerilen_esik
    if ESIK_KARARI.exists():
        kr = json.loads(ESIK_KARARI.read_text(encoding="utf-8"))
        if kr.get("esik") is not None:
            onerilen_esik = float(kr["esik"])
            esik_kaynagi = f"insan kararı (esik_karari.json): {onerilen_esik} — otomatik öneri {otomatik_esik} idi"
            print(f"→ İnsan-kararı eşik KULLANILIYOR: {onerilen_esik} (otomatik {otomatik_esik} ezildi)")
        else:
            print("→ esik_karari.json'da esik=null: otomatik recall-kısıtlı eşik kullanılıyor; "
                  "nihai karar eğitim sonrası insanla sabitlenecek.")

    (CIKTI / "model_vocab.json").write_text(
        json.dumps(
            {"surum": 3, "max_len": MAX_LEN, "pad": PAD, "oov": OOV,
             "url_jetonu": URL_TOKEN,
             "karakterler": {c: i for c, i in CHAR2ID.items()}},
            ensure_ascii=False, indent=2,
        ),
        encoding="utf-8",
    )
    (CIKTI / "egitim_raporu.json").write_text(
        json.dumps(
            {"ham_ornek": len(rows), "cogaltilmis_egitim": len(train_rows),
             "dogrulama_ornek": len(val_rows), "dogrulama_metrikleri": val_metrics,
             "egitim_metrikleri": tr_metrics,
             "asiri_ogrenme_farki": round(fark, 4),
             "dogrulama_hata_kaynaklari": dict(hata_kaynaklari.most_common(15)),
             "kullanilan_ayarlar": ayarlar,
             "onerilen_esik": onerilen_esik,
             "esik_kaynagi": esik_kaynagi,
             "esik_float_onerisi": esik_float,
             "kuantalama_sapmasi": round(kuantalama_sapmasi, 4),
             "recall_hedefi": RECALL_HEDEF,
             "recall_kisiti_uygun": recall_uygun,
             "distilasyon": {"aktif": t_tr is not None, "alpha": DISTIL_ALPHA,
                             "ogretmen": OGRETMEN.name if (t_tr is not None) else None,
                             "on_isleme": "v2 (ogretmen sozlugu)"},
             "tohum_secimi": secim_bilgi,
             "sistem_esik_taramasi": [{"esik": e, "fp_orani": round(fpr, 3),
                                       "fn_orani": round(fnr, 3),
                                       "precision": round(prec, 3)} for e, fpr, fnr, prec in tarama],
             "on_isleme_surumu": 3,
             "sozluk_boyutu": vocab_size,
             "veri_parmak_izi": veri_parmak_izi(),
             "duman_testi": SMOKE,
             "tflite_kb": round(len(tflite) / 1024, 1)},
            ensure_ascii=False, indent=2,
        ),
        encoding="utf-8",
    )
    print("Bitti. Sıradaki adım: python model/scripts/degerlendir.py")


if __name__ == "__main__":
    main()

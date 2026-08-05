# -*- coding: utf-8 -*-
"""KeywordDetector.kt'nin (Android) Python kopyası — değerlendirmenin gerçek
üretim sistemini (kesin-kelime VEYA model) ölçebilmesi için.

Android tarafı yalnızca "kesin" ifadeleri tek başına engelleme sebebi sayar;
"genel" terimler (bahis, iddaa...) modele bırakılır (çıplak terimler haber ve
şikayet metinlerini de kapatıyordu). Kaynak: app/src/main/assets/keywords.json
+ app/.../detection/KeywordDetector.kt (mantık birebir).

v10.3 düzeltmeleri (kod-inceleme bulguları):
- Colab paketi yalnız model/ içerdiğinden app/ yolu bulunamıyor ve kelime
  katmanı SESSİZCE kapanıyordu → model/data/keywords.json yedeği eklendi
  (app assets ile SENKRON tutulmalı) + bulunamazsa gürültülü uyarı.
- Listeler artık Android'deki lowercase(Locale tr) ile aynı şekilde Türkçe
  küçültülür (düz .lower() 'IDDAA'→'iddaa' üretirdi, Android 'ıddaa' üretir).
- Ayrık yazılmış İ (I + U+0307) Android SpecialCasing'iyle aynı işlenir.
"""
import json
import sys
from pathlib import Path

_KW_YOLLARI = [
    Path(__file__).resolve().parents[2] / "app/src/main/assets/keywords.json",  # repo düzeni
    Path(__file__).resolve().parents[1] / "data/keywords.json",                 # Colab paketi (kopya)
]

CENSOR = {"0": "o", "1": "i", "3": "e", "4": "a", "5": "s", "7": "t", "@": "a", "$": "s"}


def _tr_lower(t: str) -> str:
    # I+U+0307 → i (Unicode SpecialCasing; Kotlin lowercase(tr) böyle yapar)
    t = t.replace("İ", "i").replace("İ", "i").replace("I", "ı")
    return t.lower().replace("̇", "")


def _load():
    for p in _KW_YOLLARI:
        if p.exists():
            kw = json.loads(p.read_text(encoding="utf-8"))
            return ([_tr_lower(k) for k in kw.get("kesin", [])],
                    [_tr_lower(x) for x in kw.get("ignored", [])])
    print("### UYARI (kelime.py): keywords.json bulunamadı — kelime katmanı KAPALI, "
          "'sistem' ölçümü yalnız model! ###", file=sys.stderr)
    return [], []


KESIN, IGNORED = _load()


def keyword_isbetting(text: str) -> bool:
    """KeywordDetector.isBettingContent (Android, yalnız kesin) birebir."""
    low = _tr_lower(text)
    for p in IGNORED:
        low = low.replace(p, "")
    norm = "".join(CENSOR.get(c, c) for c in low)
    return any(k in norm for k in KESIN)

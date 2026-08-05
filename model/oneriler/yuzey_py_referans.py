# -*- coding: utf-8 -*-
"""Android SurfaceGuard'in Python degerlendirme kopyasi.

Bu dosya model egitmez; degerlendirme ve esik taramasi sahadaki uretim kararini
olcsun diye ucuz yuzey kapisini uygular.
"""

import re

ASCII_FOLD = str.maketrans({"ç": "c", "ğ": "g", "ı": "i", "ö": "o", "ş": "s", "ü": "u"})


def _tr_lower(t: str) -> str:
    return t.replace("İ", "i").replace("I", "ı").lower().replace("\u0307", "")


def norm(t: str) -> str:
    return " ".join(_tr_lower(t).translate(ASCII_FOLD).split())


STRONG = [
    re.compile(r"\b(bahis\w*|iddaa\w*|iddiaa\w*|casino\w*|kazino\w*|kumar\w*)\b"),
    re.compile(r"\b(slot\w*|rulet\w*|jackpot\w*|bonus\w*|free\s*spin|freespin|freebet)\b"),
    re.compile(r"\b(canli\s+bahis|deneme\s+bonusu|cevrimsiz\s+bonus)\b"),
]

CONTEXT = [
    re.compile(r"\b(banko\w*|oran\w*|kupon\w*|yatirim\w*|cekim\w*)\b.{0,32}\b(vip\w*|grup\w*|kanal\w*|dm|link\w*|katil\w*|gel\w*|uye\w*)\b"),
    re.compile(r"\b(vip\w*|grup\w*|kanal\w*|dm|link\w*|katil\w*|gel\w*|uye\w*)\b.{0,32}\b(banko\w*|oran\w*|kupon\w*|yatirim\w*|cekim\w*)\b"),
    re.compile(r"\b(mac\w*|tekli\w*|kombine\w*)\b.{0,32}\b(oran\w*|kupon\w*|banko\w*)\b"),
]

WHATSAPP_SYSTEM = [
    re.compile(r"^(~\s*)?.{1,80}\s+bir grup baglantisiyla katildi\.?$"),
    re.compile(r"^(~\s*)?.{1,80}\s+davet baglantisiyla katildi\.?$"),
    re.compile(r"^.{1,100}\s+topluluguna dahil olan bir gruba davet yoluyla katildiniz\.?$"),
    re.compile(r"^bu gruba davet yoluyla katildiniz\.?$"),
    re.compile(r"^.{1,80}\s+bu grubu bir topluluktan cikardi\.?$"),
    re.compile(r"^.{1,80}\s+bu grubu olusturdu\.?$"),
    re.compile(r"^.{1,80}\s+gruptan ayrildi\.?$"),
    re.compile(r"^mesajlar ve aramalar uctan uca sifrelidir\.?$"),
]

CODE = [
    re.compile(r"\b(public|private|class|void|static|return|arraylist|integer|string|system\.out\.println)\b"),
    re.compile(r"\b(val|var|fun|println|import|extends|implements)\b"),
]

FORM_LABEL = [
    re.compile(r"^(cep telefonu|telefon|e-?posta|email|sifre|sifre hatirlatma|kullanici adi)\s*:?$"),
    re.compile(r"^(orcid|iban|tc kimlik|ad soyad|dogum tarihi|ogrenim|universite|fakulte|bolum)\s*:?$"),
    re.compile(r"^(cep telefonu|telefon|e-?posta|email|kullanici adi|iban)\s*:\s*.{1,80}$"),
    re.compile(r"^sifre hatirlatma\b.{0,80}\bgonderildi$"),
]

IBAN = re.compile(r"\btr\d{2}\s?\d{4}\s?\d{4}\s?\d{4}\s?\d{4}\s?\d{0,6}\b")
EMAIL = re.compile(r"[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}")
PHONE = re.compile(r"(\+90|0)?\s?5\d{2}\s?\d{3}\s?\d{2}\s?\d{2}")

FOOD = re.compile(r"\b(yemek|restoran|market|sepet|menu|siparis|kurye)\b")
FOOD_COUPON = [
    re.compile(r"\b(yemek kuponu|puan topla|odulunu kazan|sana ozel restoranlar)\b"),
    re.compile(r"\b(kupon\w*|puan\w*|tl)\b.{0,24}\b(yemek|restoran|market|siparis|sepet)\b"),
    re.compile(r"\b(yemek|restoran|market|siparis|sepet)\b.{0,24}\b(kupon\w*|puan\w*|tl)\b"),
    re.compile(r"^ek\s+\d+\s*tl\s+kupon$"),
]

FORUM_LOGIN = [
    re.compile(r"\bsadece kayitli uyeler yorum yapabilir\b"),
    re.compile(r"\bkayitli uyeler yorum yapabilir\b"),
    re.compile(r"^zaten uye misin\??\s*giris yap$"),
    re.compile(r"\buye misin\??\s*giris yap\b"),
]

META = [
    re.compile(r"[\d.,+ ]+[bkm]?\s*(begenme|begeni|yorum|goruntulenme|izlenme|paylasim|takipci|puan)\S*"),
    re.compile(r"\d+\s*(sn|dk|sa|saat|dakika|gun|hafta|ay|yil)\s*once"),
]


def has_risk_anchor(text: str) -> bool:
    n = norm(text)
    return any(p.search(n) for p in STRONG) or any(p.search(n) for p in CONTEXT)


def package_of(row: dict | None) -> str:
    if not row:
        return ""
    return str(row.get("paket") or row.get("package") or "")


def is_food_package(p: str) -> bool:
    return any(x in p for x in ("yemeksepeti", "getir", "trendyol", "migros", "deliveryhero"))


def is_messaging_package(p: str) -> bool:
    return p in {"com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thunderdog.challegram"}


def surface_bypass(text: str, row: dict | None = None) -> bool:
    n = norm(text)
    if not n or has_risk_anchor(n):
        return False
    p = package_of(row)

    if p in {"com.whatsapp", "com.whatsapp.w4b"} and any(rx.match(n) for rx in WHATSAPP_SYSTEM):
        return True
    if any(rx.search(n) for rx in CODE) or sum(sym in n for sym in ("{", "}", ";", "()", "[]", "<", ">", "==", "->")) >= 3:
        return True
    if len(n) <= 140 and (any(rx.match(n) for rx in FORM_LABEL) or IBAN.match(n) or EMAIL.match(n) or PHONE.match(n)):
        return True
    if len(n) <= 140 and any(rx.search(n) for rx in FORUM_LOGIN):
        return True
    food_context = is_food_package(p) or bool(FOOD.search(n))
    if food_context and any(rx.search(n) for rx in FOOD_COUPON):
        return True
    if len(n) <= 80:
        parts = [x.strip() for x in re.split(r"[·|•]", n) if x.strip()]
        if parts and all(any(rx.match(part) for rx in META) for part in parts):
            return True
    return False


def effective_threshold(text: str, row: dict | None, base_threshold: float) -> float:
    if has_risk_anchor(text):
        return base_threshold
    p = package_of(row)
    if is_messaging_package(p):
        return max(base_threshold, 0.70)
    if p == "com.linkedin.android":
        return max(base_threshold, 0.76)
    if is_food_package(p):
        return max(base_threshold, 0.78)
    return base_threshold

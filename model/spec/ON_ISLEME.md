# Ön İşleme Spesifikasyonu (model v3)

> **v3 değişikliği (18 Tem, v10.3 OOV analizi):** NFC'den hemen sonra
> **fold adımı** eklendi (aşağıda §FOLD): tipografik karakterler sözlükteki
> karşılıklarına katlanır, U+FE0F (emoji varyasyon seçicisi) silinir. Sözlüğe
> **SONA** 10 emoji eklendi (😂😎🚀⚡💸👇🎉🎯➡😅, `surum: 3`) — v2 id'leri
> KAYMADI. v2 ile eğitilmiş model (ör. `ogretmen_v8.tflite`) v3 ön işlemeyle
> ÇALIŞMAZ: yeni id'ler onun embedding aralığını taşırır — train.py bu yüzden
> öğretmeni ayrı `preprocess_ogretmen` (v2) ile skorlar. Gerekçe: veri
> setindeki 1.599 sözlük-dışı karakterin ~%27'si tipografik varyanttı
> (’ — … NBSP), 132'si U+FE0F idi; sahada Android klavyeleri kıvrık tırnak
> üretir.
>
> **v2 değişikliği (13 Tem, saha yanlış alarmı düzeltmesi):** adımlara URL
> normalizasyonu eklendi (aşağıda §URL) ve sözlüğe 🔗 jetonu eklendi
> (`surum: 2`). v1 ile eğitilmiş model v2 ön işlemeyle ÇALIŞMAZ — model ve
> ön işleme birlikte güncellenir.

Modelin Python'da eğitilirken gördüğü girdi ile Kotlin'de çalışma anında
alacağı girdi **birebir aynı** olmak zorunda; en küçük fark sessizce yanlış
skorlara yol açar. Bu belge tek kaynaktır; referans implementasyonlar:

- Python: [`scripts/train.py`](../scripts/train.py) → `preprocess()`
- Kotlin: [`kotlin/TfLiteDetector.kt`](../kotlin/TfLiteDetector.kt) → `preprocess()`

## Adımlar (sırayla)

1. **Unicode NFC normalizasyonu.**
   - Python: `unicodedata.normalize("NFC", text)`
   - Kotlin: `Normalizer.normalize(text, Normalizer.Form.NFC)`
1a. **Fold — tipografik katlama (§FOLD, v3).** NFC'den hemen sonra, URL
   normalizasyonundan ÖNCE uygulanır (NBSP→boşluk, URL sınır tespitine de
   yardım eder). Harita İKİ DİLDE BİREBİR aynıdır (Python `train.py` →
   `FOLD_MAP`/`fold()`, Kotlin `TfLiteDetector.kt` → `fold()`); idempotenttir:
   | Girdi | Çıktı |
   |---|---|
   | U+FE0F (emoji varyasyon seçicisi) | silinir |
   | U+00A0 (NBSP) | boşluk |
   | ’ ‘ ´ \` | ' |
   | “ ” « » „ | " |
   | – — | - |
   | … | ... |
   | • · | . |
   | â Â / î Î / û Û | a / i / u (doğrudan küçük hedef — fold, küçük-harf adımından ÖNCE koştuğu için büyük biçimler haritada OLMAK ZORUNDA) |
1b. **URL normalizasyonu (§URL, v2).** `URL_RE` ile eşleşen her adres için:
   alan adı çıkarılır (protokol ve `www.` atılır, ilk `/` veya `?`de kesilir,
   küçük harfe çevrilir); alan adı `mesru_alanlar.json` listesindeyse eşleşme
   **tek boşlukla silinir**, değilse **" 🔗 "** jetonuyla değiştirilir.
   Gerekçe: model "https://" karakter dizisini teşvik sinyali olarak
   ezberlemişti (saha bulgusu); meşru adresler karardan düşer, bilinmeyen
   adresler tek tip "link var" sinyaline iner. Regex iki dilde birebir aynı
   tutulur (kaynak: `train.py` → `URL_RE`, `TfLiteDetector.kt` → `URL_RE`).
   İdempotenttir (normalize edilmiş metne tekrar uygulanabilir).
2. **Türkçe küçük harfe çevirme + U+0307 temizliği.** Kotlin'de
   `lowercase(Locale("tr"))` doğru sonucu doğrudan verir (`İ→i`, `I→ı`).
   Python bunu bilmez; eğitim tarafında önce `İ→i`, `I→ı` elle uygulanır,
   sonra `.lower()` çağrılır. Ardından İKİ TARAFTA DA birleşik nokta
   (U+0307) silinir — ayrık yazılmış `i̇` (i + U+0307) girdileri de böylece
   aynı işlenir. Test vektörleri:
   `"BAHİS" → "bahis"`, `"IŞIK" → "ışık"`, `"CASINO" → "casıno"` (evet,
   Türkçe kuralla `I→ı`; iki taraf da aynı şekilde yaptığı sürece sorun değil).
3. **Kodpoint → id eşleme** (`cikti/model_vocab.json` içindeki `karakterler`
   haritası). Haritada olmayan her kodpoint **OOV = 1** olur. `pad = 0`.
   - DİKKAT (Kotlin): emoji'ler UTF-16'da çift `Char` kaplar. `for (c in text)`
     ile DEĞİL, **`text.codePoints()`** ile dolaşın ve her kodpointi
     `String`'e çevirip haritada arayın (referans koda bakın).
4. **Kes ve doldur:** ilk **192 kodpoint** alınır (uzunsa gerisi atılır),
   kısaysa sona `0` (pad) eklenir. Çıktı: `IntArray(192)`.
5. Model çıktısı: `float32[1][1]` — 0..1 arası "bahse teşvik" skoru.
   **Eşik modelin değil uygulamanın işi**; önerilen değer
   [`cikti/esik.json`](../cikti/esik.json) dosyasında.

## Bilinçli tasarım kararları

- **Sansür ikamesi (0→o, 1→i …) ön işlemede YOKTUR.** KeywordDetector'daki
  haritanın aksine model ham karakterleri görür; sansürlü yazımları eğitim
  verisindeki örnekler + veri çoğaltma ile öğrenir. Böylece rakamlar
  ("oran 2.5", "%100 bonus") sinyal olarak korunur ve Kotlin tarafında ekstra
  adım gerekmez.
- **Tokenizasyon model dışında ama trivialdir** (~15 satır Kotlin). TFLite'ın
  string operatör desteği zayıf olduğundan tokenizasyonu modele gömmek
  (SELECT_TF_OPS) boyut ve bağımlılık maliyeti getiriyordu; karakter→id
  eşlemesi deterministik olduğundan dışarıda tutmak güvenli.
- **Uzun metin stratejisi:** ekran öğeleri tipik ≤ 200 karakter; ilk 192
  kodpoint yeterli. Kalkan ileride tam sayfa metni tek parça verirse Kotlin
  tarafında 96 kodpoint adımlı kayan pencere + pencere başına skor maksimumu
  önerilir (şimdilik gerekmez, kod basit kalsın).

## Sözlük (`model_vocab.json`, surum 3)

- `max_len`: 192, `pad`: 0, `oov`: 1, `url_jetonu`: "🔗"
- `karakterler`: Türk alfabesi + qwx (32), rakamlar (10), boşluk + yaygın
  noktalama/semboller (₺ € dahil), bahis paylaşımlarında sık görülen 9 emoji
  (💚🔥🎰💰⚽🎁✅⭐📲) + 🔗 URL jetonu (v2'de SONA eklendi — id'ler kaymadı)
  + v3'te SONA eklenen 10 emoji (😂😎🚀⚡💸👇🎉🎯➡😅; OOV analizinde en sık
  görülenler — spam sinyali taşıyorlar, OOV'de eziliyorlardı).
- Sözlük eğitim script'inde sabittir (veriden türetilmez): yeniden eğitimde
  id'ler kaymaz, Kotlin tarafı değişmez (sözlüğü dosyadan okur).
- DİKKAT: v2 sözlüğüyle eğitilmiş artefaktlara (ör. `ogretmen_v8.tflite`)
  v3 id'leri VERİLMEZ — embedding aralığı taşar (train.py `preprocess_ogretmen`).

## Test vektörleri (URL — iki tarafta da doğrulanmalı)

`mesru_alanlar.json` içinde `hurriyet.com.tr` VAR, `kuponkanal-ornek.net` YOK
varsayımıyla:

| Girdi | URL adımı sonrası |
|---|---|
| `Hürriyet https://www.hurriyet.com.tr Son dakika` | `Hürriyet   Son dakika` |
| `t.me/kuponkanali hemen katıl` | ` 🔗  hemen katıl` |
| `kuponkanal-ornek.net/giris yeni adres` | ` 🔗  yeni adres` |
| `www.hurriyet.com.tr` (çıplak, meşru) | ` ` (boş — detektör "temiz" der) |
| `bonus için 🔗 bekliyoruz` (zaten jetonlu) | değişmez (idempotent) |

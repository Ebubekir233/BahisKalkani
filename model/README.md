# Tespit Modeli — Detaylı Rehber (v10.4)

Türkçe bahis-teşvik metni sınıflandırıcısı. Bu belge dört soruya cevap verir:
**(1)** bu model projeyle/görevle gerçekten ilgili mi, nerede ve nasıl
kullanılacak; **(2)** veriler neye göre sınıflandırıldı; **(3)** model hangi
yöntemlerle ve NEDEN böyle hazırlandı; **(4)** kodlarda ne var, nasıl koşulur.

> Güncel durum ve deneme geçmişi: [YOL_HARITASI.md](YOL_HARITASI.md).

---

## 1. Modelin projedeki yeri — görevle bağı

BahisKalkanı (TEKNOFEST 2026 "Bağımlılıklarla Mücadelede Teknolojik
Uygulamalar"), telefonda ekrandaki metinleri okuyup **yasa dışı bahis teşvik
içeriğini kullanıcıya ulaşmadan kapatan** bir koruma uygulamasıdır. Faz 1
tespiti `assets/keywords.json` kelime listesiyle yapar; liste "bahis",
"iddaa" gibi açık terimleri yakalar ama şunları KAÇIRIR: boşluklu/sansürlü
yazım ("b a h i s", "b0nus"), yeni argo ("kasa katladık", "won aldık"),
kelimesiz teşvik ("3.000 yatırdım bakiye 354 bine çıktı, takipte kal").
**Bu model tam olarak o boşluğu kapatmak için var** — Faz 2'nin kendisi.

[görevler/halil-tespit-modeli.md](../görevler/halil-tespit-modeli.md)
sözleşmesindeki teslimatlar ve güncel karşılıkları:

| Teslimat | Karşılığı (v10.4) |
|---|---|
| 1. `model.tflite` (≤10 MB) | `cikti/model.tflite` — 5 üyeli topluluk, ~535 KB |
| 2. Ön işleme speki | `spec/ON_ISLEME.md` (v3) + `kotlin/TfLiteDetector.kt` + `cikti/model_vocab.json` (surum 3, 98 token) |
| 3. Eşik önerisi + kalibrasyon | **0.70** — insan kararı (`esik_karari.json`; otomatik öneri 0.62 idi) + resmi kapı kaydı `cikti/esik.json`; eşik KUANTALI TFLite skorlarıyla kalibrasyon setinde taranır |
| 4. ~100 örneklik kabul seti | `data/kabul_testi.jsonl` (100, 12 tuzak) + `data/kabul_gercek.jsonl` (391) + `data/kabul_saha.jsonl` (99, cihaz hataları) |

Sınırlar korunur: tamamen cihaz üstü (ağ yok, `INTERNET` izni yok), kişisel
veri kaydı yok, metin başına ≤20 ms (ölçülen: topluluk ~0.6 ms), girdi
5-200 karakterlik tek ekran öğesi metni.

## 2. Uygulamada nerede ve nasıl kullanılacak

```
AccessibilityService ekran metnini okur (ScreenReaderService.kt)
        │  düğüm başına bir metin (5-200 karakter)
        ▼
Detector.isBettingContent(text)          ← tek değiştirilebilir nokta
        │
        ├── KeywordDetector (Faz 1, yalnız "kesin" ifadeler)  ─┐
        │                                                      ├─ VEYA
        └── TfLiteDetector (BU MODEL)                         ─┘
        ▼
OverlayController: opak kapak + uyarı + "yine de göster"
```

Model **yalnızca 0..1 arası skor üretir**; engelleme kararını eşikle uygulama
verir. Kelime listesi güvenlik ağı olarak kalır. Tüm ölçümler de bu ÜRETİM
SİSTEMİNİ (kesin-kelime VEYA model) ölçer — `scripts/kelime.py`
KeywordDetector'ın birebir Python kopyasıdır.

Entegrasyon (Ebubekir): `cikti/model.tflite` + `cikti/model_vocab.json` +
`data/mesru_alanlar.json` → `app/src/main/assets/`;
`kotlin/TfLiteDetector.kt` → `app/.../detection/`. Bağımlılık:
`com.google.ai.edge.litert:litert:1.2.0`. **DİKKAT: v3 ön işlemeli
TfLiteDetector.kt yalnız v3 ile eğitilmiş modelle taşınır** — eski model +
yeni Kotlin (veya tersi) uyumsuzdur; ikisi birlikte güncellenir.

## 3. Veriler neye göre sınıflandırıldı

### 3.1 Etiket tanımı (tüm veri işlerinde aynı sözleşme)

- **label=1 (bahse teşvik):** okuyanı bahse YÖNLENDİREN içerik — kupon
  paylaşımı/çağrısı, bonus-çevrim promosyonu, site/kanal tanıtımı, katılım
  daveti, kazanç vaadi. Bahis sitesinin kendi tanıtım metni de teşviktir.
- **label=0 (teşvik yok):** geri kalan her şey — kritik incelik: bahis
  KELİMESİ geçen ama teşvik İÇERMEYEN metinler de 0'dır (operasyon haberi,
  mağdur şikayeti, bağımlılık uyarısı, çağrısız skor tahmini, meşru kampanya).
  Model bunlara alarm verirse uygulama haber sitelerini ve şikayetleri
  kapatır — kabul edilemez; bu yüzden "zor negatif" olarak fazla temsil edilir.
- **Tuzak negatifler:** bahis terimlerine yüzeysel benzeyen masum kelimeler
  ("Betül", "alfabetik", "bahsettiğim", "iddialı", "BahisKalkanı") —
  sözleşme gereği sıfır yanlış alarm; kabul setinde `tuzak=true` ile ayrı ölçülür.
- Kararsız örnekler sete alınmaz ("tartışmalıysa AT"); sahada etiketi ekipçe
  belirsiz kalanlar `gri:true` ile işaretlenir ve HİÇBİR hesaba girmez.

### 3.2 Eğitim verisi — toplam 5.654

- `data/egitim.jsonl` — **1.543 sentetik** (14 kategori; elle + üretken
  çeşitleme, her parti bağımsız denetimden geçirildi).
- `data/gercek.jsonl` — **4.111 gerçek/gerçekçi** (kaynak etiketli). Turlar:
  ilk toplama (HF açık-lisans korpuslar, Telegram önizlemeleri, şikayet
  siteleri, RSS) → v6-v8 (saha FP kalıpları + 37 kategorili taksonomi) →
  v10-v10.2 (WhatsApp-sistem/LinkedIn/puan-kupon/karşı-olgusal/güvenlik/borç)
  → **v10.3** (terim-dengesi: link/kanal/bonus/puan/IBAN + HF organik
  madencilik, +509) → **v10.4** (koşu-1 hata ailelerine cerrahi tur: forum
  üyelik duvarı, gacha/fantezi/airdrop, dekont-sosyal-kanıt, kısa bedava-TL,
  BÜYÜKHARF marka-link, çarpan CTA'sı, kurumsal tanıtım, +190).
  Kaynak/lisans/KVKK: [data/GERCEK_VERI_KAYNAKLARI.md](data/GERCEK_VERI_KAYNAKLARI.md).
- Her turun filtreleri: bağımsız denetçi → normalize/şablon tekilleştirme →
  test setlerine 3-gram Jaccard ≥0.5 sızıntı filtresi → öğretmen filtresi
  (v8 skoru ≥0.95 olan negatif "model işi değil" →
  `data/model_disi_negatifler.jsonl`) → doz ≤%15. Öğretmenin yanıldığı
  örnekler `agirlik: 3.0` alır.

### 3.3 Test/kalibrasyon setleri (eğitimden kod-doğrulamalı AYRIK)

| Set | n | Rolü |
|---|---|---|
| `kabul_testi.jsonl` | 100 (50/50, 12 tuzak) | Sözleşme seti — kapı 1 |
| `kabul_gercek.jsonl` | 391 (39 kategori kaynağı) | Saha temsili genel ölçüm |
| `kabul_saha.jsonl` | 99 — **DONDURULMUŞ** | Cihazda görülen üretim hataları; `seviye: bildirilen` (kapı 2, 0 FP şart) / `cekismeli` (izleme) / `gri` (hesap dışı) |
| `kalibrasyon.jsonl` | 477 | Eşik seçim seti (kapıya girmez) |

Ayrıklık hem veri turlarında hem eğitim anında doğrulanır: `train.py`
başlarken eğitim ∩ (kabul + kalibrasyon) kesişimini ölçer, sızıntı bulursa
KOŞUYU DURDURUR.

## 4. Model hangi yöntemlerle, neden böyle hazırlandı

### 4.1 Neden karakter seviyesi CNN?

| Alternatif | Neden elendi |
|---|---|
| Büyük transformer (BERTurk vb.) | 100+ MB, 10 MB sınırını ve 20 ms bütçesini aşar |
| Kelime seviyesi model + hazır tokenizer | Sansürlü yazım kelime sınırlarını bozar; tokenizer Kotlin'de yeniden yazılamaz |
| TF-IDF + lojistik regresyon | TFLite'a çevirisi dolaylı; sansür varyasyonunda genelleme zayıf |
| **Karakter CNN (seçilen)** | "b0nus"/"b.a.h.i.s" aynı karakter desenlerini taşır; tokenizasyon ~15 satır Kotlin; üye başına ~110 KB |

### 4.2 Mimari (v8 Optuna seçimi — SABİT) + v10.2 topluluk

Tek üye: `Embedding(98→32) → SpatialDropout1D → paralel Conv1D(160; çekirdek
3/4/5) → GlobalMaxPooling → Dense(64) → Dense(1, sigmoid)`.
**Nihai model 5 tohumun ([42,133,7,2025,777]) skor ORTALAMASINI alan tek
TFLite'tır** (`topluluk_kur`). Neden: tek-tohum artefaktında karar sınırı her
eğitimde kayıyor, sıfır-tolerans kapıyı her turda FARKLI tek örnek
deviriyordu (underspecification/köstebek-vurmaca). Ortalama varyansı ~1/√5
düşürür. Boyut ~535 KB, gecikme ~0.6 ms — sınırların çok altında; Kotlin
tarafı değişmez (tek dosya).

### 4.3 Eğitim istikrar mekanizmaları

- **Churn çıpası (distilasyon):** SABİT `cikti/ogretmen_v8.tflite` eğitim
  metinlerini skorlar; öğretmenin DOĞRU bildiği örneklerde hedef yumuşak:
  0.7·etiket + 0.3·öğretmen (yeni veri eklerken bariz pozitiflerin kaçmasını
  engeller — v9 dersinin ilacı). Öğretmen v2 sözlüğüyle eğitildiği için
  `preprocess_ogretmen` (v2) ile skorlanır. Yedek yok: öğretmen dosyası
  yoksa distilasyon AÇIKÇA atlanır.
- **SWA v2:** her epoch ağırlık anlık görüntüsü alınır; eğitim bitince en
  iyi epoch'ta biten ~%25'lik pencerenin ortalaması, YALNIZ doğrulama
  AUC'sinde geri yüklenen en-iyi ağırlıklardan kötü değilse uygulanır;
  hangi rejimin kazandığı üye başına rapora yazılır.
- **Focal loss (yumuşak-etiket destekli form)** veya BCE+smoothing —
  Optuna'nın seçimine göre (`cikti/en_iyi_ayarlar.json`).
- Aşırı öğrenme önlemleri: bölme ÇOĞALTMADAN ÖNCE, SpatialDropout, L2,
  EarlyStopping, çoğaltma yalnız eğitim bölmesine; `asiri_ogrenme_farki`
  rapora yazılır.

### 4.4 Ön işleme sözleşmesi v3 (Python ↔ Kotlin birebir)

NFC → **fold** (’→' —→- …→... NBSP→boşluk •·→. â→a + U+FE0F sil) → URL
normalizasyonu (meşru alan `data/mesru_alanlar.json` silinir, diğer URL'ler
🔗 jetonu) → Türkçe küçük harf + U+0307 → kodpoint→id (98 token; 10
spam-emojisi v3'te SONA eklendi, id'ler kaymadı) → 192'ye kes/doldur.
Ayrıntı + test vektörleri: [spec/ON_ISLEME.md](spec/ON_ISLEME.md).

### 4.5 Eşik: recall-kısıtlı, KUANTALI artefakt üzerinde, insan onaylı

`train.py`, dönüşümden SONRA kuantalanmış TFLite'ın skorlarıyla kalibrasyon
setinde "sistem recall ≥ 0.90 kısıtı altında precision-maks" eşiğini seçer
(float↔TFLite sapması rapora yazılır). `esik_karari.json` doluysa otomatiği
ezer; nihai eşik her sürümde İNSAN kararıyla sabitlenir ve Kotlin
`VARSAYILAN_ESIK` eşitlenir. Eşik kabul setlerinde ASLA seçilmez (iyimser
sapma — v1'de yaşandı, düzeltildi).

### 4.6 Sürüm kapısı (degerlendir.py — hepsi sağlanmalı → EVET)

1. Sözleşme seti: doğruluk ≥ %90 VE tuzak FP = 0
2. `bildirilen` üretim hatalarında 0 FP (cihazda görülmüş hatanın tekrarı
   sürümü düşürür); `cekismeli` yalnız İZLENİR, `gri` hiçbir hesaba girmez
3. INV-URL değişmezliği: negatife meşru-görünümlü URL eklemek alarm üretmemeli

Ek raporlar: dilim (kategori) raporu, kelime-sınırlı terim FP/FN raporu
(kupon/puan/tl/iban/link/bonus/kanal/üye/spin/tombala/poker/papara...),
kalibrasyon skor-bandı teşhisi, kelime-katmanı kanıtı, gecikme. Kapı
ARTEFAKTA özgüdür: her yeniden eğitimde yeniden ölçülür.

## 5. Kodlar

| Dosya | Ne yapar |
|---|---|
| `scripts/train.py` | Damga + sızıntı kontrolü → veri + çoğaltma → churn çıpası → 5 tohum eğitim (SWA v2) → topluluk → TFLite (dinamik aralık kuantalama) → kuantalı eşik taraması → sözlük/rapor. `BK_SMOKE=1` ile hızlı boru hattı duman testi (sonuç modeli değildir). |
| `scripts/degerlendir.py` | 3 kabul setini ÜRETİM SİSTEMİYLE koşar; kapı + dilim + terim FP/FN + INV + kalibrasyon teşhisi + gecikme → `cikti/esik.json`; Colab'daysa `model_cikti.zip` paketler. |
| `scripts/kelime.py` | Android KeywordDetector'ın birebir kopyası (`app/assets/keywords.json`, Colab için yedek: `data/keywords.json` — app ile SENKRON tutulur). |
| `scripts/ayarla.py` | İsteğe bağlı Optuna araması (v10+ için gerekli değil — mimari sabit). |
| `kotlin/TfLiteDetector.kt` | Android referansı: v3 ön işleme + URL kanalı + çıplak URL kapısı + meta-veri süzgeci. |

## 6. Çalıştırma — Google Colab (gerçek eğitim; Halil koşar)

```python
# Hücre 1 — temizlik + yükleme (eski zip kazasına karşı)
!rm -f /content/model_colab*.zip
!rm -rf /content/model
from google.colab import files
files.upload()   # → masaüstündeki güncel model_colab.zip

# Hücre 2 — aç + eğit + değerlendir  (~8-12 dk + ~2-3 dk)
!unzip -q -o /content/model_colab.zip -d /content
!python /content/model/scripts/train.py
!python /content/model/scripts/degerlendir.py

# Hücre 3 (AYRI hücre) — çıktıyı indir
from google.colab import files
files.download("/content/model/model_cikti.zip")
```

Çıktı doğrulaması: ilk satır `### PAKET: ... ###` paket_bilgisi.json ile
tutmalı; "Sızıntı kontrolü ✓", "Öğretmen (ogretmen_v8.tflite, v2 ön işleme)"
ve "KUANTALI TFLite" satırları görünmeli. GPU'da metrikler binde birlik
oynayabilir — bağlayıcı sonuç her zaman `degerlendir.py` çıktısıdır.

Yerel (Windows): `py -3.12` gerekir (TF, 3.13+ desteklemiyor). Hızlı boru
hattı testi: `BK_SMOKE=1 py -3.12 model/scripts/train.py`.

## 7. Sonuçlar ve dürüst sınırlılıklar (v10.4, 18 Tem — resmi kayıt)

**Sürüm kapısı: EVET @0.70** (`cikti/esik.json`; eşik insan kararı — iki
bağımsız eğitim koşusunda 0.70-0.90 penceresi kapının üç koşulunu da geçti,
otomatik öneri 0.62 idi):

| Ölçüm | Sonuç | Hedef |
|---|---|---|
| Sözleşme (100, 12 tuzak) | **%100 — FP 0, FN 0, tuzak 0** | ≥%90 + tuzak 0 ✅ |
| Gerçek saha (391) | %94.9 — FP 1, FN 19 | (genel ölçüm) |
| Saha regresyonu (96) | %99 — bildirilen **0 FP** ✅, çekişmeli 0/34 | bildirilen 0 FP ✅ |
| INV-URL | 0 ihlal | 0 ✅ |
| Boyut / gecikme | 534 KB / ~0.3 ms | ≤10 MB / ≤20 ms ✅ |

Bilinen sınırlar:
- **Mağdur şikayeti ↔ teşvik sınırı** en zor ayrım (bahis-marka yoğun şikayet
  0.7-0.9 alabiliyor) — kabul edilen yapısal sınır; şikayet uygulama/haber
  yüzeyleri yüzey kapısıyla korunur.
- Kelimesiz sosyal-kanıt ("bakiye 354 bine çıktı") ve bağlamsız kısa
  metinler tek eşikle tam çözülmez; çözümün öbür yarısı Ebubekir'in yüzey
  kapısı paketi (`oneriler/`).
- Yalnız Türkçe; İngilizce teşvik kelime listesine emanet.
- Eğitim verisinin bir kısmı elle + üretken çeşitlemeyle kurulmuştur; gerçek
  dağılım uyumu kabul_gercek
  + sandbox çapraz doğrulamasıyla (Ezgi `posts.json` bekleniyor) sınanır.

## 8. Klasör haritası

| Yol | Ne |
|---|---|
| `data/egitim.jsonl` / `gercek.jsonl` | Eğitim setleri (5.654; `agirlik` alanı 3x zor örnek) |
| `data/kabul_*.jsonl` / `kalibrasyon.jsonl` | §3.3'teki test/eşik setleri |
| `data/mesru_alanlar.json` | 391 meşru alan adı (URL kanalı; assets'e de gider) |
| `data/model_disi_negatifler.jsonl` | Öğretmen ≥0.95 negatifler — yüzey kapısı malzemesi |
| `data/keywords.json` | KeywordDetector listesinin Colab kopyası (app ile senkron) |
| `data/GERCEK_VERI_KAYNAKLARI.md` | Kaynak/lisans/KVKK belgesi (rapora girer) |
| `scripts/` · `spec/` · `kotlin/` | §5 + §4.4 |
| `cikti/` | model.tflite, model_vocab.json, esik.json, egitim_raporu.json, **ogretmen_v8.tflite (SABİT çıpa — SİLME!)**, en_iyi_ayarlar.json |
| `esik_karari.json` / `paket_bilgisi.json` | İnsan-kararı eşik (null=otomatik) / paket damgası |
| `oneriler/` | Ebubekir'e yüzey kapısı devir paketi |

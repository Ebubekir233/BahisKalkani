# Yüzey Kapısı Önerisi (Ebubekir'e) — v10 ile birlikte

16 Temmuz saha görsellerindeki yanlış alarm aileleri (WhatsApp sistem
mesajları, LinkedIn tebrik/profil, yemek uygulaması puan/kupon ekranı,
kişisel sohbette IBAN/dekont, uzun grup duyuruları) iki parçalı çözüm
gerektiriyor: model tarafı v10'da ele alınıyor (bkz. YOL_HARITASI); bu belge
**uygulama tarafına düşen** yarıyı öneri olarak derler. Kod bilinçli olarak
BİZ tarafından app/'e uygulanMAmıştır (sahiplik + Aylin senkronu) — hazır
malzeme bu klasördedir:

- `app_degisiklikleri.patch` — v9 denemesinde denenen app değişiklikleri
  (keywords v4 + kesin_regex + KeywordDetector fold + servis akışı)
- `SurfaceGuard.kt` + `SurfaceGuardTest.kt` — yüzey kapısı taslağı
- `yuzey_py_referans.py` — kapının Python kopyası (değerlendirme senkronu
  istenirse; v10 değerlendirmesine BİLEREK gömülmedi)

## Öneriler (sektör pratikleriyle gerekçeli)

1. **Kapı modelin ÖNÜNE, ayrı katman olarak** (Chrome Client-Side Detection
   deseni): `shouldClassify(event)` → uygun değilse model HİÇ çağrılmaz.
   v9 taslağı bunu değerlendirmeye gömmüştü — tersine çevrilmeli.
2. **Muafiyet kararı yapısal sinyalle, salt metin regex'iyle DEĞİL:**
   bahis spam'i kendini "… grup bağlantısıyla katıldı" formatında yazarsa
   metin-regex muafiyeti onu da geçirir (bypass kötüye kullanımı, kanıtlı
   risk). Doğrusu: `FLAG_REPORT_VIEW_IDS` açıp (paket + viewIdResourceName)
   ikilisiyle karar vermek — WhatsApp sistem-mesajı düğümü, LinkedIn bildirim
   düğümü, Yemeksepeti puan banner'ı yapısal olarak ayırt edilebilir.
   Taslaktaki "riskli kelime varsa muaf tutma" kuralı korunmalı.
3. **Pozitif yüzey listesi** (Google SafetyCore/Sensitive Content Warnings
   deseni): "her şeyi tara, FP'yi düzelt" yerine riskin aktığı yüzeylerde
   tara (tarayıcı, sosyal medya feed'i, mesaj GÖVDESİ); sistem UI /
   bildirim çubuğu / form alanları kapsam dışı.
4. **Uygulama-bazlı eşik** (Meta yüzey-bazlı eşik): haber/e-ticaret/banka
   paketlerinde yüksek (örn. 0.8), bilinmeyen tarayıcı sekmesinde düşük
   (örn. 0.55). Paket adı serviste zaten var.
5. **Parmak izi bastırma (dedupe):** aynı normalize metin + aynı yüzeyden
   tekrar eden alarm bir kez gösterilsin (uzun grup duyurusu her
   kaydırmada yeniden alarm üretmesin) — ticari ürünlerde (Bark) standart.
6. **Uzun metin: cümle-hizalı pencereleme** — `BreakIterator.getSentenceInstance
   (Locale("tr"))` ile cümlelere böl, 192 kodpointe cümle sınırından paketle,
   pencere skorlarını `0.5·max + 0.5·mean` ile birleştir; tek pencere yerine
   "2-of-N pencere eşik üstü" kuralı FP'yi düşürür. Maliyet: pencere başına
   ~0.1 ms — bütçe sorunu yok.
7. **kesin_regex fikri değerli** (v9 denemesinden): "hesap aç…bonus",
   "MARKA+bet…link" gibi kombinasyon regex'leri kelime katmanında recall'ı
   taşır. UYARI: keywords.json Chrome'la (Aylin) senkron — v4'e geçiş ekip
   kararı olmalı; ASCII-fold eklenirse KeywordDetector ile birlikte gitmeli
   (patch'te ikisi bir arada).

## Uygulama sırası önerisi

Önce 1+2+5 (en yüksek getiri, düşük risk), sonra 4, sonra 6; 3 ve 7 ekip
kararıyla. Her adım sonrası `model/data/kabul_saha.jsonl` regresyon setiyle
(cihazda ya da PC'de degerlendir.py) doğrulama.

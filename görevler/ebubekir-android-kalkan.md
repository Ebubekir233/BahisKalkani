# Ebubekir YILMAZ — Android Kalkan (proje lideri)

## Görev

Bu repodaki Android uygulaması: AccessibilityService ile ekran metnini okur,
tespit motoruna sorar, bahis içeriğinin üstüne overlay çizer. Ek olarak proje
liderliği: ekipler arası koordinasyon, entegrasyonlar, rapor demosu.

## Mevcut durum (18 Temmuz)

Uygulama uçtan uca çalışıyor ve saha testinden geçti: servis + kelime listesi
(kesin ifadeler) + v10.4 topluluk modeli + SurfaceGuard yüzey kapısı +
dokunuş-geçirgen kapaklar + "yine de göster" + toplu çip + yenilenen ana
ekran (durum kartı, canlı sayaç, "nasıl çalışır", KVKK notu, koruma
aç/kapat).

## Yapılacaklar

1. **Cihaz duman testi:** `gradlew.bat connectedDebugAndroidTest` — model
   yükleme + pozitif/negatif ayrımı + gecikme ölçümü (≤20 ms bütçe); sonuç
   Halil'e iletilecek (kendi yol haritasında açık madde).
2. **PC demo sayfası (bende):** offline açılan tek dosyalık HTML — sahte
   sosyal medya akışı. Final demosu offline olacağı için şart; gönderi
   verisi Ezgi'nin `posts.json`'undan beslenecek, Aylin eklentiyi bu sayfada
   test edecek.
3. **Sandbox uçtan uca testi:** Ezgi'nin ilk kaydırılabilir sürümü çıkar çıkmaz
   aynı cihazda ortak test; overlay konumlandırması sandbox'ta kusursuz olmalı
   (final demosu orada). Uyum kuralları: [ezgi-demo-sandbox.md](ezgi-demo-sandbox.md)
4. **Rapor malzemesi (4-5 Ağustos):** sandbox + kalkan ekran kaydı, ana ekran
   ve sayaç görüntüleri.
5. **Koordinasyon:** keywords.json değişince Aylin'e iletmek; yeni model
   sürümlerini entegre etmek (assets + TfLiteDetector + eşik birlikte
   güncellenir); saha yanlış alarmlarını Halil'e iletmek.

## Bilinen sınırlar (bilinçli)

- WhatsApp'ta tek tük yanlış alarm sürüyor — sonraki model turunda ele
  alınacak, şimdilik kabul edildi.
- Overlay kapağı bölge boyutuna göre üç kademede çizilir (yalnız karartma /
  + uyarı / + kendi "yine de göster"i); gerekçesi `OverlayController`
  içindeki yorumda. Toplu çip her durumda görünür.

## Nasıl

- Komutlar (depo kökünden): `gradlew.bat assembleDebug` (derle),
  `gradlew.bat installDebug` (bağlı cihaza kur),
  `gradlew.bat testDebugUnitTest` (birim testleri), `gradlew.bat lint`
- Cihazda: Ayarlar → Erişilebilirlik → Bahis Kalkanı'nı etkinleştir;
  doğrulama: `adb logcat -s BahisKalkani` (log etiketi kod kimliği,
  uygulama adından bağımsız)
- Kod yapısı: `detection/` (Detector arayüzü — tek değiştirilebilir nokta),
  `overlay/`, `service/`, `stats/`; mimari ayrıntılar yerel CLAUDE.md'de
  (git dışı, Claude Code için)
- Değişmez kurallar: KVKK (veri kaydı yok, ağ yok, INTERNET izni yok),
  tespit her zaman Detector arayüzünün arkasında

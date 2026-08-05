# 🛡 Bahis Kalkanı

**Takım: BetBlockers** — TEKNOFEST 2026 "Bağımlılıklarla Mücadelede
Teknolojik Uygulamalar" yarışması projesi. Sistem, sosyal medyadaki
yasadışı bahis teşvik içeriklerini cihaz üstünde tespit edip kullanıcıya
ulaşmadan engelliyor. Hiçbir veri kaydedilmez, hiçbir ağ isteği atılmaz.


## Okuma sırası

1. **[PROJE.md](PROJE.md)** — ürün ne, kim neyin sahibi, ortak kurallar, takvim
2. **`görevler/` altındaki kendi dosyan** — görevin, nasıl yapacağın, teslim listesi:
   - [Ebubekir — Android Kalkan](görevler/ebubekir-android-kalkan.md)
   - [Ezgi — Demo Sandbox](görevler/ezgi-demo-sandbox.md)
   - [Aylin — Chrome Eklentisi](görevler/aylin-chrome-eklentisi.md)
   - [Halil — Tespit Modeli](görevler/halil-tespit-modeli.md)
3. Gerektikçe:
   - **Ebubekir:** diğer üç görev dosyası (entegrasyon sözleşmeleri —
     model teslim formatı Halil'in, sandbox uyum kuralları Ezgi'nin
     dosyasında)
   - **Ezgi:** gönderi JSON'unu yazarken [keywords.json](app/src/main/assets/keywords.json)
   - **Aylin:** [keywords.json](app/src/main/assets/keywords.json) (aynen
     kopyalanacak tek kaynak) + [KeywordDetector.kt](app/src/main/java/com/teknofest/bahiskalkani/detection/KeywordDetector.kt)
     (birebir taşınacak eşleşme mantığı)
   - **Halil:** [Detector.kt](app/src/main/java/com/teknofest/bahiskalkani/detection/Detector.kt)
     (modelin takılacağı arayüz)

## Çalışma düzeni

- Herkes doğrudan `main`'e push yapar. Push etmeden önce **`git pull`** çek;
  çakışma çıkarsa ve içinden çıkamazsan Ebubekir'e yaz.
- Belgeler (PROJE.md, görevler/) herkesin: güncellemekten çekinme, küçük ve
  sık commit at.
- `keywords.json` güncelleyen kişi Aylin'e haber verir (Chrome tarafı aynı
  listeyi elle senkron tutuyor).

## Kesin kurallar (KVKK — tüm bileşenler için)

- Kullanıcı verisi kaydedilmez; okunan ekran/sayfa metinleri saklanmaz,
  loglanmaz
- Ağ isteği atılmaz; Android tarafında `INTERNET` izni yok ve eklenmeyecek
- Tespit mantığı `Detector` arayüzünün (Chrome'da `detector.js`) arkasında
  kalır — Faz 2'de TFLite modeli aynı arayüze takılacak

# Proje Bağlamı

**Takım:** BetBlockers

Bu proje TEKNOFEST 2026 "Bağımlılıklarla Mücadelede Teknolojik Uygulamalar Yarışması" için geliştiriliyor. Sistem, sosyal medyadaki yasadışı bahis teşvik içeriklerini (kupon paylaşımı, bonus/çevrim dili, Telegram davetleri) tespit edip kullanıcıya ulaşmadan engelleyen çok bileşenli bir üründür. Yarışmada sıralamayı %100 prototip puanı belirliyor; en yüksek ağırlık "canlı çalışan demo" (%25) ve "teknik tutarlılık" (%20).

## Sistem Bileşenleri ve Sahipleri

- **Android Kalkan + PC Demo Sayfası (Ebubekir YILMAZ — proje lideri):**
  *Android Kalkan:* AccessibilityService ile ekran metnini okur, tespit motoruna sorar, bahis içeriğinin üstüne overlay (opak kapak + uyarı + "yine de göster") çizer. Faz 1'de tespit = kelime listesi; Faz 2'de TFLite model takılır, bu yüzden Detector tek bir değiştirilebilir arayüz olarak kalmalı. Not: bölge bazlı gerçek blur Android erişilebilirlik overlay'inde uygulanabilir değil (FLAG_BLUR_BEHIND tüm ekranı bulanıklaştırıp cihazı kilitliyor — denendi); opak kapak koruma açısından eşdeğer, hatta daha güçlü (bulanık içerik kısmen seçilebilir, opak hiç seçilemez).
  *PC Demo Sayfası:* offline açılan tek dosyalık HTML — sahte sosyal medya akışı, gönderi verisi sandbox'ın `posts.json`'undan. Final demosu offline olduğu için tarayıcı eklentisi bu sayfada gösterilecek; aynı içeriğin telefonda kalkanla, bilgisayarda eklentiyle yakalanması ürün bütünlüğünü gösterir.
  - *Ayrıca:* proje liderliği — ekipler arası koordinasyon ve entegrasyonlar.
- **Demo Sandbox (Ezgi Efsa GÜLEÇ):** Instagram benzeri sahte sosyal medya uygulaması (Kotlin/Compose, LazyColumn feed, gönderiler yerel JSON'dan, tamamen offline). Final günü jüri demosu bu uygulama üzerinde yapılacak: aynı telefonda sandbox + kalkan kurulu olacak, kalkan sandbox'taki bahis gönderilerini canlı yakalayacak. Ayrı repoda.
- **Chrome Eklentisi (Aylin AKAGÜNDÜZ):** Aynı mantığın tarayıcı versiyonu, Manifest V3, content script + sayaç popup'ı. Bu repodaki `chrome-eklentisi/` klasöründe geliştiriliyor (ayrı repo değil). Kelime listesi ve Detector mantığı Android tarafıyla eşdeğer tutulmalı — bir tarafta liste güncellenirse diğerine de taşınır. **Karar (18 Tem): tarayıcı tarafı yalnız kelime listesiyle gider** (model Android'de); haber sitelerindeki yanlış alarmlar `mesru_alanlar.json` ile alan-bazlı davranışla çözülür. Modelin TF.js sürümü ileriye bırakıldı. Eklenti eklendi ve büyük/küçük harf + alan-eşleşme düzeltmeleriyle güncellendi (2 Ağustos).
- **Tespit Modeli (Halil Hadra UZUN):** Türkçe bahis-teşvik metni sınıflandırıcısı. Kısıtlar: cihaz üstü çalışacak (küçük/hızlı, TFLite hedefi), ağ isteği yok, kişisel veri yok. Sansürleme varyasyonlarını ("b0nus", "ç3vrim") kapsayacak. Hazır olunca bu repodaki Detector implementasyonunun yerine geçecek.

## Ekip Genelinde Geçerli Kurallar

- Hiçbir kullanıcı verisi kaydedilmez, hiçbir ağ isteği atılmaz (KVKK stratejik kararı — sunumda "veri cihazdan çıkmıyor" diyeceğiz)
- Tespit mantığı Detector arayüzünün arkasında kalır; model entegrasyonunda arayüz değişmez
- Overlay konumlandırması sandbox uygulamasında kusursuz çalışmak zorunda (final demosu orada); gerçek Instagram'da çalışması bonus
- Kod, ekipteki herkesin okuyup anlayabileceği sadelikte tutulur; bileşenler farklı kişilerce geliştirildiği için okunabilirlik önceliklidir

## Takvim Kilometre Taşları

- **24 Temmuz:** baseline model entegrasyona hazır, kalkan kelime listesiyle uçtan uca çalışıyor
- **4-5 Ağustos:** rapor için ekran görüntüleri/ekran kaydı alınacak (çalışan yakalama demosu şart)
- **7 Ağustos 17:00:** rapor teslimi
- **30 Eylül - 4 Ekim:** TEKNOFEST final (Şanlıurfa) — offline canlı demo

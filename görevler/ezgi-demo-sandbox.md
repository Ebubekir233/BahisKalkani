# Ezgi Efsa GÜLEÇ — Demo Sandbox

## Görev

Instagram benzeri sahte sosyal medya uygulaması (ayrı repo). Final günü jüri
demosu bunun üzerinde yapılacak: aynı telefonda sandbox + kalkan kurulu
olacak, jüri feed'i kaydırdıkça kalkan bahis gönderilerini canlı yakalayıp
kapatacak. **Demo puanının sahnesi bu uygulama** — akıcılık ve gerçekçi
görünüm önemli.

## Teslim edilecekler

1. Kotlin + Jetpack Compose uygulaması; `LazyColumn` ile kaydırılabilir feed
2. Gönderiler yerel JSON dosyasından (kullanıcı adı, avatar/renk, metin,
   opsiyonel görsel); tamamen offline, INTERNET izni yok
3. Instagram hissi veren gönderi kartı: üstte kullanıcı, ortada içerik,
   altta beğeni/yorum simgeleri (çalışması şart değil, görünmesi yeter)

## Kalkan uyumu (kritik kurallar)

1. **Metinler erişilebilirlik ağacında olmalı.** Gönderi metinlerini normal
   `Text` composable ile çiz. Metni resmin/Canvas'ın içine gömme — kalkan
   yalnızca erişilebilirlik ağacındaki metni görür, resim içindeki yazıyı
   göremez.
2. **Gönderinin tamamının kapanması için:** kartın kök composable'ına
   `Modifier.semantics(mergeDescendants = true) {}` ekle. Böylece kartın tüm
   metni tek düğümde birleşir ve kalkan kartın tamamını kapatır. Eklenmezse
   yalnızca eşleşen `Text` satırı kapanır — hangisinin demoda iyi durduğuna
   ilk ortak testte birlikte karar verilir.
3. **Kart boyutu:** kapakta uyarı + "yine de göster" butonunun görünmesi için
   bölge en az ~320 px genişlik ve ~220 px yükseklik olmalı (feed kartları
   doğal olarak bunun üstündedir; çok küçük öğelerde kalkan yalnızca düz
   kapak çizer).
4. **WebView kullanma.** Native Compose kal.
5. **Uygulama adında ve sabit arayüz metinlerinde bahis kelimeleri geçmesin**
   (ör. uygulamaya "BahisFeed" deme) — kalkan onları da kapatır. Gönderi
   İÇERİKLERİNDE geçmesi zaten işin amacı.
6. Yorum yazma kutusu gibi yazı alanları güvenli — kalkan `isEditable`
   alanları bilinçli olarak kapatmıyor.

## Demo verisi

- Bahis gönderilerini kalkan reposundaki `app/src/main/assets/keywords.json`
  listesinden besle (tek kaynak orası)
- Karışım: ~%30 bahis (düz yazım + sansürlü "b0nus"/"ç3vrim" + kupon ve
  Telegram davet dili), ~%70 masum; araya "Betül", "alfabetik" gibi tuzak
  kelimeli masum gönderiler koy (yanlış pozitif olmadığını göstermek için)
- 30-40 gönderi yeterli; JSON'u elle yazmak sıkıcıysa Ebubekir'den yardım iste

## Nasıl (önerilen sıra)

1. Boş Compose projesi (Android Studio şablonu), minSdk 26
2. `Post` data class + `assets/posts.json` + okuma katmanı (org.json yeterli)
3. `LazyColumn` + gönderi kartı composable'ı
4. Görünüm cilası en sona — önce kaydırılabilir 10 gönderilik sürüm çıkar,
   Ebubekir'in cihazında kalkanla ortak test yapın (24 Temmuz'u bekleme;
   kalkan APK'sı kalkan reposundan `gradlew.bat assembleDebug` ile çıkıyor)

## Takvim

- İlk kaydırılabilir sürüm: en kısa sürede (ortak test için)
- 4-5 Ağustos: rapor ekran kayıtları bu uygulama + kalkan ikilisiyle çekilecek
- 30 Eylül: final demosu (offline, Şanlıurfa)

## Tamamlanan İşler

- [x] Kotlin + Jetpack Compose projesi oluşturuldu
- [x] LazyColumn ile kaydırılabilir feed yapıldı
- [x] 40 gönderi eklendi (%30 bahis, %70 normal, tuzak kelimeler dahil)
- [x] posts.json yerel dosyasından okunuyor, internet izni yok
- [x] Modifier.semantics(mergeDescendants = true) eklendi
- [x] Gönderi kartı: profil dairesi + kullanıcı adı + metin + beğeni/yorum
- [x] Uygulama adı "SosyalApp" (bahis kelimesi yok)
- [x] Splash screen ve özel logo eklendi
- [x] Kalkan ile entegrasyon testi yapıldı, bahis içerikleri kapatılıyor
- [x] Realme C53 telefonda test edildi

## Repo
[github.com/ezgiefsa/BahisSandbox](https://github.com/ezgiefsa/BahisSandbox)

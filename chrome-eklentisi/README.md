# Bahis Kalkanı — Chrome Eklentisi

TEKNOFEST 2026 "Bağımlılıklarla Mücadelede Teknolojik Uygulamalar" projesi
Bahis Kalkanı'nın tarayıcı bileşeni. Web sayfalarındaki bahis-teşvik
içeriğini cihaz üzerinde tespit edip kullanıcıya ulaşmadan kapatır.

Android uygulamasının tarayıcı karşılığıdır; tespit mantığı ve kelime listesi Android tarafıyla
eşdeğer tutulur. Geliştirici: **Aylin Akagündüz**.

---

## Özellikler

- **Otomatik tespit + kapatma**: Sayfadaki metinler taranır, bahis-teşvik
  içeriği bulunursa üstü opak bir kapakla gizlenir.
- **"Yine de göster" / tekrar gizle**: Kullanıcı isterse kapatılan içeriği
  görebilir; sağ üstte beliren küçük 🛡️ ikonuyla sonra tekrar gizleyebilir.
- **Dinamik içerik takibi**: `MutationObserver` ile sonsuz kaydırmalı
  sayfalarda (Instagram, Twitter vb.) sonradan yüklenen içerik de taranır.
- **Sayaç**: Eklenti simgesine tıklanınca "bu oturumda N içerik engellendi"
  gösterilir. Oturum bazlıdır (`chrome.storage.session` + `background.js`
  ile erişim izni), tarayıcı yeniden başlatılınca sıfırlanır.
- **Bağlamsal koruma (`muaf`)**: Haber/uyarı bağlamındaki metinler (örn.
  "gözaltı", "operasyon", "191") genel terimlerin ("kumar", "bahis" gibi)
  yanlış pozitif üretmesini engeller. Kesin ifadeler bu istisnadan
  etkilenmez — spam "gözaltı" kelimesi ekleyip kaçamaz.
- **Meşru site whitelist (`mesru_alanlar`)**: 391 denetlenmiş haber/kurum
  sitesinde yalnızca `kesin` ifadeler engelleme sebebi sayılır, `genel`
  terimler devre dışı kalır.
- **Sansür/leetspeak normalizasyonu**: `b0nus`, `fre3bet` gibi sansürlü
  yazımlar da yakalanır (`0→o, 1→i, 3→e, 4→a, 5→s, 7→t, @→a, $→s`).
- **Türkçe büyük/küçük harf duyarlılığı**: Metin ve kelime listesi birlikte
  Türkçe küçük harfe çevrilip harf katlamasından (ı→i, ç→c, ğ→g, ö→o, ş→s,
  ü→u) geçirilir. Böylece hem `CANLI BAHİS` hem `FREE SPIN` gibi büyük harfli
  yazımlar doğru eşleşir; `bahıs` gibi aksansız kaçamak yazımlar da yakalanır.

## Dosya Yapısı

| Dosya | Görev |
|---|---|
| `manifest.json` | Eklenti yapılandırması (Manifest V3) |
| `content.js` | Sayfa tarama, kapatma, `MutationObserver`, sayaç tetikleme |
| `detector.js` | Tespit fonksiyonları (normalize, ignored/muaf filtreleme, eşleştirme) |
| `background.js` | `chrome.storage.session` erişim izni + kelime/alan listelerini bir kez okuyup içerik betiğine mesajla veren katman |
| `popup.html` / `popup.js` | Sayaç arayüzü |
| `keywords.json` | Kelime listesi (v4: kesin/genel/ignored/muaf) — Android ile ortak kaynak |
| `mesru_alanlar.json` | Whitelist domain listesi (391 alan) |
| `test.html` | Geliştirme sürecinde kullanılan basit test sayfası |

Resmî test/demo sayfası bu klasörde değil, deponun kökündeki
`web-demo/index.html` dosyasıdır (tek kaynak; kopyası tutulmaz).

## Nasıl Çalışır (özet akış)

1. `manifest.json`, her sayfaya `detector.js` + `content.js`'i enjekte eder.
2. `content.js` sayfa yüklendiğinde `scanPage()`'i çalıştırır. Kelime ve
   meşru alan listeleri `background.js`'ten mesajla bir kez alınır. Sayfanın
   alan adı `mesru_alanlar` listesindeyse (tam alan ya da alt alan olarak)
   yalnızca `kesin` terimler, değilse `kesin + genel` terimler aktif olur.
3. `TreeWalker` ile sayfadaki metin düğümleri gezilir (kendi eklediğimiz
   uyarı kutuları ile `<script>`/`<style>` içerikleri hariç tutulur).
4. Her metin `detector.js`'teki `isBettingContent()`'e sorulur: önce
   `kesin` kontrol edilir, sonra `muaf` bağlamı varsa `genel` göz ardı
   edilir, yoksa `genel` de kontrol edilir.
5. Eşleşen metnin en yakın gönderi/blok kutusu (`.post` ya da ebeveyn
   element) `hideNode()` ile kapatılır ve sayaç bir artırılır.
6. `MutationObserver`, sayfaya sonradan eklenen içerikte de aynı taramayı
   (debounce'lu şekilde) tekrar tetikler.

## KVKK / Gizlilik

- Hiçbir kullanıcı verisi veya okunan sayfa metni saklanmaz/loglanmaz;
  yalnızca engellenen içerik **sayısı** tutulur.
- Hiçbir dış sunucuya ağ isteği atılmaz — `fetch` yalnızca eklenti paketi
  içindeki dosyalar (`keywords.json`, `mesru_alanlar.json`) için ve yalnızca
  `background.js` içinde kullanılır. Dosyalar `web_accessible_resources` ile
  sayfalara açılmadığından hiçbir site eklentinin varlığını bu yolla tespit
  edemez.
  Bu, DevTools Network sekmesiyle görsel olarak doğrulanmıştır (tüm
  istekler `content.js`/`detector.js` kaynaklı, `http(s)://` isteği yok).
- İnternet bağlantısı olmadan (Wi-Fi kapalı) test edilmiş, tam olarak
  çalıştığı doğrulanmıştır.

## Test Süreci ve Karşılaşılan Sorunlar

Geliştirme sürecinde bulunup çözülen başlıca sorunlar :

1. **Türkçe I/İ hatası (iki yönlü)**: Önce `toLocaleLowerCase('tr')`
   kullanıldı; bu, İngilizce kökenli kelimeleri bozuyordu (`FREE SPIN` →
   `free spın`, listedeki `free spin` ile eşleşmiyordu). Ardından İ/I elle
   `i`'ye çevrilip düz `toLowerCase()` denendi; bu sefer ters yönde kırıldı:
   `CANLI BAHİS` → `canli bahis` olduğundan `ı` içeren kelimeler
   (`canlı bahis`, `gözaltı`, `yardım hattı`) hiç eşleşmedi — büyük harfli
   spam kaçıyor, büyük harfli haber ise yanlışlıkla engelleniyordu. Nihai
   çözüm: Türkçe küçük harf + **harf katlama** (ı→i, ç→c, ğ→g, ö→o, ş→s,
   ü→u) hem metne hem kelime listesine uygulandı; iki taraf da aynı ASCII
   tabanına indiğinden her iki durum da doğru çalışıyor. Android tarafındaki
   `KeywordDetector` ile aynı harita kullanılır.
2. **Script/style içeriğinin taranması**: `TreeWalker`, sayfadaki
   `<script>` etiketinin içindeki kodu da "metin" sayıp yanlışlıkla
   tarıyordu (örn. `web-demo.html`'in kendi `KEYWORDS` tanımı içindeki
   "bahis" kelimesi bir eşleşme olarak algılanıyordu). Çözüm: `acceptNode`
   filtresine `script, style, noscript` etiketlerini hariç tutan bir
   kontrol eklendi. Bu, gerçek sitelerde (hepsinde `<script>` bulunur)
   yanlış pozitifleri önleyen kritik bir düzeltmeydi.
3. **Kendi kendini yiyen `MutationObserver` döngüsü**: Kapatma kutusunun
   kendi uyarı metni ("Bahis içeriği gizlendi") "bahis" kelimesini
   içerdiği için, eklenen her kapak yeni bir mutation tetikleyip kendi
   üstüne kapak ekliyordu (sonsuz döngü, tarayıcı çöküyordu). Çözüm: kendi
   oluşturduğumuz elementlere `bk-ignore` sınıfı verilip tarama dışı
   bırakıldı.
4. **Paralel tarama çakışması**: `scanPage()` asenkron olduğu için birden
   fazla mutation aynı anda birden fazla taramayı tetikleyip aynı içeriği
   birden çok kez kapatabiliyordu. Çözüm: tarama fonksiyonuna kilit
   (`taramaCalisiyor` bayrağı) ve `MutationObserver` tetikleyicisine
   debounce eklendi.
5. **Metin parçası vs. gönderi bütünü**: Bir gönderide isim, kullanıcı adı
   ve gönderi metni ayrı elementlerde olduğunda, aynı gönderi birden fazla
   kez (her eşleşen parça için ayrı) kapatılıyor ve sayaç yanlış
   artıyordu. Çözüm: eşleşen düğümün en yakın `.post` kutusu bulunup
   `Set` ile tekilleştirildi.
6. **Sayaç yarış durumu (race condition)**: `chrome.storage` asenkron
   olduğu için art arda hızlı gelen artırma istekleri birbirinin üzerine
   yazabiliyordu. Çözüm: artırma işlemleri bir promise kuyruğunda
   sıraya alındı.
7. **Oturum bazlı sayaç**: `chrome.storage.session`, content script'lerden
   varsayılan olarak erişilemiyordu. Çözüm: `background.js` ile
   `setAccessLevel(TRUSTED_AND_UNTRUSTED_CONTEXTS)` çağrılarak erişim açıldı.
8. **`mesru_alanlar` mantığı**: İlk halde whitelist'teki bir sitede tarama
   tamamen durduruluyordu; doğrusu yalnızca `genel` listesinin devre dışı
   kalması, `kesin` ifadelerin hâlâ geçerli olmasıydı. Buna göre düzeltildi.
9. **Alan adı eşleşmesinde bypass**: Kontrol `hostname.includes(alan)` ile
   yapıldığından `hurriyet.com.tr.bahis-sitesi.xyz` gibi bir alan adı meşru
   sayılıp `genel` katmanını devre dışı bırakabiliyordu. Çözüm: tam eşleşme
   veya nokta sınırlı alt alan kontrolü (`host === alan || host.endsWith('.' + alan)`).
10. **Listelerin her taramada yeniden okunması**: `keywords.json` ve
   `mesru_alanlar.json` her `scanPage()` çağrısında yeniden `fetch` +
   `JSON.parse` ediliyordu (sonsuz kaydırmada gereksiz yük). Çözüm: okuma
   `background.js`'e taşındı, oturumda bir kez yapılıp mesajla paylaşılıyor;
   böylece `web_accessible_resources` ihtiyacı da ortadan kalktı.

`web-demo.html` üzerinde yapılan son doğrulamada: 4 bahis içeriği doğru
tespit edilip kapatıldı, 2 haber/uyarı içeriği (`muaf` sayesinde) doğru
şekilde açık bırakıldı, sayaç doğru sonucu (4) verdi.

## Bilinen Tasarım Kararları

- **`<all_urls>` kapsamı** bilinçli tercih edildi: bahis-teşvik içeriği
  herhangi bir sitede karşımıza çıkabileceği için kapsamı daraltmak
  korumayı zayıflatırdı.
- Chrome eklentisinde tespit modeli (TFLite) **yoktur** — yalnızca kelime
  listesiyle çalışır (Android'de model + kelime listesi birlikte
  kullanılır). Bu nedenle `mesru_alanlar` ve `muaf` mekanizmaları, modelin
  Android'de sağladığı bağlamsal ayrımı Chrome tarafında telafi eder.
- `keywords.json`, Android reposundaki (`BahisKalkani/app/src/main/assets/
  keywords.json`) dosyayla elle senkron tutulur; liste güncellenince iki
  taraf da birbirine haber verir.

## Kurulum / Test (geliştirici için)

1. `chrome://extensions` → Geliştirici modu aç → "Paketlenmemiş öğe yükle"
   → bu klasörü seç.
2. Depo kökündeki `web-demo/index.html`'i Chrome'da aç, eklenti otomatik
   taramaya başlar.
3. Eklenti ikonuna tıklayarak sayaç popup'ını görüntüle.

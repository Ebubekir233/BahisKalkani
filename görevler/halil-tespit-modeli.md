# Halil Hadra UZUN — Tespit Modeli

## Görev

Türkçe bahis-teşvik metni sınıflandırıcısı: girdi olarak kısa bir ekran metni
alır, 0..1 arası "bahse teşvik" skoru döner. Hazır olunca Android Kalkan'daki
kelime listesinin yanına takılacak (model VEYA liste eşleşirse engelle).

## Uygulamadaki takılma noktası

Kalkan'da tespit mantığı tek bir arayüzün arkasında:

```kotlin
fun interface Detector {
    fun isBettingContent(text: String): Boolean
}
```

Model geldiğinde Ebubekir `TfLiteDetector : Detector` sınıfını yazacak ve
serviste tek satır değişecek. Kelime listesi (`assets/keywords.json`) yedek
olarak kalacak. Eşiği uygulama uygular; model yalnızca skor döner.

## Teslim edilecekler

1. `model.tflite` dosyası (≤ 10 MB; APK içine, `assets/` altına gömülecek)
2. Ön işleme spesifikasyonu — tercihen tokenizasyon modelin İÇİNDE gömülü;
   değilse vocab dosyası + Kotlin'de yeniden yazılabilecek kadar net kurallar
3. Skor eşiği önerisi + nasıl kalibre edildiği
4. ~100 örneklik etiketli kabul test seti (pozitif/negatif karışık; sansürlü
   varyasyonlar — "b0nus", "ç3vrim" — ve "Betül", "alfabetik" gibi tuzak
   negatifler dahil). Entegrasyonun doğruluğu bu setle sınanacak.

## Sınırlar (KVKK + demo cihazı)

- Tamamen cihaz üstü; eğitim dahil hiçbir aşamada kullanıcı verisi yok,
  çalışma anında ağ yok (uygulamada `INTERNET` izni yok ve eklenmeyecek)
- Metin başına ≤ 20 ms (orta segment telefon); ekran taraması tek olayda
  onlarca metin sorgulayabilir, toplam bütçe ~200 ms
- Girdi: UTF-8 Türkçe metin, tipik 5–200 karakter (ekrandaki tek öğenin
  metni, tam sayfa değil); Kalkan metni olduğu gibi verir

## Kalkan tarafında şimdiden yapılanlar (mükerrer iş olmasın)

- Kelime listesi eşleşmesi + "içeriyor mu" türev yakalama (`KeywordDetector`)
- Basit sansür normalizasyonu: 0→o, 1→i, 3→e, 4→a, 5→s, 7→t, @→a, $→s
  karakter dönüşümü eşleşmeden önce uygulanıyor. Model bunun ötesini
  (boşluklu/noktalı yazım, yeni argo, bağlam) hedeflemeli.
- Marka muafiyeti: "bahiskalkanı" türevleri tespitten muaf (uygulamanın adı
  "bahis" içeriyor). Model eğitiminde de negatif örnek olarak eklenmeli.

## Nasıl (önerilen yol)

1. **Veri:** elle + üretken çeşitleme ile kendi setini kur. Pozitifler:
   kupon paylaşımı, bonus/çevrim dili, Telegram davetleri, sansürlü yazımlar
   ("b0nus", "ç3vrim", boşluklu "b a h i s"). Negatifler: günlük sosyal medya
   dili + tuzak kelimeliler (spor haberi, "Betül", "iddialı" gibi). Sandbox
   gönderi JSON'u da (Ezgi) veri kaynağı olarak kullanılabilir.
2. **Baseline'ı basit tut (24 Temmuz):** karakter n-gram TF-IDF + lojistik
   regresyon bile kelime listesinden iyi olabilir; TFLite'a çevrilebilir
   olduğundan emin ol. Alternatif: Keras'ta küçük embedding + CNN/GRU metin
   sınıflandırıcısı → doğrudan TFLite'a dönüşür.
3. **Kaçınılacaklar:** büyük transformer'lar (boyut/gecikme bütçesini aşar),
   ağ gerektiren tokenizer'lar, Python'a bağımlı ön işleme (Kotlin'de
   yeniden yazılamıyorsa entegre edilemez).
4. **Doğrulama:** kabul test setinde hedef ≥ %90 doğruluk + tuzak
   negatiflerde sıfır yanlış pozitif; gecikmeyi telefonda ölçmek için
   Ebubekir'le entegrasyon sonrası ortak test.

## Bu hafta cevaplanacak açık sorular (Ebubekir'le birlikte)

- Tokenizasyon modelin içinde mi, dışında mı?
- Maksimum girdi uzunluğu; uzun metin kesme stratejisi?
- Hangi TFLite çalışma zamanı (LiteRT / org.tensorflow:tensorflow-lite)?
  Bağımlılık, model teslimiyle birlikte kalkan reposuna eklenecek.
- Eşik kalibrasyonu hangi veriyle (öneri: sandbox gönderi JSON'u + kabul
  test seti, birlikte)?

## Entegrasyon sonrası açık madde (18 Tem)

v10.4 + SurfaceGuard uygulamaya alındı. Yüzey kapısı taslağındaki eşikler
0.60 tabanına göreydi; nihai eşik 0.70 olduğu için oranlar korunarak sürüm
kapısının doğrulandığı 0.70-0.90 penceresine taşındı:

| Yüzey | Eşik |
|---|---|
| Genel (tarayıcı vb.) | 0.70 (`VARSAYILAN_ESIK`) |
| Mesajlaşma (WhatsApp/Telegram) | 0.80 |
| LinkedIn | 0.86 |
| Yemek/alışveriş | 0.88 |

Kalibrasyon turunda bu değerleri teyit et (gerekirse `esik_karari.json`
mantığıyla revize edelim). Cihaz gecikme ölçümü Ebubekir'den gelecek.

## Takvim

- **24 Temmuz:** baseline model entegrasyona hazır (yukarıdaki teslim
  formatında)
- 4-5 Ağustos: rapor için model entegre edilmiş demo (yetişmezse rapor
  kelime listesi + "model entegrasyonu sürüyor" anlatımıyla çıkar — baskı
  yok ama hedef bu)

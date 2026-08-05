# Saha Yanlış Alarmları — yeniden eğitim adayları

13 Temmuz, cihaz testi (Ebubekir). Uygulama eşiği **0.92** iken modelin
yanlış alarm verdiği gerçek ekran metinleri. Hata-analizi döngüsünün girdisi
(YOL_HARITASI "V3" maddesi): bu kalıplarda 50-100 hedefli negatif üretilip
v6 eğitimi yapılmalı.

## Kesin yanlış alarmlar (label=0 adayı)

| Metin | Kalıp |
|---|---|
| Hürriyet https://www.hurriyet.com.tr 76 milyar 284 milyon işlem hacmi! 68 şüpheli adliyeye sevk edildi | haber başlığı + URL + para/rakam yoğunluğu |
| Hürriyet https://www.hurriyet.com.tr Son Dakika Yasa Dışı Bahis Hakkında Güncel Haber ve Bilgiler | haber listeleme sayfası başlığı + URL |
| 780+ beğenme · 2 hafta önce | sosyal medya meta verisi (sayı + zaman) |
| Sadece kayıtlı üyeler yorum yapabilir. Bir kaç saniye içerisinde kayıt olabilirsiniz. | forum üyelik kalıbı — "kayıt ol" davetine benziyor ama bahis değil |

## Sınırda (etiket kararı Halil'in)

| Metin | Not |
|---|---|
| Kanal linkini dm atsana | Telegram davet kalıbı — bahis bağlamında gerçek sinyal, bağlamsız yorumda masum |
| Kanal linkini bana dm atarsın | aynı |

## İkinci tur (13 Tem akşam): çıplak URL alarmları

Model şu metinlerin hepsine alarm verdi — hepsi yalnızca adres:
`https://www.hurriyet.com.tr`, `https://play.google.com`,
`https://eksisozluk.com`, `https://www.instagram.com`.

Teşhis: eğitim pozitifleri link yoğun (t.me davetleri, site adresleri);
model "https://" desenini başlı başına teşvik sinyali öğrenmiş. v6 eğitiminde
**çıplak meşru URL'ler negatif olarak** eklenmeli; URL + davet metni
karışımı pozitifler kalmalı.

## Uygulama tarafında bu arada yapılanlar (mükerrer iş olmasın)

- Eşik 0.63 → 0.92 (tanı taramasındaki bant)
- 15 karakterden kısa metinler modele sorulmuyor
- contentDescription taranmıyor (yalnızca görünen metin)
- Tek başına URL olan metinler modele sorulmuyor

Yani v6'nın hedefi uzun-metin kalıpları (haber+URL karışık başlıklar,
meta veri, forum kalıbı); kısa metin, ikon ve çıplak URL kaynaklı alarmlar
uygulama tarafında çözüldü.

## V9 saha bulguları (16 Temmuz): uygulama entegrasyonu sonrası yeni FP aileleri

Ekran görüntülerinde URL/haber değil, Android yüzeyi bilinmediği için yanlış
blurlanan aileler görüldü. Kök neden: model `grup`, `katıl`, `kupon`, `puan`,
`TL`, `kod`, `IBAN`, `link` gibi bahis dünyasında güçlü sinyalleri WhatsApp
sistem mesajı, LinkedIn profil, yemek uygulaması, form/kod/PDF önizleme
yüzeylerinde de risk sanabiliyor.

Eklenen çözüm iki katmanlıdır:

- `kabul_saha.jsonl`: yeni exact/maskeli FP aileleri `bildirilen` seviyesinde
  regresyon kapısına eklendi; karşı riskli pozitifler `cekismeli` izleme satırı
  olarak eklendi.
- `egitim.jsonl`, `gercek.jsonl`, `kalibrasyon.jsonl`: aynı ailelerin
  genelleştirilmiş negatifleri ve riskli karşı pozitifleri sınırlı sayıda
  eklendi. Amaç exact ekranı ezberletmek değil, yüzey bağlamındaki masum
  kullanımları öğretmektir.

Android tarafında eklenen `SurfaceGuard` paketleri komple muaf tutmaz. Sadece
bahis risk anchor'ı yoksa WhatsApp sistem mesajı, kod/form/PDF, LinkedIn profil
ve yemek kuponu gibi düşük riskli yüzeyleri modelden önce temiz sayar; riskli
anchor varsa karar keyword/model yoluna gider.

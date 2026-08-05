# Gerçek Veri Kaynakları ve Lisans Notları

`gercek.jsonl` (4.111 eğitim örneği) ve `kabul_gercek.jsonl` (391 saha test
örneği) 13-18 Temmuz 2026 arasında halka açık kaynaklardan ve hedefli üretim
turlarından çok turlu derlendi. Her
satırın `kaynak`/`kategori` alanı kökenini taşır. Süreç her turda: toplama →
temizlik + KVKK maskeleme → bağımsız (zorlarda çekişmeli) etiket denetimi →
eğitim/kabul/kalibrasyon bölmesi (test setleri eğitimden ve sentetikten
kod düzeyinde ayrık — jenerelleme ölçülür, ezber değil).

## Toplama turları

| Tur | Kapsam | Yeni gerçek örnek |
|---|---|---|
| İlk tur | HF spam setleri, Telegram önizleme, şikayet, haber RSS, kampanya | ~1.375 |
| V6 | Saha FP kalıpları: haber+URL, çıplak URL, meta veri, üyelik, kumar-hakkında | +419 |
| V7 | Meşru kupon/kampanya + üyelik CTA + marka-kod-link | +143 |
| V8 | 37 kategorili tam taksonomi (12 ★ yeni zor tuzak: gacha, çekiliş, piyango, fantezi lig, kripto airdrop, marka-haber, argo-masum, kısa-masum; emoji-minimal, farklı bahis türleri, ödeme, influencer, İng-Tr) | +1.132 (eğitim) + 271 (test) + 175 (kalibrasyon) |
| V10-V10.2 | Saha FP ailelerinin genelleştirilmiş örnekleri (WhatsApp-sistem, LinkedIn, puan-kupon, uzun-dilim), karşı-olgusal çiftler, yemek-kupon, kısa bonus-SMS, tombala/poker, dekont, güvenlik-uyarısı, borç-anlatısı | +556 (eğitim) + kalibrasyon payı |
| V10.3 (18 Tem) | Terim-dengesi turu: 16 hedefli aile (üretici + bağımsız çekişmeli denetçi hattı) + HF madenciliği — c4-tr gerçek bahis-SEO pozitifleri, winvoker/c4 organik masum-terim negatifleri (bonus/kupon/kanal/link/puan/üye/IBAN) | +509 (eğitim) + 47 (kalibrasyon); 2 örnek model-dışı |
| V10.4 (18 Tem) | Koşu-1 hata ailelerine cerrahi tur: forum üyelik/yorum duvarı, gacha/fantezi/airdrop/spor-kura negatifleri; dekont-sosyal-kanıt, kısa bedava-TL, BÜYÜKHARF marka-link, çarpan-CTA, kurumsal-tanıtım pozitifleri (üretici + bağımsız çekişmeli denetçi) | +190 (eğitim) + 16 (kalibrasyon) |

V8 turu 74 paralel parti hâlinde yürütüldü (kategori başına toplama + bağımsız denetim;
★ kategorilerde çekişmeli ikinci denetim). 1.578 aday üretildi, denetim
sonrası eğitim/test/kalibrasyona bölündü.

## Kaynaklar

| Kaynak | Tür | Lisans / durum |
|---|---|---|
| HuggingFace `allenai/c4` (tr) | Bahis sitesi tanıtım/SEO cümleleri (poz) + haber/uyarı/spam-dışı (neg) | ODC-BY 1.0 (atıf şartlı) |
| HF `winvoker/turkish-sentiment-analysis-dataset` | Normal Türkçe kısa metin (neg) | CC-BY-SA-4.0 — veri seti YAYIMLANIRSA aynı lisans şartı |
| HF `FredZhang7/all-scam-spam` | Türkçe bahis-dışı spam (neg) | Apache-2.0 |
| HF `eskfestsecurity/turkish-igaming-spam-detection` | 6 örnek | CC-BY-4.0 (küçük ve elle derlenmiş görünümlü — temkinli) |
| Telegram kanal web önizlemeleri (`t.me/s/…`, 18 kanal) | Gerçek teşvik gönderileri (poz) | Resmi lisans yok; halka açık ticari spam. **Proje içi kullanım — ham haliyle yeniden yayımlamayın** |
| sikayetvar.com (~920 sayfa tarandı) + ekşi sözlük + technopat | Alıntılanan gerçek spam SMS'ler (poz) + bahis kelimeli teşviksiz şikayet cümleleri (neg) | Aynı şekilde proje içi kullanım |
| Haber RSS'leri (NTV, TRT, Sözcü vb.) | Başlıklar: genel + spor + "yasa dışı bahis operasyonu" (neg) | Başlık düzeyinde alıntı |
| Kampanya/oyun sayfaları | Meşru "kazan/bonus/fırsat" dili (neg) | Metin parçası düzeyinde |

**Lisansı belirsiz olduğu için KULLANILMAYANLAR:** akuysal/turkishSMS-ds,
anilguven/turkish_spam_email, Kaggle Türkçe SMS aynaları, batubayk/TR-News,
GPL'li çeviri SMS seti, vngrs-web-corpus (NC kısıtı), M-Arjun/SpamShield
(kapalı erişim). İleride kullanılacaksa yazarlardan izin istenmeli.

## KVKK / gizlilik önlemleri

- Telefon numaraları biçim korunarak sahteleştirildi (`0500 000 00 00`),
  IBAN'lar `TR00 …` yapıldı (toplayıcıda + temizlik regex'i + denetçi,
  üç katman).
- Sıradan kişilerin ad-soyadları alınmadı/`[AD]` yapıldı; şikayetçi
  kimlikleri hiç toplanmadı.
- Kanal ve bahis sitesi adları kamuya açık ticari spam kimliği olarak
  korundu (tespit sinyali).
- Yalnızca üyeliksiz, halka açık sayfalar kullanıldı; hesap açılmadı,
  hiçbir bot koruması aşılmadı.

## Bilinen sınırlılıklar (dürüst not)

- `p-web` pozitifleri (C4 kaynaklı, 364 adet) web sitesi düzyazısı —
  sosyal medya üslubundan farklı; mesaj-üslubu gerçek örnekler (Telegram
  182 + şikayet 42) bunu dengeliyor ve gerçek kabul setinde altın
  kaynaklara ağırlık verildi (60 pozitifin 50'si Telegram+şikayet).
- Telegram önizlemeleri özetleyici bir katmandan geçtiği için tek tük
  gönderi kırpılmış olabilir; denetçiler anlamsız kalanları attı.
- Şablonlaşmış spam (aynı kalıp farklı miktar) rakam-bağımsız anahtarla
  teklendi; yine de Telegram/SMS dilinde doğal tekrarlılık vardır.

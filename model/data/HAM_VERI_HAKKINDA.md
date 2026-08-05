# Ham eğitim verisi neden bu depoda yok?

Bu klasörde normalde bulunması gereken ham eğitim/kalibrasyon dosyaları
(`egitim.jsonl`, `gercek.jsonl`, `kabul_gercek.jsonl`, `kabul_saha.jsonl`,
`kabul_testi.jsonl`, `kalibrasyon.jsonl`, `model_disi_negatifler.jsonl`)
**bilinçli olarak bu depoya eklenmemiştir.**

**Neden:** Bu dosyalar, modelin "bahse teşvik" örneklerini tanıması için
toplanmış/üretilmiş binlerce metin satırı içerir — bir kısmı gerçek
Telegram kanal önizlemelerinden ve şikayet sitelerinden derlenmiştir (bkz.
[GERCEK_VERI_KAYNAKLARI.md](GERCEK_VERI_KAYNAKLARI.md)). Bu ham metinleri
olduğu gibi bir GitHub deposunda barındırmak, içerik olarak gerçek bahis
reklamı/teşvik metni yayımlamak anlamına gelir; bu da platform politikaları
açısından istenmeyen bir durumdur.

**Ne kayboldu, ne kaybolmadı:** Bu dosyalar yalnızca eğitim aşamasında
kullanılır; **uygulama çalışırken hiçbir şekilde okunmaz.** Android
uygulaması ve Chrome eklentisi, eğitilmiş ve derlenmiş model dosyasını
(`cikti/model.tflite`) kullanır — bu dosya depoda mevcuttur ve tamamen
işlevseldir. Ham verinin yokluğu uygulamanın çalışmasını hiçbir şekilde
etkilemez; yalnızca modelin sıfırdan yeniden eğitilmesi bu depo üzerinden
mümkün olmaz.

Veri toplama yöntemi, kaynakları, KVKK önlemleri ve etiketleme süreci
[GERCEK_VERI_KAYNAKLARI.md](GERCEK_VERI_KAYNAKLARI.md) ve proje raporunda
ayrıntılı olarak anlatılmıştır.

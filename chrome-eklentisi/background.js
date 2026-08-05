// Sayaç, popup ile içerik betiği arasında paylaşılır
chrome.storage.session.setAccessLevel({
  accessLevel: "TRUSTED_AND_UNTRUSTED_CONTEXTS"
});

// Kelime listesi ve meşru alan listesi YALNIZCA burada okunur; içerik betiği
// mesajla ister. Böylece iki şey birden çözülür:
//  1. Dosyaların web_accessible_resources ile her siteye açılması gerekmez
//     (aksi halde herhangi bir sayfa eklentinin kurulu olduğunu anlayabilir).
//  2. Dosyalar tarama başına değil, oturumda bir kez okunur (sonsuz
//     kaydırmalı sayfalarda gereksiz iş yapılmaz).
let veriSozu = null;

async function veriyiYukle() {
  const [keywords, alanDosyasi] = await Promise.all([
    fetch(chrome.runtime.getURL("keywords.json")).then((r) => r.json()),
    fetch(chrome.runtime.getURL("mesru_alanlar.json")).then((r) => r.json())
  ]);
  return { keywords, alanlar: alanDosyasi.alanlar };
}

chrome.runtime.onMessage.addListener((mesaj, gonderen, yanitla) => {
  if (!mesaj || mesaj.tur !== "veriIste") return;

  if (!veriSozu) veriSozu = veriyiYukle();
  veriSozu
    .then(yanitla)
    .catch(() => {
      veriSozu = null; // sonraki istekte yeniden denensin
      yanitla(null);
    });

  return true; // yanıt asenkron gelecek
});

// Android uygulamasındaki BlockStats.YEDAM_THRESHOLD ile aynı eşik
const YEDAM_ESIGI = 3;

chrome.storage.session.get(["engellenenSayisi", "yineDeGosterSayisi"], (result) => {
  const sayi = result.engellenenSayisi || 0;
  document.getElementById("sayac").textContent = sayi;
  if ((result.yineDeGosterSayisi || 0) >= YEDAM_ESIGI) {
    document.getElementById("yedam").style.display = "block";
  }
});

chrome.storage.session.get(["engellenenSayisi"], (result) => {
  const sayi = result.engellenenSayisi || 0;
  document.getElementById("sayac").textContent = sayi;
});
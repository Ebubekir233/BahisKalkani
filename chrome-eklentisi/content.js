let sayacKuyrugu = Promise.resolve();

function sayaciArtir() {
  sayacKuyrugu = sayacKuyrugu.then(() => {
    return new Promise((resolve) => {
      chrome.storage.session.get(["engellenenSayisi"], (result) => {
        const mevcut = result.engellenenSayisi || 0;
        chrome.storage.session.set({ engellenenSayisi: mevcut + 1 }, resolve);
      });
    });
  });
}
// Kelime listesi + meşru alan listesi background.js'ten bir kez alınır
let veriSozu = null;
function veriAl() {
  if (!veriSozu) {
    veriSozu = new Promise((resolve) => {
      chrome.runtime.sendMessage({ tur: "veriIste" }, (yanit) => {
        if (chrome.runtime.lastError || !yanit) {
          veriSozu = null; // sonraki taramada yeniden denensin
          resolve(null);
        } else {
          resolve(yanit);
        }
      });
    });
  }
  return veriSozu;
}

// Şu anki sayfanın alan adı meşru listede mi?
// Alt alan sınırına dikkat: düz "içeriyor mu" kontrolü yapılırsa bir bahis
// sitesi kendine "hurriyet.com.tr.bahis-sitesi.xyz" adını verip meşru
// sayılabilir ve genel terim katmanını devre dışı bırakabilirdi.
function isSafeDomain(safeDomains) {
  const host = window.location.hostname.toLowerCase().replace(/^www\./, '');
  return safeDomains.some(domain => host === domain || host.endsWith('.' + domain));
}

function hideNode(parent) {
  if (!parent || parent.dataset.bahiskalkaniKapatildi) return;

  parent.dataset.bahiskalkaniKapatildi = "true";
  parent.style.position = "relative";
  const overlay = document.createElement("div");
  overlay.className = "bk-ignore"; // taramadan hariç tutulacak
  overlay.style.cssText = `
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgb(20, 20, 20);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    z-index: 9999;
    border-radius: 4px;
  `;
  overlay.innerHTML = `
    <span>🛡️ Bahis içeriği gizlendi</span>
    <button class="bk-goster" style="
      margin-left: 8px; padding: 4px 10px; font-size: 12px;
      background: white; color: black; border: none;
      border-radius: 4px; cursor: pointer;
    ">Yine de göster</button>
  `;

  const tekrarGizleBtn = document.createElement("button");
  tekrarGizleBtn.className = "bk-ignore"; // taramadan hariç tutulacak
  tekrarGizleBtn.textContent = "🛡️";
  tekrarGizleBtn.title = "Tekrar gizle";
  tekrarGizleBtn.style.cssText = `
    position: absolute;
    top: 4px; right: 4px;
    width: 24px; height: 24px;
    font-size: 12px;
    background: rgba(20,20,20,0.9);
    color: white;
    border: none;
    border-radius: 50%;
    cursor: pointer;
    z-index: 9998;
    display: none;
  `;

  overlay.querySelector(".bk-goster").addEventListener("click", (e) => {
    e.stopPropagation();
    overlay.style.display = "none";
    tekrarGizleBtn.style.display = "block";
  });

  tekrarGizleBtn.addEventListener("click", (e) => {
    e.stopPropagation();
    overlay.style.display = "flex";
    tekrarGizleBtn.style.display = "none";
  });

  parent.appendChild(overlay);
  parent.appendChild(tekrarGizleBtn);
  sayaciArtir();
}

// Kapatılacak kutuyu seç. Demo sayfasında gönderiler ".post" sınıflı; gerçek
// sitelerde bu sınıf yok ve yalnızca eşleşen metnin en yakın etiketi (örneğin
// arama sonucundaki kalın yazılı parça) kapanıyor, başlığın kalanı ve bahis
// sitesinin adresi açıkta kalıyordu (Bing testi: 10 kartın 10'u yarım).
// Bu yüzden önce ".post", sonra genel "kart" kalıpları (article / liste öğesi)
// aranır. Kart ekranın çoğunu kaplıyorsa (sayfa iskeleti) seçilmez, yalnızca
// eşleşen metnin etiketi kapatılır.
const KART_SECICI = 'article, [role="article"], li';
function kapsayiciBul(el) {
  const post = el.closest('.post');
  if (post) return post;
  const kart = el.closest(KART_SECICI);
  if (kart && kart !== document.body) {
    const yukseklik = kart.getBoundingClientRect().height;
    if (yukseklik > 0 && yukseklik < window.innerHeight * 0.8) return kart;
  }
  return el;
}

let taramaCalisiyor = false;
let tekrarTaramaGerekli = false;

async function scanPage() {
  if (taramaCalisiyor) {
    // Zaten bir tarama çalışıyor, bitince bir kere daha çalışsın diye işaretle
    tekrarTaramaGerekli = true;
    return;
  }
  taramaCalisiyor = true;

  try {
    const veri = await veriAl();
    if (!veri) return; // liste okunamadıysa tarama yapma

    const safe = isSafeDomain(veri.alanlar);
    const aktifKeywords = safe
      ? { ...veri.keywords, genel: [] }
      : veri.keywords;
const walker = document.createTreeWalker(
      document.body,
      NodeFilter.SHOW_TEXT,
      {
        acceptNode(node) {
          const parent = node.parentElement;
          if (!parent) return NodeFilter.FILTER_REJECT;

          // Kendi eklediğimiz kutuları atla
          if (parent.closest('.bk-ignore')) {
            return NodeFilter.FILTER_REJECT;
          }
          // Sayfa kodu/stilini (script, style) hiç metin olarak sayma
          if (parent.closest('script, style, noscript')) {
            return NodeFilter.FILTER_REJECT;
          }
          return NodeFilter.FILTER_ACCEPT;
        }
      }
    );

   const matchedContainers = new Set();
    let node;
    while (node = walker.nextNode()) {
      const text = node.nodeValue.trim();
      if (text.length === 0) continue;

      if (isBettingContent(text, aktifKeywords)) {
        // Gönderinin tamamını (varsa .post kutusunu) hedefle, sadece
        // eşleşen küçük metin parçasını değil — aynı gönderi birden
        // fazla parça eşleşse bile Set sayesinde tek sayılır
        const container = kapsayiciBul(node.parentElement);
        matchedContainers.add(container);
      }
    }

    matchedContainers.forEach(hideNode);
  } finally {
    taramaCalisiyor = false;
    if (tekrarTaramaGerekli) {
      tekrarTaramaGerekli = false;
      scanPage(); // beklemede kalan tarama isteği varsa şimdi çalıştır
    }
  }
}

scanPage();

let taramaZamanlayici = null;
function taramaPlanla() {
  if (taramaZamanlayici) clearTimeout(taramaZamanlayici);
  taramaZamanlayici = setTimeout(() => {
    scanPage();
  }, 150);
}

const observer = new MutationObserver(() => {
  taramaPlanla();
});

observer.observe(document.body, {
  childList: true,
  subtree: true
});
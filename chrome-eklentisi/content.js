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

// --- YEDAM yönlendirmesi ---
// Kullanıcı engellenen içeriği sık sık "Yine de göster" ile açıyorsa, onu
// yargılamadan Yeşilay Danışmanlık Merkezi'ne (YEDAM) yönlendiren bir kart
// gösterilir. Android uygulamasıyla aynı eşik ve aynı metin kullanılır.
// KVKK: yalnızca bir sayı tutulur, oturum deposunda (tarayıcı kapanınca
// silinir); hangi içeriğin açıldığı kaydedilmez, hiçbir yere gönderilmez.
const YEDAM_ESIGI = 3;
// Bilgisayarda "tel:" bağlantısı çoğu zaman çalışmaz; bu yüzden kart telefonu
// aramak yerine YEDAM'ın resmi sayfasını açar, 115 ise ayrıca yazılır.
// yedam.org.tr erişilemez olduğunda da açılsın diye Yeşilay'ın kendi
// sitesindeki YEDAM sayfası kullanılır. "noreferrer": açılan site,
// kullanıcının hangi sayfadan (ör. engellenen bir içerikten) geldiğini görmez.
const YEDAM_SAYFASI = "https://www.yesilay.org.tr/yesilay-danismanlik-merkezi/";

function yineDeGosterKaydet() {
  sayacKuyrugu = sayacKuyrugu.then(() => {
    return new Promise((resolve) => {
      chrome.storage.session.get(["yineDeGosterSayisi", "yedamKartiKapatildi"], (result) => {
        const yeni = (result.yineDeGosterSayisi || 0) + 1;
        chrome.storage.session.set({ yineDeGosterSayisi: yeni }, () => {
          if (yeni >= YEDAM_ESIGI && !result.yedamKartiKapatildi) yedamKartiGoster();
          resolve();
        });
      });
    });
  });
}

function yedamKartiGoster() {
  if (document.getElementById("bk-yedam-karti")) return;

  // Kart Shadow DOM içinde: sitenin kendi stilleri kartı bozamaz, kartın
  // metni de sayfa taramasına girmez.
  const kutu = document.createElement("div");
  kutu.id = "bk-yedam-karti";
  kutu.className = "bk-ignore";
  kutu.style.cssText = "position: fixed; right: 20px; bottom: 20px; z-index: 2147483647;";
  const kok = kutu.attachShadow({ mode: "closed" });
  kok.innerHTML = `
    <style>
      .kart {
        width: 320px; box-sizing: border-box; padding: 16px 18px;
        background: #e8f5e9; color: #1b3d1f; border-radius: 14px;
        font-family: -apple-system, "Segoe UI", Roboto, sans-serif;
        box-shadow: 0 8px 28px rgba(0, 0, 0, 0.35);
      }
      .baslik { font-size: 16px; font-weight: 700; margin: 0 0 8px; }
      .metin { font-size: 13px; line-height: 1.5; margin: 0 0 12px; }
      .dugmeler { display: flex; gap: 8px; align-items: center; }
      .ara {
        flex: 1; text-align: center; padding: 9px 12px; border-radius: 10px;
        background: #2e7d32; color: #fff; font-weight: 700; font-size: 14px;
        text-decoration: none;
      }
      .kapat {
        padding: 9px 12px; border-radius: 10px; border: 1px solid #9bbf9e;
        background: transparent; color: #1b3d1f; font-size: 13px; cursor: pointer;
      }
      .hat { font-size: 14px; font-weight: 700; margin: 0 0 12px; }
    </style>
    <div class="kart" role="dialog" aria-label="YEDAM yönlendirmesi">
      <p class="baslik">💚 Yalnız değilsin</p>
      <p class="metin">Engellenen içeriği sık sık görüntülediğini fark ettik. İstersen
        Yeşilay Danışmanlık Merkezi'ne (YEDAM) ücretsiz ve gizlilik içinde ulaşabilirsin.</p>
      <p class="hat">📞 Danışma hattı: 115</p>
      <div class="dugmeler">
        <a class="ara" href="${YEDAM_SAYFASI}" target="_blank" rel="noopener noreferrer">YEDAM sayfasını aç</a>
        <button class="kapat" type="button">Kapat</button>
      </div>
    </div>`;

  kok.querySelector(".kapat").addEventListener("click", () => {
    kutu.remove();
    chrome.storage.session.set({ yedamKartiKapatildi: true });
  });

  document.body.appendChild(kutu);
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
    yineDeGosterKaydet();
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
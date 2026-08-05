// Sansürlü yazım çözümü: b0nus -> bonus, ç3vrim -> çevrim
const CENSOR_MAP = {
  '0': 'o', '1': 'i', '3': 'e',
  '4': 'a', '5': 's', '7': 't',
  '@': 'a', '$': 's'
};

// Türkçe harf katlama: ı->i, ç->c ... Metne VE kelime listesine birlikte
// uygulanır; amaç iki yönlü eşleşme sorununu kökten çözmek:
//   - Düz toLowerCase()/elle I->i : "CANLI BAHİS" -> "canli bahis" olur,
//     'ı' içeren kelimeler ("canlı bahis", "gözaltı") eşleşmez.
//   - Yalnız toLocaleLowerCase('tr') : "FREE SPIN" -> "free spın" olur,
//     İngilizce kelimeler ("free spin") eşleşmez.
// Katlama sonrası her iki taraf da aksansız ASCII tabanına indiğinden iki
// durum da doğru çalışır; ayrıca "bahıs" gibi aksansız yazımlar da yakalanır.
const FOLD_MAP = {
  'ı': 'i', 'ç': 'c', 'ğ': 'g', 'ö': 'o', 'ş': 's', 'ü': 'u',
  'â': 'a', 'î': 'i', 'û': 'u'
};

function foldTr(text) {
  let result = '';
  for (const ch of text) result += (FOLD_MAP[ch] !== undefined ? FOLD_MAP[ch] : ch);
  return result;
}

// Metni normalize eder: Türkçe küçük harf -> sansür çözme -> harf katlama
function normalizeText(text) {
  let result = text.toLocaleLowerCase('tr');
  for (const [censored, real] of Object.entries(CENSOR_MAP)) {
    result = result.split(censored).join(real);
  }
  return foldTr(result);
}

// Kelime listeleri de aynı normalizasyondan geçmeli. Liste her metin için
// yeniden işlenmesin diye dizi bazında önbelleğe alınır.
const listeOnbellek = new WeakMap();
function hazirla(liste) {
  if (!liste) return [];
  let hazirListe = listeOnbellek.get(liste);
  if (!hazirListe) {
    hazirListe = liste.map(terim => foldTr(terim.toLocaleLowerCase('tr')));
    listeOnbellek.set(liste, hazirListe);
  }
  return hazirListe;
}

// ignored listesindeki kelimeleri metinden çıkarır
function removeIgnored(text, ignoredList) {
  let result = text;
  for (const ignored of ignoredList) {
    result = result.split(ignored).join('');
  }
  return result;
}

function isBettingContent(text, keywords) {
  const cleaned = removeIgnored(normalizeText(text), hazirla(keywords.ignored));

  // Kesin ifadeler her zaman engelleme sebebi (muaf bile olsa)
  if (hazirla(keywords.kesin).some(term => cleaned.includes(term))) return true;

  // Muaf (haber/uyarı) bağlamı varsa, genel terimler artık sebep sayılmaz
  if (hazirla(keywords.muaf).some(term => cleaned.includes(term))) return false;

  // Muaf değilse, genel terimlere de bak
  return hazirla(keywords.genel).some(term => cleaned.includes(term));
}

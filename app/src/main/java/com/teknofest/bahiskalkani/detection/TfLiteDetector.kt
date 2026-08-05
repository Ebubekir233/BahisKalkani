package com.teknofest.bahiskalkani.detection

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import org.json.JSONObject
import org.tensorflow.lite.Interpreter

/**
 * Faz 2 tespitçisi (v3): karakter seviyesi TFLite topluluk modeliyle 0..1
 * "bahse teşvik" skoru üretir; saha yanlış alarmlarına karşı koruma
 * katmanları içerir:
 *
 *  1. URL kanalı: URL'ler modele HAM verilmez. Meşru alan adları
 *     (assets/mesru_alanlar.json) metinden silinir; kalan URL'ler tek 🔗
 *     jetonuna indirgenir.
 *  2. Çıplak URL kapısı: URL'ler çıkarılınca geriye anlamlı metin
 *     kalmıyorsa hiç alarm üretilmez (modele de sorulmaz).
 *  3. Meta-veri süzgeci: "780+ beğenme · 2 hafta önce" gibi salt
 *     sayaç/zaman metinleri ve tarayıcı üretimi sabit metinler ("IP
 *     adresinizden" konum alt bilgisi) modele sorulmadan temiz sayılır.
 *
 * Ön işleme, model/spec/ON_ISLEME.md (v3) sözleşmesiyle birebir aynı olmak
 * ZORUNDA — URL regex'i ve fold haritası dahil değişiklik Python tarafıyla
 * birlikte yapılır. DİKKAT: v3 ön işleme yalnız v3 sözlüğüyle (surum 3)
 * eğitilmiş modelle çalışır; model + sözlük + bu dosya BİRLİKTE güncellenir.
 * Asset kaynağı: model/cikti/ + model/data/mesru_alanlar.json.
 *
 * Not: Interpreter iş parçacığı güvenli değildir; servis ana akışından
 * (tek iş parçacığı) çağrılmak üzere tasarlandı.
 */
class TfLiteDetector(
    private val interpreter: Interpreter,
    private val char2id: Map<String, Int>,
    private val maxLen: Int,
    private val threshold: Float,
    private val mesruAlanlar: Set<String>,
) : Detector {

    private val turkish = Locale.forLanguageTag("tr")
    private val input = Array(1) { IntArray(maxLen) }
    private val output = Array(1) { FloatArray(1) }

    override fun isBettingContent(text: String): Boolean = isBettingContent(text, threshold)

    /** Yüzeye göre eşikle karar (SurfaceGuard.thresholdFor ile kullanılır);
     *  koruma katmanları eşikten bağımsız aynıdır. */
    fun isBettingContent(text: String, esik: Float): Boolean {
        if (isMetadata(text)) return false                       // katman 3
        val normalized = normalizeUrls(fold(Normalizer.normalize(text, Normalizer.Form.NFC)))
        val kalan = normalized.replace(URL_TOKEN, " ").trim()
        if (kalan.length < 3) return false                       // katman 2 (çıplak URL)
        return score(normalized) >= esik
    }

    /** 0..1 arası teşvik skoru — eşik kalibrasyonu ve hata ayıklama için açık.
     *  Girdi ham metin de olabilir; URL normalizasyonu idempotenttir. */
    fun score(text: String): Float {
        preprocess(text, input[0])
        interpreter.run(input, output)
        return output[0][0]
    }

    /** Katman 1 — spec §URL: meşru alan adı → silinir, diğer URL → 🔗. */
    private fun normalizeUrls(text: String): String =
        URL_RE.replace(text) { m ->
            val alan = m.value
                .replace(Regex("(?i)^https?://"), "")
                .replace(Regex("(?i)^www\\."), "")
                .substringBefore('/').substringBefore('?').trim().lowercase(Locale.ROOT)
            if (alan in mesruAlanlar) " " else " $URL_TOKEN "
        }

    /** Katman 3: '·' ile ayrılmış her parça sayaç/zaman/buton kalıbıysa
     *  meta-veridir; tarayıcı üretimi sabit metinler de burada elenir. */
    private fun isMetadata(text: String): Boolean {
        val t = text.trim()
        if (t.length > 64) return false
        if (SISTEM_METNI_KALIPLARI.any { it.containsMatchIn(t) }) return true
        val parcalar = t.split('·', '•', '|').map { it.trim() }.filter { it.isNotEmpty() }
        return parcalar.isNotEmpty() && parcalar.all { p ->
            META_KALIPLARI.any { it.matches(p) }
        }
    }

    /** spec §FOLD (v3): tipografik katlama + U+FE0F silme — train.py fold()
     *  ile BİREBİR aynı harita. İdempotenttir; tüm karakterler BMP olduğu
     *  için Char döngüsü güvenlidir (emoji vekil çiftleri değişmeden geçer). */
    private fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when (ch) {
                '\uFE0F' -> {}                             // varyasyon seçicisi: sil
                '\u00A0' -> sb.append(' ')                 // NBSP
                '’', '‘', '´', '`' -> sb.append('\'')
                '“', '”', '«', '»', '„' -> sb.append('"')
                '–', '—' -> sb.append('-')
                '…' -> sb.append("...")
                '•', '·' -> sb.append('.')
                'â', 'Â' -> sb.append('a')
                'î', 'Î' -> sb.append('i')
                'û', 'Û' -> sb.append('u')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** spec/ON_ISLEME.md v3: NFC → fold → URL normalizasyonu → tr küçük harf
     *  → U+0307 temizliği → kodpoint→id → 192'ye kes/doldur. */
    private fun preprocess(text: String, dest: IntArray) {
        val lower = normalizeUrls(fold(Normalizer.normalize(text, Normalizer.Form.NFC)))
            .lowercase(turkish)
            .replace("̇", "")
        var i = 0
        val it = lower.codePoints().iterator()
        while (i < maxLen && it.hasNext()) {
            val cp = it.nextInt()
            dest[i++] = char2id[String(Character.toChars(cp))] ?: OOV
        }
        while (i < maxLen) dest[i++] = PAD
    }

    companion object {
        private const val PAD = 0
        private const val OOV = 1
        private const val URL_TOKEN = "🔗"

        /** Python tarafındaki URL_RE ile BİREBİR aynı (spec §URL). */
        private val URL_RE = Regex(
            "(?i)(?:https?://|www\\.|t\\.me/)\\S+" +
                "|\\b[a-z0-9çğıöşü-]{2,}(?:\\.[a-z0-9-]+)*\\." +
                "(?:com|net|org|info|biz|xyz|bet|tv|club|site|online|top|io|me|mobi)" +
                "(?:\\.tr)?\\b(?:/[^\\s]*)?" +
                "|\\b[a-z0-9-]{2,}\\.(?:gov|edu|bel|pol|av|k12)\\.tr\\b(?:/[^\\s]*)?",
        )

        /** Salt meta-veri parçaları: "780+ beğenme", "2 hafta önce", "1,2B görüntülenme",
         *  "Paylaş", "Kaydet" gibi tek başına masum ekran kalıpları. */
        private val META_KALIPLARI = listOf(
            Regex("(?i)^[\\d.,+ ]+[bkm]?\\s*(beğenme|beğeni|yorum|görüntülenme|izlenme|paylaşım|takipçi|abone|oy|puan|indirme)\\S*$"),
            Regex("(?i)^\\d+\\s*(sn|dk|sa|saat|dakika|gün|hafta|ay|yıl)\\s*önce$"),
            Regex("(?i)^(paylaş|kaydet|bildir|takip et|abone ol|beğen|yanıtla|yorum yap)$"),
            Regex("^\\d+([.,]\\d+)?[bkmBKM]?$"),
            Regex("^\\d+\\s*/\\s*\\d+$"),
        )

        /** Tarayıcı/işletim sistemi üretimi sabit metinler — kullanıcı
         *  içeriği değil, hiçbir zaman teşvik olamaz. Ör. Chrome'un adres
         *  çubuğu konum alt bilgisi: "23350, Elazığ - IP adresinizden". */
        private val SISTEM_METNI_KALIPLARI = listOf(
            Regex("(?i)ip\\s*adresinizden"),
        )

        /** Önerilen eşik — v10.4 (18 Tem): insan kararı, esik_karari.json ile
         *  eşitlenir. İki BAĞIMSIZ artefaktta 0.70-0.90 penceresi sürüm
         *  kapısının üç koşulunu da geçti; 0.70 = pencerenin recall-dostu
         *  ucu. @0.70 resmi kayıt (cikti/esik.json): sözleşme %100 (tuzak 0),
         *  gerçek-391 %94.9 (FP 1), saha bildirilen 0 FP + çekişmeli 0/34,
         *  INV-URL 0 ihlal, ~0.3 ms. Yüzeye göre daha sıkı eşikler
         *  SurfaceGuard'da. */
        const val VARSAYILAN_ESIK = 0.70f

        fun fromAssets(context: Context, threshold: Float = VARSAYILAN_ESIK): TfLiteDetector {
            val model = context.assets.open("model.tflite").readBytes()
            val buffer = java.nio.ByteBuffer.allocateDirect(model.size)
                .order(java.nio.ByteOrder.nativeOrder())
            buffer.put(model)
            buffer.rewind()

            val vocab = JSONObject(
                context.assets.open("model_vocab.json").bufferedReader().use { it.readText() },
            )
            val chars = vocab.getJSONObject("karakterler")
            val char2id = buildMap {
                for (key in chars.keys()) put(key, chars.getInt(key))
            }
            val alanlar = try {
                val j = JSONObject(
                    context.assets.open("mesru_alanlar.json").bufferedReader().use { it.readText() },
                ).getJSONArray("alanlar")
                buildSet { for (i in 0 until j.length()) add(j.getString(i)) }
            } catch (e: Exception) {
                emptySet() // liste yoksa tüm URL'ler 🔗 olur; tespit çalışmaya devam eder
            }
            return TfLiteDetector(
                interpreter = Interpreter(buffer),
                char2id = char2id,
                maxLen = vocab.getInt("max_len"),
                threshold = threshold,
                mesruAlanlar = alanlar,
            )
        }
    }
}

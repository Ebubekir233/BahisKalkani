package com.teknofest.bahiskalkani.detection

import java.util.Locale

data class SurfaceContext(
    val packageName: String? = null,
    val rootClassName: String? = null,
    val nodeClassName: String? = null,
    val eventType: Int? = null,
)

/**
 * Yüzey kapısı (Halil'in v10 önerisi, model/oneriler/): bariz UI/sistem/meta
 * metinlerini modele hiç sormadan eler ve yüzeye göre eşik seçer. Bilinçli
 * olarak muhafazakâr: metinde bahis-risk çapası varsa muafiyet UYGULANMAZ —
 * spam kendini sistem mesajı gibi yazarsa yine taranır.
 *
 * Eşikler: taslak 0.60 tabanına göreydi; v10.4 nihai eşiği 0.70 olduğu için
 * oranlar korunarak sürüm kapısının doğrulandığı 0.70-0.90 penceresine
 * taşındı. Sandbox kalibrasyon turunda Halil'le teyit edilecek.
 */
class SurfaceGuard {

    fun shouldBypassModel(text: String, context: SurfaceContext = SurfaceContext()): Boolean {
        val n = normalize(text)
        if (n.isBlank() || hasBettingRiskAnchor(n)) return false

        return isWhatsappSystemMessage(n, context) ||
            isCodeLike(n) ||
            isFormOrIdentityField(n) ||
            isFilePreview(n) ||
            isForumOrLoginChrome(n) ||
            isProfessionalProfileText(n, context) ||
            isFoodCouponSurface(n, context) ||
            isUiMetadata(n)
    }

    fun thresholdFor(text: String, context: SurfaceContext = SurfaceContext()): Float {
        val n = normalize(text)
        if (hasBettingRiskAnchor(n)) return GENERAL_THRESHOLD
        return when {
            isMessagingPackage(context) -> MESSAGING_THRESHOLD
            isProfessionalPackage(context) -> PROFESSIONAL_THRESHOLD
            isFoodOrShoppingPackage(context) -> FOOD_THRESHOLD
            else -> GENERAL_THRESHOLD
        }
    }

    fun decisionCacheKey(text: String, context: SurfaceContext = SurfaceContext()): Int {
        var result = CACHE_VERSION
        result = 31 * result + text.hashCode()
        result = 31 * result + (context.packageName ?: "").hashCode()
        result = 31 * result + (context.rootClassName ?: "").hashCode()
        result = 31 * result + (context.nodeClassName ?: "").hashCode()
        result = 31 * result + (context.eventType ?: 0)
        return result
    }

    fun hasBettingRiskAnchorForTest(text: String): Boolean =
        hasBettingRiskAnchor(normalize(text))

    private fun isWhatsappSystemMessage(n: String, context: SurfaceContext): Boolean {
        if (!isWhatsappPackage(context)) return false
        return WHATSAPP_SYSTEM_PATTERNS.any { it.matches(n) }
    }

    private fun isCodeLike(n: String): Boolean {
        val codeHits = CODE_PATTERNS.count { it.containsMatchIn(n) }
        val symbolHits = CODE_SYMBOLS.count { n.contains(it) }
        return codeHits >= 1 || symbolHits >= 3
    }

    private fun isFormOrIdentityField(n: String): Boolean {
        if (n.length > 140) return false
        if (FORM_LABEL_PATTERNS.any { it.matches(n) }) return true
        return EMAIL_RE.matches(n) || PHONE_RE.matches(n) || IBAN_RE.matches(n)
    }

    private fun isFilePreview(n: String): Boolean {
        if (n.length > 160) return false
        return FILE_PATTERNS.any { it.containsMatchIn(n) }
    }

    private fun isForumOrLoginChrome(n: String): Boolean {
        if (n.length > 140) return false
        return FORUM_LOGIN_PATTERNS.any { it.containsMatchIn(n) }
    }

    private fun isProfessionalProfileText(n: String, context: SurfaceContext): Boolean {
        val looksProfessional = PROFESSIONAL_PATTERNS.any { it.containsMatchIn(n) }
        val hasProfileSeparators = n.count { it == '|' || it == '·' || it == '-' } >= 1
        return (isProfessionalPackage(context) && looksProfessional) ||
            (looksProfessional && hasProfileSeparators)
    }

    private fun isFoodCouponSurface(n: String, context: SurfaceContext): Boolean {
        val foodContext = isFoodOrShoppingPackage(context) || FOOD_CONTEXT.any { it.containsMatchIn(n) }
        if (!foodContext) return false
        return FOOD_COUPON_PATTERNS.any { it.containsMatchIn(n) }
    }

    private fun isUiMetadata(n: String): Boolean {
        if (n.length > 80) return false
        val parts = n.split('·', '|', '•').map { it.trim() }.filter { it.isNotEmpty() }
        return parts.isNotEmpty() && parts.all { part -> UI_METADATA_PATTERNS.any { it.matches(part) } }
    }

    private fun hasBettingRiskAnchor(n: String): Boolean =
        hasStrongBettingAnchor(n) || BETTING_CONTEXT_PATTERNS.any { it.containsMatchIn(n) }

    private fun hasStrongBettingAnchor(n: String): Boolean =
        STRONG_BETTING_PATTERNS.any { it.containsMatchIn(n) }

    private fun isWhatsappPackage(context: SurfaceContext): Boolean {
        val p = context.packageName ?: return false
        return p == "com.whatsapp" || p == "com.whatsapp.w4b"
    }

    private fun isMessagingPackage(context: SurfaceContext): Boolean {
        val p = context.packageName ?: return false
        return p == "com.whatsapp" ||
            p == "com.whatsapp.w4b" ||
            p == "org.telegram.messenger" ||
            p == "org.thunderdog.challegram"
    }

    private fun isProfessionalPackage(context: SurfaceContext): Boolean {
        val p = context.packageName ?: return false
        return p == "com.linkedin.android"
    }

    private fun isFoodOrShoppingPackage(context: SurfaceContext): Boolean {
        val p = context.packageName ?: return false
        return p.contains("yemeksepeti") ||
            p.contains("getir") ||
            p.contains("trendyol") ||
            p.contains("migros") ||
            p.contains("deliveryhero")
    }

    /** Türkçe aksan katlamalı normalize: kalıplar ascii yazılır, bypass zorlaşır. */
    private fun normalize(text: String): String =
        text.lowercase(TURKISH)
            .replace("̇", "")
            .replace('ı', 'i')
            .replace('ç', 'c')
            .replace('ğ', 'g')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ü', 'u')
            .replace(Regex("\\s+"), " ")
            .trim()

    private companion object {
        private val TURKISH = Locale.forLanguageTag("tr")
        private const val CACHE_VERSION = 2

        // Genel eşik = modelin nihai eşiği (esik_karari.json ile tek kaynak);
        // yüzey eşikleri taslağın göreli aralıklarıyla 0.70-0.90 penceresinde
        private val GENERAL_THRESHOLD = TfLiteDetector.VARSAYILAN_ESIK
        private const val MESSAGING_THRESHOLD = 0.80f
        private const val PROFESSIONAL_THRESHOLD = 0.86f
        private const val FOOD_THRESHOLD = 0.88f

        private val STRONG_BETTING_PATTERNS = listOf(
            Regex("\\b(bahis\\w*|iddaa\\w*|iddiaa\\w*|casino\\w*|kazino\\w*|kumar\\w*)\\b"),
            Regex("\\b(slot\\w*|rulet\\w*|jackpot\\w*|bonus\\w*|free\\s*spin|freespin|freebet)\\b"),
            Regex("\\b(canli\\s+bahis|deneme\\s+bonusu|cevrimsiz\\s+bonus)\\b"),
        )

        private val BETTING_CONTEXT_PATTERNS = listOf(
            Regex("\\b(banko\\w*|oran\\w*|kupon\\w*|yatirim\\w*|cekim\\w*)\\b.{0,32}\\b(vip\\w*|grup\\w*|kanal\\w*|dm|link\\w*|katil\\w*|gel\\w*|uye\\w*)\\b"),
            Regex("\\b(vip\\w*|grup\\w*|kanal\\w*|dm|link\\w*|katil\\w*|gel\\w*|uye\\w*)\\b.{0,32}\\b(banko\\w*|oran\\w*|kupon\\w*|yatirim\\w*|cekim\\w*)\\b"),
            Regex("\\b(mac\\w*|tekli\\w*|kombine\\w*)\\b.{0,32}\\b(oran\\w*|kupon\\w*|banko\\w*)\\b"),
        )

        private val WHATSAPP_SYSTEM_PATTERNS = listOf(
            Regex("^(~\\s*)?.{1,80}\\s+bir grup baglantisiyla katildi\\.?$"),
            Regex("^(~\\s*)?.{1,80}\\s+davet baglantisiyla katildi\\.?$"),
            Regex("^.{1,100}\\s+topluluguna dahil olan bir gruba davet yoluyla katildiniz\\.?$"),
            Regex("^bu gruba davet yoluyla katildiniz\\.?$"),
            Regex("^.{1,80}\\s+bu grubu bir topluluktan cikardi\\.?$"),
            Regex("^.{1,80}\\s+bu grubu olusturdu\\.?$"),
            Regex("^.{1,80}\\s+gruptan ayrildi\\.?$"),
            Regex("^.{1,80}\\s+grup simgesini degistirdi\\.?$"),
            Regex("^.{1,80}\\s+grup aciklamasini degistirdi\\.?$"),
            Regex("^mesajlar ve aramalar uctan uca sifrelidir\\.?$"),
            Regex("^guvenlik kodu degisti\\.?$"),
            Regex("^bu mesaj silindi\\.?$"),
        )

        private val CODE_PATTERNS = listOf(
            Regex("\\b(public|private|class|void|static|return|arraylist|integer|string|system\\.out\\.println)\\b"),
            Regex("\\b(val|var|fun|println|import|extends|implements)\\b"),
            Regex("[a-z0-9_]+\\s*=\\s*new\\s+[a-z0-9_]+"),
        )
        private val CODE_SYMBOLS = listOf("{", "}", ";", "()", "[]", "<", ">", "==", "->")

        private val FORM_LABEL_PATTERNS = listOf(
            Regex("^(cep telefonu|telefon|e-?posta|email|sifre|sifre hatirlatma|kullanici adi)\\s*:?$"),
            Regex("^(orcid|iban|tc kimlik|ad soyad|dogum tarihi|ogrenim|universite|fakulte|bolum)\\s*:?$"),
            Regex("^(dogrulama kodu|onay kodu|qr kod|basvuru formu|belge yukle)\\s*:?$"),
            Regex("^(cep telefonu|telefon|e-?posta|email|kullanici adi|iban)\\s*:\\s*.{1,80}$"),
            Regex("^sifre hatirlatma\\b.{0,80}\\bgonderildi$"),
        )

        private val FILE_PATTERNS = listOf(
            Regex("\\b(pdf|docx?|xlsx?|pptx?|jpg|jpeg|png)\\b"),
            Regex("\\b(dosya|belge|indir|onizleme|mb|kb)\\b"),
        )

        private val FORUM_LOGIN_PATTERNS = listOf(
            Regex("\\bsadece kayitli uyeler yorum yapabilir\\b"),
            Regex("\\bkayitli uyeler yorum yapabilir\\b"),
            Regex("^zaten uye misin\\??\\s*giris yap$"),
            Regex("\\buye misin\\??\\s*giris yap\\b"),
        )

        private val PROFESSIONAL_PATTERNS = listOf(
            Regex("\\b(computer engineering|software engineering|data science|cyber security|ai|bootcamp)\\b"),
            Regex("\\b(student|developer|engineer|intern|mezun|ogrenci|yazilim|siber guvenlik)\\b"),
            Regex("\\b(tebrikler|sertifika|egitim programi|kariyer|staj|baglanti|deneyim|yetenek)\\b"),
        )

        private val FOOD_CONTEXT = listOf(
            Regex("\\b(yemek|restoran|market|sepet|menu|siparis|kurye)\\b"),
        )

        private val FOOD_COUPON_PATTERNS = listOf(
            Regex("\\b(yemek kuponu|puan topla|odulunu kazan|sana ozel restoranlar)\\b"),
            Regex("\\b(kupon\\w*|puan\\w*|tl)\\b.{0,24}\\b(yemek|restoran|market|siparis)\\b"),
            Regex("\\b(yemek|restoran|market|siparis)\\b.{0,24}\\b(kupon\\w*|puan\\w*|tl)\\b"),
        )

        private val UI_METADATA_PATTERNS = listOf(
            Regex("[\\d.,+ ]+[bkm]?\\s*(begenme|begeni|yorum|goruntulenme|izlenme|paylasim|takipci|puan)\\S*"),
            Regex("\\d+\\s*(sn|dk|sa|saat|dakika|gun|hafta|ay|yil)\\s*once"),
            Regex("(paylas|kaydet|bildir|takip et|abone ol|begen|yanitla|yorum yap)"),
            Regex("\\d+([.,]\\d+)?[bkm]?"),
        )

        private val EMAIL_RE = Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")
        private val PHONE_RE = Regex("(\\+90|0)?\\s?5\\d{2}\\s?\\d{3}\\s?\\d{2}\\s?\\d{2}")
        private val IBAN_RE = Regex("\\btr\\d{2}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{0,6}\\b")
    }
}

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
            isIdentityLine(text, n, context) ||
            isLongTextWithoutBettingSignal(n) ||
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

    /**
     * Kimlik satırı mı? (telefon numarası, @kullanıcı adı, kişi/grup adı)
     *
     * 24 Eylül saha testi: bu metinler İÇERİK değil ETİKET; modele sorulunca
     * promosyon metni sanılıp örtülüyor (WhatsApp grup üyelerinin ad/numaraları,
     * X kullanıcı adları). Kapı BÜYÜK/küçük harf ayrımına baktığı için ham
     * metinle çalışır — normalize() her şeyi küçültür.
     *
     * Güvenlik: bahis çağrışımlı tek kelime bile varsa kimlik sayılmaz, karar
     * modele kalır (üstte zaten hasBettingRiskAnchor kapısı var; buradaki
     * SOFT_BETTING_WORDS onun daha geniş bir katmanı).
     */
    private fun isIdentityLine(text: String, n: String, context: SurfaceContext): Boolean {
        // WhatsApp rehberde olmayan kişiyi "~Ahmet" diye gösterir; grup
        // başlığındaki üye listesinde her ada ayrı ayrı eklenir.
        val raw = text.trim().trim('~', ' ')
        if (raw.isEmpty()) return false
        if (SOFT_BETTING_WORDS.any { it.containsMatchIn(n) }) return false
        // Bitişik yazım kaçağı: "@denemebonusu" kelime sınırı taşımadığı için
        // yukarıdaki kontrollerin hiçbirine takılmaz (24 Eylül cihaz ölçümü).
        // Tanıtım yapısı (link, para, yüzde) da kimlik satırında bulunmaz.
        if (hasBettingSignal(n) || hasPromoSignal(n)) return false

        // Telefon numarası: yeterince rakam + neredeyse tamamı rakam/numara
        // noktalaması. Eski PHONE_RE tam eşleşme aradığı için "+90 (555)…"
        // veya "Ahmet: 0555…" gibi biçimler kaçıyordu.
        if (raw.count { it.isDigit() } >= PHONE_MIN_DIGITS) {
            val phoneish = raw.count { it.isDigit() || it in PHONE_PUNCTUATION }
            if (phoneish >= raw.length * PHONE_CHAR_RATIO) return true
        }

        // Virgüllü ad listesi (WhatsApp grup başlığındaki üye satırı) tek bir
        // addan uzun olur; sınırlar ona göre gevşetilir.
        val parcalar = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val adListesi = parcalar.size >= 2
        if (raw.length > if (adListesi) IDENTITY_LIST_MAX_LEN else IDENTITY_MAX_LEN) return false

        var handles = 0
        var adKelimesi = 0
        var buyukHarfliAd = 0
        for (parca in parcalar) {
            var parcaAdSayisi = 0
            for (token in parca.split(WHITESPACE_RE)) {
                val t = token.trim(*TOKEN_TRIM_CHARS)
                when {
                    t.isEmpty() -> Unit // ayraç: "·", "•", "-", "~"
                    t.startsWith("@") && t.length > 1 -> handles++
                    t.all { it.isDigit() || it in PHONE_PUNCTUATION } -> Unit // numara parçası
                    META_TOKEN_RE.matches(t.lowercase(TURKISH)) -> Unit // "2s", "12dk", "1953"
                    t.none { it.isLetterOrDigit() } -> Unit // emoji / sembol
                    isNameWord(t) -> {
                        parcaAdSayisi++
                        adKelimesi++
                        if (t.first().isUpperCase()) buyukHarfliAd++
                    }
                    else -> return false // cümle içeriği: kimlik değil
                }
            }
            // Tek kişinin adı bu kadar uzun olmaz; daha uzunsa cümledir
            if (parcaAdSayisi > IDENTITY_PART_MAX_WORDS) return false
        }

        // @kullanıcı adı her yüzeyde kimliktir; çıplak ad-soyad yalnızca
        // sosyal/mesajlaşma yüzeylerinde (haber başlığı da ad-soyad gibi görünür)
        if (handles > 0) return true
        if (!isIdentityNamePackage(context)) return false
        val enFazlaAd = if (adListesi) IDENTITY_LIST_MAX_WORDS else IDENTITY_MAX_WORDS
        if (adKelimesi !in 1..enFazlaAd) return false
        // Ad listesinde kelimelerin çoğu büyük harfle başlar ("Halil hoca" gibi
        // karışık yazımlara yer bırakır); cümlede yalnızca ilk kelime başlar —
        // ayrımı bu oran yapar, kelime kelime büyük harf şartı değil.
        return buyukHarfliAd >= adKelimesi * NAME_UPPERCASE_RATIO
    }

    /**
     * Uzun metin ama içinde tek bir bahis sinyali bile yok → modele sorulmaz.
     *
     * 24 Eylül saha testi: model uzun metinlerde futbol haberlerini (transfer,
     * sakatlık, maç özeti) ve duyuru/kampanya dilini (kontenjan, başvuru formu,
     * son gün) teşvik sanıyor. Sebebi eğitim setindeki bahis-SEO pozitiflerinin
     * de aynı dille yazılmış olması (model/data/GERCEK_VERI_KAYNAKLARI.md,
     * "p-web pozitifleri" notu).
     *
     * Modelin kelime listesine kattığı asıl değer sansürlü/yeni yazımları
     * yakalamak; bu yüzden sinyal araması harf-rakam katlamasına ve noktalama
     * sökmeye dayanıklıdır ("b0nus", "b.o.n.u.s" da sinyal sayılır). Bu
     * uzunlukta bir teşvik metninin listedeki 30+ terimden hiçbirini
     * içermemesi beklenmez.
     *
     * DİKKAT: kısa metinlerde kural KAPALIDIR — model orada tek başına karar
     * vermeye devam eder.
     */
    private fun isLongTextWithoutBettingSignal(n: String): Boolean =
        n.length >= LONG_TEXT_CHARS && !hasBettingSignal(n) && !hasPromoSignal(n)

    /**
     * Metin tanıtım YAPISI taşıyor mu? Kelimeden bağımsız biçimsel işaretler:
     * bağlantı, para tutarı, yüzde, hediye/bedava vaadi, üyelik çağrısı.
     *
     * 24 Eylül: "MILANBAH*S" gibi yıldızlı sansür hiçbir kelime aramasına
     * takılmıyor ve uzun metin kapısı gerçek bir SMS teşvikini kaçırdı. Bu
     * yüzden kapı artık yalnız kelimeye değil, metnin biçimine de bakıyor —
     * spam kelimelerini gizleyebilir, ama linkini ve para vaadini gizleyemez.
     *
     * Haber ve duyuru metinleri bu işaretlerin hiçbirini taşımaz.
     */
    private fun hasPromoSignal(n: String): Boolean = PROMO_PATTERNS.any { it.containsMatchIn(n) }

    /**
     * Metinde bahse özgü bir terim geçiyor mu? İki yazım kaçağına dayanıklıdır:
     * harf yerine rakam ("b0nus") ve araya noktalama/boşluk sokma ("b.o.n.u.s",
     * "@denemebonusu"). İkinci arama kelime sınırı tanımadığı için yalnızca
     * uzun ve ayırt edici terimlerle yapılır.
     */
    private fun hasBettingSignal(n: String): Boolean {
        val leet = n.map { LEET_HARITASI[it] ?: it }.joinToString("")
        return BAHIS_SINYALI_RE.containsMatchIn(leet) ||
            BAHIS_SINYALI_SIKISIK_RE.containsMatchIn(leet.filter { it.isLetterOrDigit() })
    }

    /**
     * "Ahmet", "Yılmaz", "MEHMET", "O'Brien", "Kaya-Demir", "hoca" → ad parçası.
     * Büyük harf şartı burada DEĞİL, satır düzeyinde aranır: rehberdeki kayıtlar
     * "Halil hoca", "aylin" gibi karışık yazılıyor ve tek küçük harfli kelime
     * bütün üye listesini kimlik olmaktan çıkarıyordu (24 Eylül saha raporu).
     */
    private fun isNameWord(token: String): Boolean {
        if (token.length > NAME_WORD_MAX_LEN) return false
        return token.all { it.isLetter() || it in NAME_WORD_PUNCTUATION }
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

    /**
     * Ad-soyad kapısının açık olduğu yüzeyler: akış/sohbet listelerinde her
     * gönderinin yanında kişi adı vardır ve bunlar içerik değildir. Haber
     * uygulamaları ve tarayıcı bu listede YOKTUR — orada "Ahmet Yılmaz" bir
     * başlık parçası olabilir.
     */
    private fun isIdentityNamePackage(context: SurfaceContext): Boolean {
        if (isMessagingPackage(context)) return true
        val p = context.packageName ?: return false
        return p == "com.instagram.android" ||
            p == "com.twitter.android" ||
            p == "com.zhiliaoapp.musically" || // TikTok
            p == "com.facebook.katana" ||
            p == "com.snapchat.android" ||
            p == "com.google.android.contacts" ||
            p == "com.android.contacts" ||
            p == "com.google.android.apps.messaging" ||
            p == "com.android.mms"
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
        // Kapı mantığı değişince eski kararlar geçersiz olmalı: sürümü artır
        private const val CACHE_VERSION = 3

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
            Regex("^(~\\s*)?.{1,80}\\s+gruba eklendi\\.?$"),
            Regex("^(~\\s*)?.{1,80}\\s+gruptan cikarildi\\.?$"),
            Regex("^(~\\s*)?.{1,80}\\s+telefon numarasini degistirdi\\.?$"),
            Regex("^.{1,80}\\s+grup simgesini degistirdi\\.?$"),
            Regex("^.{1,80}\\s+grup aciklamasini degistirdi\\.?$"),
            Regex("^mesajlar ve aramalar uctan uca sifrelidir\\.?$"),
            Regex("^guvenlik kodu degisti\\.?$"),
            Regex("^bu mesaj silindi\\.?$"),
        )

        // DİKKAT: anahtar kelimeler tek başına aranmaz. "var" Türkçede çok
        // sık geçen bir kelime ("fırsat var") ve bu kapı açıldığında metin
        // modele hiç sorulmuyordu — teşvik içeriği kaçıyordu (24 Eylül).
        // Bu yüzden bildirim kalıbı aranır, salt kelime değil.
        private val CODE_PATTERNS = listOf(
            Regex("\\b(public|private|class|void|static|arraylist|integer|system\\.out\\.println)\\b"),
            Regex("\\b(val|var)\\s+[a-z_][a-z0-9_]*\\s*[:=]"),
            Regex("\\bfun\\s+[a-z_][a-z0-9_]*\\s*\\("),
            Regex("\\b(println|extends|implements)\\b"),
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

        // --- Uzun metin / bahis sinyali kapısı ---
        // Saha örneklerinin en kısası 112 karakterdi (transfer haberi); 90
        // pay bırakır. Altında model tek başına karar vermeye devam eder.
        private const val LONG_TEXT_CHARS = 90

        /** Sansürlü yazımları çözmek için: "b0nus" → "bonus", "ç3vr1m" → "cevrim". */
        private val LEET_HARITASI = mapOf(
            '0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a',
            '5' to 's', '7' to 't', '8' to 'b', '@' to 'a', '$' to 's',
        )

        /**
         * Yalnızca bahse özgü terimler — "katıl", "kazan", "üye", "link" gibi
         * meşru metinde de sık geçen kelimeler BİLEREK dışarıda bırakıldı
         * (ör. "katılım formu" bir duyuruyu teşvik saydırmamalı).
         */
        private const val BAHIS_SINYAL_KELIMELERI =
            "bahis|iddaa|casino|kazino|kumar|slot|rulet|jackpot|bonus|freespin|" +
                "free ?spin|freebet|betting|cevrim(?!ici|disi)|banko|kupon|oran|misli|tombala|" +
                "poker|blackjack|bakara|rulo|jeton|papara|bahisci|yatirim|cekim|" +
                "telegram|t\\.me|vip|kacak"

        /**
         * Tanıtım yapısı işaretleri. Haber/duyuru metinlerinde bulunmaz;
         * teşvik metninde en az biri neredeyse zorunludur (bir yere
         * yönlendirmek ve bir şey vadetmek durumundadır).
         */
        private val PROMO_PATTERNS = listOf(
            Regex("https?://|www\\.|\\bt\\.me/"),                       // bağlantı
            Regex("\\b[a-z0-9-]{2,}\\.(com|net|org|xyz|site|online|club|top|info|biz|link|bet|tv)\\b"),
            Regex("\\d[\\d.,]*\\s*(tl|try|₺|usd|\\$|euro|€)\\b"),       // para vaadi
            Regex("(%\\s*\\d|\\d\\s*%)"),                                // yüzde
            Regex("\\b(hediye|bedava|ucretsiz|promosyon|promo\\s*kod)\\b"),
            Regex("\\b(uye ol|kayit ol|hesap ac|giris yap|hemen tikla|tiklayin|linke tikla)\\b"),
        )

        private val BAHIS_SINYALI_RE = Regex("\\b($BAHIS_SINYAL_KELIMELERI)")

        /**
         * Noktalama sökülmüş ("b.o.n.u.s") hâl için kelime sınırı olmadan
         * aranır; bu yüzden yalnızca uzun ve ayırt edici terimler kullanılır —
         * "oran" boşluksuz metinde "soran"a takılırdı.
         */
        private val BAHIS_SINYALI_SIKISIK_RE = Regex(
            "(bahis|iddaa|casino|kazino|kumar|jackpot|bonus|freespin|freebet|" +
                "betting|cevrim(?!ici|disi)|banko|kupon|tombala|blackjack|bakara|papara|" +
                "bahisci|yatirim|telegram)",
        )

        // --- Kimlik satırı kapısı ---
        private const val IDENTITY_MAX_LEN = 48
        private const val IDENTITY_MAX_WORDS = 4
        // WhatsApp grup başlığındaki üye listesi ("Aylin, Ezgi, Halil Hadra, Sen").
        // Kalabalık gruplarda bu satır uzun olur; erişilebilirlik metni ekranda
        // kırpılmış görünse de tam listeyi taşır.
        private const val IDENTITY_LIST_MAX_LEN = 300
        private const val IDENTITY_LIST_MAX_WORDS = 40
        private const val IDENTITY_PART_MAX_WORDS = 4
        private const val NAME_UPPERCASE_RATIO = 0.5
        private const val NAME_WORD_MAX_LEN = 20
        private const val PHONE_MIN_DIGITS = 7
        private const val PHONE_CHAR_RATIO = 0.9
        private const val PHONE_PUNCTUATION = "+()-/. "
        private const val NAME_WORD_PUNCTUATION = "'’-."
        private val TOKEN_TRIM_CHARS =
            charArrayOf('·', '•', ',', '.', '|', '-', '(', ')', ':', '~', '…')
        private val WHITESPACE_RE = Regex("\\s+")
        private val META_TOKEN_RE = Regex("^\\d+[a-z]{0,6}$")

        /**
         * Kimlik kapısını iptal eden kelimeler: bunlardan biri geçiyorsa metin
         * "sadece bir isim" sayılmaz, karar modele bırakılır. Liste bilinçli
         * olarak geniş tutuldu — kapıyı fazla açmaktansa modele sormak yeğdir.
         * Kelime BAŞI eşleşir ("kuponu", "yatirim" girer; "Atlas" girmez).
         */
        private val SOFT_BETTING_WORDS = listOf(
            Regex(
                "\\b(bahis|iddaa|iddia|casino|kazino|kumar|slot|rulet|jackpot|bonus|" +
                    "spin|freebet|cevrim(?!ici|disi)|kupon|oran|banko|kombine|yatir|cekim|kazan|" +
                    "jeton|kanal|telegram|vip|katil|uyelik|cekilis|poker|tombala|" +
                    "misli|kacak)",
            ),
            Regex("\\b\\d+\\s*(tl|try|₺)\\b"),
        )

        private val EMAIL_RE = Regex("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")
        private val PHONE_RE = Regex("(\\+90|0)?\\s?5\\d{2}\\s?\\d{3}\\s?\\d{2}\\s?\\d{2}")
        private val IBAN_RE = Regex("\\btr\\d{2}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{4}\\s?\\d{0,6}\\b")
    }
}

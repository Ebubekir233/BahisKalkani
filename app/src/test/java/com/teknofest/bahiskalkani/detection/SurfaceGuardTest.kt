package com.teknofest.bahiskalkani.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceGuardTest {

    private val guard = SurfaceGuard()

    @Test
    fun `whatsapp sistem mesajini modelden muaf tutar`() {
        val context = SurfaceContext(packageName = "com.whatsapp")

        assertTrue(
            guard.shouldBypassModel("~ Ahmet bir grup bağlantısıyla katıldı.", context),
        )
        assertTrue(
            guard.shouldBypassModel(
                "Firat Universitesi topluluğuna dahil olan bir gruba davet yoluyla katıldınız",
                context,
            ),
        )
    }

    @Test
    fun `whatsapp riskli grup davetini muaf tutmaz`() {
        val context = SurfaceContext(packageName = "com.whatsapp")

        assertFalse(
            guard.shouldBypassModel("VIP bahis grubuna davet yoluyla katıldınız", context),
        )
    }

    @Test
    fun `form kelimesi gecen promosyonu muaf tutmaz`() {
        assertFalse(
            guard.shouldBypassModel(
                "500 TL bonus icin telefon numarani yaz",
                SurfaceContext(packageName = "com.android.chrome"),
            ),
        )
    }

    @Test
    fun `turkce ekli risk ifadelerini anchor olarak gorur`() {
        assertTrue(guard.hasBettingRiskAnchorForTest("Hoca dunku kuponla 5 kat aldik, kanala gel"))
        // Çapa varken yüzey eşiği değil genel eşik uygulanır
        assertEquals(
            TfLiteDetector.VARSAYILAN_ESIK,
            guard.thresholdFor(
                "Hoca dunku kuponla 5 kat aldik, kanala gel",
                SurfaceContext(packageName = "com.whatsapp"),
            ),
            0.001f,
        )
    }

    @Test
    fun `yemek kuponu yuzeyini yemek uygulamasinda muaf tutar`() {
        assertTrue(
            guard.shouldBypassModel(
                "200 puan topla, Yemek Kuponu (125 TL) odulunu kazan",
                SurfaceContext(packageName = "com.yemeksepeti.android"),
            ),
        )
    }

    @Test
    fun `kod ve profesyonel profil metinlerini muaf tutar`() {
        assertTrue(guard.shouldBypassModel("ArrayList<Medya> medya = new ArrayList<>();"))
        assertTrue(
            guard.shouldBypassModel(
                "Computer Engineering Student | AI - Cyber Security",
                SurfaceContext(packageName = "com.linkedin.android"),
            ),
        )
    }

    @Test
    fun `forum ve giris chrome metinlerini muaf tutar`() {
        assertTrue(
            guard.shouldBypassModel(
                "Sadece kayıtlı üyeler yorum yapabilir. Bir kaç saniye içerisinde kayıt olabilirsiniz.",
            ),
        )
        assertTrue(guard.shouldBypassModel("zaten üye misin? giriş yap"))
    }

    // --- Kimlik satırı kapısı (24 Eylül saha raporu) ---

    @Test
    fun `telefon numaralarini her yuzeyde muaf tutar`() {
        val whatsapp = SurfaceContext(packageName = "com.whatsapp")

        assertTrue(guard.shouldBypassModel("+90 555 123 45 67", whatsapp))
        assertTrue(guard.shouldBypassModel("0555 123 45 67", whatsapp))
        assertTrue(guard.shouldBypassModel("+90 (555) 123-45-67", whatsapp))
        assertTrue(guard.shouldBypassModel("+49 176 12345678", whatsapp))
        assertTrue(guard.shouldBypassModel("0212 444 0 000"))
    }

    @Test
    fun `kullanici adlarini muaf tutar`() {
        val x = SurfaceContext(packageName = "com.twitter.android")

        assertTrue(guard.shouldBypassModel("@ahmet_yilmaz_1907", x))
        assertTrue(guard.shouldBypassModel("Ahmet Yılmaz @ahmetyilmaz · 2s", x))
        assertTrue(guard.shouldBypassModel("@sporhaber", SurfaceContext()))
    }

    @Test
    fun `whatsapp kisi ve grup adlarini muaf tutar`() {
        val whatsapp = SurfaceContext(packageName = "com.whatsapp")

        assertTrue(guard.shouldBypassModel("~Mehmet Kaya", whatsapp))
        assertTrue(guard.shouldBypassModel("Ayşe Demir Yılmaz", whatsapp))
        assertTrue(guard.shouldBypassModel("MEHMET KAYA", whatsapp))
        assertTrue(guard.shouldBypassModel("Fatma O'Brien-Kaya", whatsapp))
    }

    @Test
    fun `whatsapp grup uye listesini muaf tutar`() {
        val whatsapp = SurfaceContext(packageName = "com.whatsapp")

        assertTrue(guard.shouldBypassModel("Aylin, Ezgi, Halil Hadra, Sadullah", whatsapp))
        assertTrue(
            guard.shouldBypassModel("Aylin, Ezgi, Halil Hadra, Sadullah, Mehmet Kaya, Sen", whatsapp),
        )
        // "~" rehberde olmayan kişi işareti: her ada ayrı ayrı gelir
        assertTrue(guard.shouldBypassModel("~Yusuf Akın, ~Alper Akden, Sen", whatsapp))
        assertTrue(guard.shouldBypassModel("~ ~Yusuf Akın~ ~", whatsapp))
        assertTrue(guard.shouldBypassModel("~ Alper Akden gruba eklendi.", whatsapp))
        // Rehberdeki gerçek yazımlar: karışık büyük/küçük harf, takma ad, ünvan
        assertTrue(guard.shouldBypassModel("Aylin, ezgi güleç, Halil hoca, Sen", whatsapp))
        assertTrue(guard.shouldBypassModel("Sadullah abi, Ahmet, ~Yusuf Akın, Sen", whatsapp))
        assertTrue(
            guard.shouldBypassModel(
                "Ahmet Yılmaz, Ayşe Nur Kaya, ~Mehmet, Fatma Demir, Ali Veli, " +
                    "Zeynep Şahin, Emre, Burak Can Öz, Sen",
                whatsapp,
            ),
        )
        // Karışık: kayıtsız numaralar ve adlar bir arada
        assertTrue(guard.shouldBypassModel("~Yusuf Akın, +90 532 123 45 67, Ezgi, Sen", whatsapp))
        // "çevrimiçi" bahis terimi değil, WhatsApp durum satırı
        assertTrue(guard.shouldBypassModel("Halil Hadra", whatsapp))
        assertFalse(guard.hasBettingRiskAnchorForTest("çevrimiçi"))

        // Liste kapısı da bahis çağrışımına kapalı
        assertFalse(guard.shouldBypassModel("Kupon Hocası, Banko Tüyo, Sen", whatsapp))
        assertFalse(guard.shouldBypassModel("Ahmet, Mehmet, deneme bonusu, Sen", whatsapp))
    }

    @Test
    fun `cevrimici bahis sinyali sayilmaz`() {
        val whatsapp = SurfaceContext(packageName = "com.whatsapp")

        // "çevrim" bahis terimidir ama "çevrimiçi"/"çevrimdışı" değildir —
        // bu kelime kimlik kapısını iptal ediyordu.
        assertTrue(guard.shouldBypassModel("Ahmet Yılmaz, çevrimiçi", whatsapp))
        // Gerçek bahis terimi hâlâ sinyal
        assertFalse(guard.shouldBypassModel("Ahmet Yılmaz, çevrimsiz bonus", whatsapp))
    }

    @Test
    fun `cıplak ad soyadi yalnizca sosyal yuzeylerde muaf tutar`() {
        // Haber başlığı da ad-soyad gibi görünür; tarayıcıda kapı kapalı olmalı
        assertFalse(guard.shouldBypassModel("Ahmet Yılmaz Demir", SurfaceContext()))
        assertTrue(
            guard.shouldBypassModel(
                "Ahmet Yılmaz Demir",
                SurfaceContext(packageName = "com.instagram.android"),
            ),
        )
    }

    @Test
    fun `kimlik kapisi bahis cagrisimli metni muaf tutmaz`() {
        val whatsapp = SurfaceContext(packageName = "com.whatsapp")

        assertFalse(guard.shouldBypassModel("Deneme Bonusu", whatsapp))
        assertFalse(guard.shouldBypassModel("Kanala Gel Kazan", whatsapp))
        assertFalse(guard.shouldBypassModel("VIP Kupon Grubu", whatsapp))
        assertFalse(guard.shouldBypassModel("0555 123 45 67 bonus", whatsapp))
        assertFalse(guard.shouldBypassModel("@bahiskanali", whatsapp))
        // Bitişik yazım: kelime sınırı yok, yine de kimlik sayılmamalı
        assertFalse(guard.shouldBypassModel("@denemebonusu", whatsapp))
        assertFalse(guard.shouldBypassModel("@kuponhocasi", whatsapp))
        assertFalse(guard.shouldBypassModel("~KuponHocası", whatsapp))
        assertFalse(guard.shouldBypassModel("@b0nusveren", whatsapp))
    }

    @Test
    fun `kimlik kapisi cumleleri muaf tutmaz`() {
        val whatsapp = SurfaceContext(packageName = "com.whatsapp")

        assertFalse(guard.shouldBypassModel("Ahmet bugün gelecek mi", whatsapp))
        assertFalse(guard.shouldBypassModel("Hemen katıl ve kazanmaya başla", whatsapp))
    }

    // --- Uzun metin / bahis sinyali kapısı (24 Eylül saha raporu) ---

    @Test
    fun `uzun futbol haberlerini modele sormaz`() {
        val x = SurfaceContext(packageName = "com.twitter.android")

        assertTrue(
            guard.shouldBypassModel(
                "Singo, milli aradan sonra da sahalara dönemeyecek. Kasımpaşa ve " +
                    "Barcelona maçlarını da kaçıracak. Singo'nun, Fenerbahçe derbisinden " +
                    "önceki Gençlerbirliği maçında sahalara dönmesi bekleniyor. Singo, " +
                    "bu sezon yalnızca 41 dakika forma giydi. (A Spor)",
                x,
            ),
        )
        assertTrue(
            guard.shouldBypassModel(
                "Galatasaray, İlkay Gündoğan ve Kaan Ayhan'ın sözleşmesini feshetme " +
                    "kararı aldı. Devre arasında yollar ayrılacak.",
                x,
            ),
        )
    }

    @Test
    fun `uzun kulup duyurusunu modele sormaz`() {
        assertTrue(
            guard.shouldBypassModel(
                "Değerli Fırat Üniversitesi TEKNOFEST Kulübü Üyeleri, Şanlıurfa'da " +
                    "gerçekleşecek TEKNOFEST coşkusuna ortak olmaya hazır mısınız? " +
                    "Fırat Üniversitesi'nin paylaşmış olduğu genel katılım formunun " +
                    "dışında, sadece TEKNOFEST Kulübü üyelerimize özel 25 kişilik " +
                    "sınırlı bir kontenjan oluşturmuş bulunmaktayız! Başvurular formun " +
                    "doldurulma sırasına göre değerlendirilecektir.",
                SurfaceContext(packageName = "com.whatsapp"),
            ),
        )
    }

    @Test
    fun `uzun metinde bahis sinyali varsa modele sorar`() {
        val whatsapp = SurfaceContext(packageName = "com.whatsapp")

        assertFalse(
            guard.shouldBypassModel(
                "Arkadaşlar bu akşamki maç için hazırladığım listeyi paylaşıyorum, " +
                    "geçen hafta da tutturmuştuk. Detaylar için kanalımıza göz atın, " +
                    "yeni gelenlere özel çevrimsiz fırsat var, kaçırmayın derim.",
                whatsapp,
            ),
        )
        // Sansürlü yazım da sinyal sayılır: kapı açılmamalı
        assertFalse(
            guard.shouldBypassModel(
                "Merhaba arkadaşlar size çok özel bir fırsattan bahsetmek istiyorum, " +
                    "yeni üyelere özel b0nus veriliyor ve şartsız şekilde hesabınıza " +
                    "hemen tanımlanıyor, son gün bugün kaçırmayın sakın.",
                whatsapp,
            ),
        )
        assertFalse(
            guard.shouldBypassModel(
                "Merhaba arkadaşlar size çok özel bir fırsattan bahsetmek istiyorum, " +
                    "yeni üyelere özel b.o.n.u.s veriliyor ve şartsız şekilde hesabınıza " +
                    "hemen tanımlanıyor, son gün bugün kaçırmayın sakın.",
                whatsapp,
            ),
        )
    }

    @Test
    fun `kod kapisi turkcede sik gecen kelimelere acilmaz`() {
        // "var", "sınıf", "dönüş" gibi kelimeler kod sanılıp metni modelden
        // kaçırmamalı — bu kapı açıldığında teşvik içeriği taranmadan geçiyordu.
        assertFalse(guard.shouldBypassModel("Yeni gelenlere özel fırsat var, kaçırmayın"))
        assertFalse(guard.shouldBypassModel("Bugün maç var mı"))
        assertTrue(guard.shouldBypassModel("val sonuc: Int = hesapla(5)"))
        assertTrue(guard.shouldBypassModel("fun hesapla(x: Int): Int { return x * 2 }"))
    }

    @Test
    fun `uzun metin kapisi tanitim yapisina acilmaz`() {
        // Yıldızlı sansür kelime aramasına takılmaz; kapı metnin BİÇİMİNE de
        // bakmalı (link + para + yüzde + hediye + üyelik çağrısı).
        assertFalse(
            guard.shouldBypassModel(
                "HEMEN UYE OL YENI UYELERE 5.000T*L HEDIYE AL ILK KAZANCINA OZEL " +
                    "2X %200 KATI ODEME MILANBAH*S GLOBAL TURKIYEDE ACILDI giris " +
                    "https://ornek-kisa.test/xxxxx B234",
                SurfaceContext(packageName = "com.google.android.apps.messaging"),
            ),
        )
        // Tek tek her tanıtım işareti de yeter
        assertFalse(
            guard.shouldBypassModel(
                "Arkadaşlar merhaba bugün sizlere çok güzel bir haberim var, detaylara " +
                    "buradan ulaşabilirsiniz: https://ornek-kisa.test/yyyyy hemen bakın derim",
            ),
        )
        assertFalse(
            guard.shouldBypassModel(
                "Arkadaşlar merhaba bugün sizlere çok güzel bir haberim var, yeni " +
                    "gelen herkese 5000 TL hediye veriliyormuş, son gün bugünmüş",
            ),
        )
    }

    @Test
    fun `kisa metinlerde model tek basina karar vermeye devam eder`() {
        assertFalse(guard.shouldBypassModel("Hemen katıl ve kazanmaya başla, son gün bugün"))
    }

    @Test
    fun `cache anahtari paket baglamina gore degisir`() {
        val text = "Kupon kodun hazir"

        assertNotEquals(
            guard.decisionCacheKey(text, SurfaceContext(packageName = "com.linkedin.android")),
            guard.decisionCacheKey(text, SurfaceContext(packageName = "com.yemeksepeti.android")),
        )
    }
}

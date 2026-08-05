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
            guard.shouldBypassModel(
                "~ Ahmet bir grup ba\u011flant\u0131s\u0131yla kat\u0131ld\u0131.",
                context,
            ),
        )
        assertTrue(
            guard.shouldBypassModel(
                "Firat Universitesi toplulu\u011funa dahil olan bir gruba davet yoluyla kat\u0131ld\u0131n\u0131z",
                context,
            ),
        )
    }

    @Test
    fun `whatsapp riskli grup davetini muaf tutmaz`() {
        val context = SurfaceContext(packageName = "com.whatsapp")

        assertFalse(
            guard.shouldBypassModel(
                "VIP bahis grubuna davet yoluyla kat\u0131ld\u0131n\u0131z",
                context,
            ),
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
        assertEquals(
            0.60f,
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
                "Sadece kayitli uyeler yorum yapabilir. Bir kac saniye icerisinde kayit olabilirsiniz.",
            ),
        )
        assertTrue(guard.shouldBypassModel("zaten uye misin? giris yap"))
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

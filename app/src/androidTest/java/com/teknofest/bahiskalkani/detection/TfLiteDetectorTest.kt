package com.teknofest.bahiskalkani.detection

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cihazda duman testi: model asset'lerden yükleniyor, bariz pozitif/negatif
 * örnekleri doğru ayırıyor ve gecikme bütçesinde kalıyor mu?
 * Kapsamlı doğruluk ölçümü model/scripts/degerlendir.py ile PC'de yapılır.
 */
@RunWith(AndroidJUnit4::class)
class TfLiteDetectorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val detector = TfLiteDetector.fromAssets(context)

    @Test
    fun bahisTesvikiniYakalar() {
        assertTrue(detector.isBettingContent("Deneme bonusu 500 TL, kaçırmayın! Katılmak için DM atın"))
        assertTrue(detector.isBettingContent("Hoca dünkü kuponla 5 kat aldık, kanala gel"))
    }

    @Test
    fun masumMetniIsaretlemez() {
        assertFalse(detector.isBettingContent("Bugün hava çok güzel, sahilde yürüyüş yaptık"))
        assertFalse(detector.isBettingContent("Yasa dışı bahis operasyonunda 12 gözaltı"))
        assertFalse(detector.isBettingContent("Betül ile alfabetik sıralama ödevini bitirdik"))
    }

    @Test
    fun gecikmeButcesindeKalir() {
        detector.score("ısınma turu metni")
        val start = System.nanoTime()
        repeat(10) { detector.score("canlı maç başladı, hep beraber izliyoruz arkadaşlar") }
        val perTextMs = (System.nanoTime() - start) / 10 / 1_000_000.0
        assertTrue("metin başına $perTextMs ms > 20 ms bütçe", perTextMs <= 20.0)
    }
}

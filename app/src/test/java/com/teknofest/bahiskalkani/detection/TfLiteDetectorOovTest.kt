package com.teknofest.bahiskalkani.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Katman 4 (sözlük dışı oran kapısı) birim testi. Interpreter gerektirmez:
 * kapı saf bir fonksiyondur, sözlüğü dışarıdan alır.
 */
class TfLiteDetectorOovTest {

    /** assets/model_vocab.json (surum 3) ile aynı kümeden temsilî bir kesit. */
    private val sozluk: Map<String, Int> = buildMap {
        var id = 2
        for (c in "abcçdefgğhıijklmnoöprsştuüvyzqwx0123456789 .,!?;:'\"/\\()[]{}<>-+@#%&*_🔗") {
            put(c.toString(), id++)
        }
    }

    @Test
    fun `duz turkce metni modele sorulmaya birakir`() {
        assertFalse(TfLiteDetector.sozlukDisiOraniYuksek("deneme bonusu veriyoruz", sozluk))
        assertFalse(TfLiteDetector.sozlukDisiOraniYuksek("çevrimsiz b0nus fırsatı", sozluk))
        assertFalse(TfLiteDetector.sozlukDisiOraniYuksek("ahmet yılmaz", sozluk))
    }

    @Test
    fun `instagram suslu yazi tipli kullanici adlarinda modeli devre disi birakir`() {
        assertTrue(TfLiteDetector.sozlukDisiOraniYuksek("𝓰𝓾𝓵 𝓼𝓮𝓿𝓰𝓲", sozluk))
        assertTrue(TfLiteDetector.sozlukDisiOraniYuksek("𝕮𝖆𝖓 𝕶𝖆𝖞𝖆", sozluk))
        assertTrue(TfLiteDetector.sozlukDisiOraniYuksek("ᵃʰᵐᵉᵗ", sozluk))
    }

    @Test
    fun `baska alfabeleri ve emoji dizilerini modele sormaz`() {
        assertTrue(TfLiteDetector.sozlukDisiOraniYuksek("привет как дела", sozluk))
        assertTrue(TfLiteDetector.sozlukDisiOraniYuksek("🎰🎲🃏💰🔥🎉", sozluk))
    }

    @Test
    fun `az sayida bilinmeyen karakter kapiyi acmaz`() {
        // Tek süslü harf ya da birkaç emoji, metnin geri kalanı okunur:
        // kapı açılmamalı, karar modele kalmalı.
        assertFalse(
            TfLiteDetector.sozlukDisiOraniYuksek("🎰 deneme bonusu hemen katıl", sozluk),
        )
    }

    @Test
    fun `bos metinde kapi acilmaz`() {
        assertFalse(TfLiteDetector.sozlukDisiOraniYuksek("   ", sozluk))
    }
}

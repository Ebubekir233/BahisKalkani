package com.teknofest.bahiskalkani.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordDetectorTest {

    // assets/keywords.json ile aynı mantık; testler eşleşme davranışını sınar
    private val detector = KeywordDetector(
        listOf("bahis", "iddaa", "casino", "çevrimsiz bonus"),
    )

    @Test
    fun `bahis kelimesini yakalar`() {
        assertTrue(detector.isBettingContent("Canlı bahis oranları burada"))
    }

    @Test
    fun `buyuk harf ve Turkce karakterlerle yakalar`() {
        assertTrue(detector.isBettingContent("BAHİS SİTESİ"))
        assertTrue(detector.isBettingContent("Çevrimsiz Bonus fırsatı"))
    }

    @Test
    fun `kelime turevlerini yakalar`() {
        assertTrue(detector.isBettingContent("1xbahis giriş"))
        assertTrue(detector.isBettingContent("casinolar listesi"))
    }

    @Test
    fun `masum metni isaretlemez`() {
        assertFalse(detector.isBettingContent("Bugün hava çok güzel"))
        assertFalse(detector.isBettingContent("Alfabetik sıralama"))
        assertFalse(detector.isBettingContent("Betül ile buluşma"))
    }

    @Test
    fun `bos metni isaretlemez`() {
        assertFalse(detector.isBettingContent(""))
    }

    @Test
    fun `buyuk harfli Ingilizce ifadeleri yakalar`() {
        // Türkçe küçük harf kuralı "FREE SPIN" -> "free spın" yapar;
        // harf katlaması olmadan liste ile eşleşmezdi
        val d = KeywordDetector(listOf("free spin", "freebet", "jackpot"))
        assertTrue(d.isBettingContent("FREE SPIN KAZANDIN"))
        assertTrue(d.isBettingContent("free spin kazandın"))
        assertTrue(d.isBettingContent("FREEBET ile başla"))
    }

    @Test
    fun `buyuk harfli Turkce ifadeleri yakalar`() {
        val d = KeywordDetector(listOf("canlı bahis", "deneme bonusu"))
        assertTrue(d.isBettingContent("CANLI BAHİS ORANLARI"))
        assertTrue(d.isBettingContent("DENEME BONUSU 500 TL"))
    }

    @Test
    fun `aksansiz kacamak yazimi yakalar`() {
        assertTrue(detector.isBettingContent("bahıs sitesi"))
    }

    @Test
    fun `sansurlu varyasyonlari yakalar`() {
        assertTrue(detector.isBettingContent("b4his siteleri"))
        assertTrue(detector.isBettingContent("ç3vrimsiz b0nus fırsatı"))
        assertTrue(detector.isBettingContent("c@sino oyunları"))
    }

    @Test
    fun `muaf ifadeleri isaretlemez`() {
        val d = KeywordDetector(
            keywords = listOf("bahis"),
            ignoredPhrases = listOf("bahiskalkanı", "bahis kalkanı"),
        )
        // Uygulamanın kendi adı launcher/ayarlar ekranlarında görünür;
        // kalkanı tetiklememeli
        assertFalse(d.isBettingContent("BahisKalkanı"))
        assertFalse(d.isBettingContent("BahisKalkanı ayarları"))
        // Muaf ifade geçse bile gerçek bahis içeriği yakalanmalı
        assertTrue(d.isBettingContent("BahisKalkanı canlı bahis sitesini engelledi"))
    }
}

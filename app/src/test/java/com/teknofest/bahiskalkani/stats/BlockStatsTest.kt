package com.teknofest.bahiskalkani.stats

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class BlockStatsTest {

    @Before
    fun sifirla() {
        BlockStats.blockedCount = 0
        BlockStats.perApp.clear()
    }

    @Test
    fun `engelleme hem toplam hem uygulama sayacina islenir`() {
        BlockStats.recordBlock("org.telegram.messenger")
        BlockStats.recordBlock("org.telegram.messenger")
        BlockStats.recordBlock("com.instagram.android")

        assertEquals(3, BlockStats.blockedCount)
        assertEquals(2, BlockStats.perApp["org.telegram.messenger"])
        assertEquals(1, BlockStats.perApp["com.instagram.android"])
    }

    @Test
    fun `paket adi bilinmiyorsa yalnizca toplam artar`() {
        BlockStats.recordBlock(null)

        assertEquals(1, BlockStats.blockedCount)
        assertEquals(0, BlockStats.perApp.size)
    }
}

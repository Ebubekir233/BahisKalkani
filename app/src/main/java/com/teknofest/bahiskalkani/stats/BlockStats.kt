package com.teknofest.bahiskalkani.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Uygulama içi sayaçlar. KVKK gereği yalnızca bellekte ve yalnızca SAYI
 * tutulur; hiçbir metin saklanmaz, kalıcı depoya yazılmaz. Servis yeniden
 * başlayınca sıfırlanır. Compose state olduğu için ana ekran canlı güncellenir.
 */
object BlockStats {
    /** Engellenen benzersiz içerik sayısı. */
    var blockedCount by mutableIntStateOf(0)

    /** Kullanıcının "yine de göster" / "tümünü göster" ile korumayı aştığı
     *  eylem sayısı (öğe sayısı değil, tıklama sayısı — bir "tümünü göster"
     *  tıklaması tek eylem sayılır). Eşiği aşınca ana ekranda YEDAM'a nazik
     *  bir yönlendirme gösterilir; bu davranışsal bir sinyaldir, yargılamaz. */
    var showAnywayCount by mutableIntStateOf(0)

    /** Bu eşikten itibaren YEDAM yönlendirme kartı gösterilir. */
    const val YEDAM_THRESHOLD = 3
}

package com.teknofest.bahiskalkani.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Engellenen içerik sayacı. KVKK gereği yalnızca bellekte ve yalnızca sayı
 * tutulur; metin saklanmaz, kalıcı depoya yazılmaz. Servis yeniden başlayınca
 * sıfırlanır. Compose state olduğu için ana ekran canlı güncellenir.
 */
object BlockStats {
    var blockedCount by mutableIntStateOf(0)
}

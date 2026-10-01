package com.teknofest.bahiskalkani.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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

    /**
     * Uygulama başına engellenen içerik sayısı (paket adı -> sayı). Kullanıcı
     * engellenenlerin çoğunun tek bir uygulamadan geldiğini görüp oraya ara
     * verebilsin diye. KVKK: içerik değil yalnızca hangi uygulamada kaç kez;
     * diğer sayaçlar gibi bellekte, servis yeniden başlayınca sıfırlanır.
     */
    val perApp = mutableStateMapOf<String, Int>()

    /** Engellenen yeni bir içeriği hem toplam hem uygulama sayacına işler. */
    fun recordBlock(packageName: String?) {
        blockedCount++
        if (packageName != null) perApp[packageName] = (perApp[packageName] ?: 0) + 1
    }

    /** Bu eşikten itibaren YEDAM yönlendirme kartı gösterilir. */
    const val YEDAM_THRESHOLD = 3
}

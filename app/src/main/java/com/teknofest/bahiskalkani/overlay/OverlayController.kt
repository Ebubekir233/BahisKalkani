package com.teknofest.bahiskalkani.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.teknofest.bahiskalkani.R

/** Kapatılacak tek bir içerik bölgesi. [key] aynı içeriği olaylar arasında eşlemeye yarar. */
data class CoverTarget(val key: String, val bounds: Rect, val textHash: Int)

/**
 * Tespit edilen içeriklerin üstüne kapak çizer.
 * TYPE_ACCESSIBILITY_OVERLAY: erişilebilirlik servisiyle birlikte gelir,
 * ayrıca SYSTEM_ALERT_WINDOW izni gerektirmez.
 *
 * Pencere yapısı:
 * - Kapak (scrim): içeriği örter; FLAG_NOT_TOUCHABLE olduğu için tüm
 *   dokunuşları alttaki uygulamaya geçirir — kaydırma çalışmaya devam eder.
 *   (Dokunulabilir tek pencere denendi ve ekranı kilitledi.)
 * - "Yine de göster" butonu: yalnızca buton sığacak kadar büyük kapaklara
 *   eklenen, kendi alanı kadar dokunulabilir ayrı pencere.
 * - Toplu çip: ekranda herhangi bir kapak varken altta görünen
 *   "N içerik engellendi · Yine de göster" penceresi — küçük kapakların
 *   kendi butonu olmadığı için ekran başına tek kaçış yolu burası.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val onShowAnyway: (CoverTarget) -> Unit,
    private val onShowAnywayAll: (List<CoverTarget>) -> Unit,
) {
    private class Cover(val scrim: View, val button: View?, var target: CoverTarget)

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val covers = mutableMapOf<String, Cover>()
    private var chip: TextView? = null

    /** Ekranı hedef listesiyle eşitler: yenileri ekler, kaybolanları kaldırır, taşınanları kaydırır. */
    fun update(targets: List<CoverTarget>) {
        val wantedKeys = targets.mapTo(mutableSetOf()) { it.key }
        covers.keys.filter { it !in wantedKeys }.forEach(::removeCover)
        var newCoverAdded = false
        for (target in targets) {
            val existing = covers[target.key]
            when {
                existing == null -> {
                    addCover(target)
                    newCoverAdded = true
                }
                existing.target.bounds != target.bounds -> moveCover(existing, target)
            }
        }
        updateChip()
        if (newCoverAdded) restackTouchables()
    }

    /**
     * Aynı pencere türünde üstte olan = son eklenen. Yeni bir kapak (scrim)
     * eklendiğinde daha önce eklenmiş butonların/çipin ÜSTÜNE binebiliyor;
     * buton kapağın altında kalınca görünmüyor ve dokunuş kapaktan geçip
     * alttaki uygulamaya gidiyor. Bu yüzden her yeni kapaktan sonra
     * dokunulabilir pencereler en üste yeniden dizilir.
     */
    private fun restackTouchables() {
        for (cover in covers.values) {
            cover.button?.let {
                windowManager.removeView(it)
                windowManager.addView(it, buttonParams(it, cover.target.bounds))
            }
        }
        chip?.let {
            windowManager.removeView(it)
            windowManager.addView(it, chipParams())
        }
    }

    fun clear() {
        covers.keys.toList().forEach(::removeCover)
        updateChip()
    }

    private fun addCover(target: CoverTarget) {
        val scrim = buildScrim(target.bounds)
        windowManager.addView(scrim, scrimParams(target.bounds))

        var button: View? = null
        if (target.bounds.height() >= MIN_HEIGHT_FOR_BUTTON_PX &&
            target.bounds.width() >= MIN_WIDTH_FOR_TEXT_PX
        ) {
            button = buildButton(target)
            windowManager.addView(button, buttonParams(button, target.bounds))
        }
        covers[target.key] = Cover(scrim, button, target)
    }

    private fun moveCover(cover: Cover, target: CoverTarget) {
        cover.target = target
        windowManager.updateViewLayout(cover.scrim, scrimParams(target.bounds))
        cover.button?.let { windowManager.updateViewLayout(it, buttonParams(it, target.bounds)) }
    }

    private fun removeCover(key: String) {
        covers.remove(key)?.let {
            windowManager.removeView(it.scrim)
            it.button?.let(windowManager::removeView)
        }
    }

    private fun updateChip() {
        val count = covers.size
        if (count == 0) {
            chip?.let(windowManager::removeView)
            chip = null
            return
        }
        val text = service.getString(R.string.overlay_chip_show_all, count)
        val existing = chip
        if (existing == null) {
            val view = buildChip()
            view.text = text
            windowManager.addView(view, chipParams())
            chip = view
        } else {
            existing.text = text
        }
    }

    private fun scrimParams(bounds: Rect) = WindowManager.LayoutParams(
        bounds.width(),
        bounds.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = bounds.left
        y = bounds.top
        // FLAG_BLUR_BEHIND KULLANMA: kapağın altını değil, pencerenin
        // arkasındaki TÜM ekranı bulanıklaştırıyor. Onlarca kapak açıkken
        // ekranın tamamı bulanıklaşıp GPU boğuluyor, telefon kilitlenmiş
        // gibi davranıyor. Kapak opak olduğu için blur'a gerek de yok.
    }

    private fun buttonParams(button: View, bounds: Rect): WindowManager.LayoutParams {
        button.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return WindowManager.LayoutParams(
            button.measuredWidth,
            button.measuredHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.centerX() - button.measuredWidth / 2
            y = bounds.centerY() + BUTTON_OFFSET_Y_PX
        }
    }

    private fun chipParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = CHIP_BOTTOM_MARGIN_PX
    }

    private fun buildScrim(bounds: Rect): View {
        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2263238"))
                cornerRadius = 24f
            }
        }
        if (bounds.height() >= MIN_HEIGHT_FOR_WARNING_PX &&
            bounds.width() >= MIN_WIDTH_FOR_TEXT_PX
        ) {
            // Uyarı ortanın biraz üstünde dursun; buton penceresi ortanın altına gelir
            container.setPadding(0, 0, 0, 2 * BUTTON_OFFSET_Y_PX)
            container.addView(TextView(service).apply {
                text = service.getString(R.string.overlay_warning)
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
            })
        }
        return container
    }

    private fun buildButton(target: CoverTarget): View {
        return TextView(service).apply {
            text = service.getString(R.string.overlay_show_anyway)
            setTextColor(Color.parseColor("#80CBC4"))
            textSize = 13f
            setPadding(40, 20, 40, 20)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#37474F"))
                cornerRadius = 32f
            }
            setOnClickListener {
                removeCover(target.key)
                onShowAnyway(target)
                updateChip()
            }
        }
    }

    private fun buildChip(): TextView {
        return TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(48, 28, 48, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#263238"))
                cornerRadius = 48f
            }
            setOnClickListener {
                onShowAnywayAll(covers.values.map { it.target })
                clear()
            }
        }
    }

    private companion object {
        /*
         * Kapak üç kademede çizilir — çünkü engellenen bölgeler ekranda çok
         * farklı boyutlarda çıkıyor (arama önerisi satırı ile video kartı
         * aynı değil) ve dar bir kapağa uyarı + buton sıkıştırmak okunmaz
         * bir yığın üretiyordu (13 Tem saha testi):
         *
         *   dar/alçak            → yalnız karartma
         *   ≥140 yükseklik+geniş → karartma + uyarı yazısı
         *   ≥220 yükseklik+geniş → karartma + uyarı + kendi "yine de göster"i
         *
         * Alttaki toplu çip HER durumda vardır: küçük kapakların kendi
         * butonu olmadığı için tek kaçış yolu odur. Büyük kapaklarda ikisi
         * birden görünür — kapak butonu yalnız o içeriği, çip ekrandaki
         * tümünü açar.
         */
        const val MIN_HEIGHT_FOR_WARNING_PX = 140
        const val MIN_HEIGHT_FOR_BUTTON_PX = 220
        const val MIN_WIDTH_FOR_TEXT_PX = 320
        const val BUTTON_OFFSET_Y_PX = 20
        const val CHIP_BOTTOM_MARGIN_PX = 160
    }
}

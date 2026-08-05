package com.teknofest.bahiskalkani.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.teknofest.bahiskalkani.BuildConfig
import com.teknofest.bahiskalkani.detection.KeywordDetector
import com.teknofest.bahiskalkani.detection.SurfaceContext
import com.teknofest.bahiskalkani.detection.SurfaceGuard
import com.teknofest.bahiskalkani.detection.TfLiteDetector
import com.teknofest.bahiskalkani.overlay.CoverTarget
import com.teknofest.bahiskalkani.overlay.OverlayController
import com.teknofest.bahiskalkani.stats.BlockStats

class ScreenReaderService : AccessibilityService() {

    // Faz 1 kelime listesi + Faz 2 TFLite modeli birlikte çalışır:
    // ikisinden biri "evet" derse içerik engellenir (liste güvenlik ağı).
    // SurfaceGuard yüzey kapısıdır: sistem/UI metinlerini modelden uzak
    // tutar ve yüzeye göre eşik seçer (WhatsApp sıkı, tarayıcı normal).
    private lateinit var keyword: KeywordDetector
    private lateinit var model: TfLiteDetector
    private val guard = SurfaceGuard()
    private var currentPackage: String? = null
    private lateinit var overlay: OverlayController

    // "Yine de göster" denilen içerikler; KVKK gereği yalnızca bellekte
    // hash olarak tutulur, servis yeniden başlayınca sıfırlanır.
    private val allowedHashes = mutableSetOf<Int>()

    // Sayaç her benzersiz içeriği bir kez saysın diye
    private val countedHashes = mutableSetOf<Int>()

    private val handler = Handler(Looper.getMainLooper())
    private val scanRunnable = Runnable { runScan() }

    // Hiç taranmayacak paketler; launcher onServiceConnected'da eklenir
    private val skippedPackages = mutableSetOf(
        "com.android.settings",
        "com.android.systemui",
    )

    // Metin başına tespit kararı önbelleği (LRU): kaydırma sırasında aynı
    // metinler saniyede defalarca yeniden taranır; model her seferinde
    // çalışırsa kapaklar içeriğin gerisinde kalır. Yalnızca hash + karar
    // tutulur, metin saklanmaz (KVKK).
    private val decisionCache = object : LinkedHashMap<Int, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Boolean>) =
            size > CACHE_LIMIT
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Ana ekranı (launcher) tarama: widget/uygulama etiketlerinde bahis
        // reklamı olmaz ama model, hava durumu gibi rakam yoğun kısa widget
        // metinlerinde yanlış alarm verebiliyor
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName?.let { skippedPackages.add(it) }

        keyword = KeywordDetector.fromAssets(this)
        model = TfLiteDetector.fromAssets(this)
        overlay = OverlayController(
            this,
            onShowAnyway = { target -> allowedHashes.add(target.textHash) },
            onShowAnywayAll = { targets -> targets.forEach { allowedHashes.add(it.textHash) } },
        )
        Log.i(TAG, "Erişilebilirlik servisi bağlandı")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            return
        }
        // Kendi overlay pencerelerimizin ürettiği olaylarla uğraşma
        if (event.packageName == packageName) return

        // Olay fırtınası sönümleme: sayfa geçişlerinde onlarca olay art arda
        // gelir; her biri için tam ağaç taraması yapmak tepki süresini
        // dalgalandırıyor. Fırtına durulunca TEK tarama yapılır.
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, SCAN_DEBOUNCE_MS)
    }

    private fun runScan() {
        val root = rootInActiveWindow
        if (root == null) {
            overlay.clear()
            return
        }
        // Olayın kaynağı değil, TARANAN pencere belirleyici: aktif pencere
        // kendi uygulamamız, sistem yüzeyi (ayarlar, bildirim çubuğu) ya da
        // ana ekransa hiç tarama — kullanıcının servisi kapatabileceği kaçış
        // yolu her zaman açık kalmalı.
        val rootPackage = root.packageName?.toString()
        if (rootPackage == packageName || rootPackage in skippedPackages) {
            overlay.clear()
            return
        }
        currentPackage = rootPackage
        val targets = mutableListOf<CoverTarget>()
        scan(root, targets)
        overlay.update(targets)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(scanRunnable)
        if (::overlay.isInitialized) overlay.clear()
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanRunnable)
        if (::overlay.isInitialized) overlay.clear()
        super.onDestroy()
    }

    /**
     * Düğüm ağacını gezer; metni tespitçiye takılan düğümlerin ekran
     * koordinatlarını toplar. KVKK: metinler yalnızca bellekte işlenir,
     * kaydedilmez, cihaz dışına çıkmaz.
     */
    private fun scan(node: AccessibilityNodeInfo, out: MutableList<CoverTarget>) {
        // Kullanıcının kendi yazı alanlarını (arama kutusu vb.) kapatma:
        // kendi yazdığı metin "bahse teşvik eden içerik" değildir ve kutu
        // kapanırsa yazdığını göremez
        if (node.isEditable) return

        // Yalnızca ekranda GÖRÜNEN metin taranır. contentDescription bilinçli
        // olarak dışarıda: ikon butonları (arama önerisi ok'u gibi) arama
        // metnini contentDescription'da taşıyor ve ekranda görünmeyen "içerik"
        // yüzünden ikonlar kapatılıyordu. Görselleri alt metinden yakalamak
        // görsel tespitle birlikte gelecek çalışma kapsamında.
        val text = node.text?.toString() ?: ""
        if (text.isNotBlank()) {
            val hash = text.hashCode()
            val ctx = SurfaceContext(
                packageName = currentPackage,
                nodeClassName = node.className?.toString(),
            )
            // Önbellek anahtarı metin + yüzey ikilisi: aynı metin farklı
            // uygulamada farklı eşiğe tabi olabilir
            val isBetting = decisionCache.getOrPut(guard.decisionCacheKey(text, ctx)) {
                keyword.isBettingContent(text) ||
                    (text.length >= MODEL_MIN_CHARS &&
                        !guard.shouldBypassModel(text, ctx) &&
                        model.isBettingContent(text, guard.thresholdFor(text, ctx)))
            }
            if (!isBetting) {
                scanChildren(node, out)
                return
            }
            if (hash !in allowedHashes) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    // Aynı metin ekranda birden çok yerde olabilir; anahtara sıra ekle
                    val key = "$hash:${out.count { it.textHash == hash }}"
                    out.add(CoverTarget(key, bounds, hash))
                    if (countedHashes.add(hash)) {
                        BlockStats.blockedCount++
                        if (BuildConfig.DEBUG) Log.d(TAG, "TESPİT: $text")
                    }
                }
            }
            // Eşleşen düğümün altına inmeye gerek yok, tamamı kapanacak
            return
        }
        scanChildren(node, out)
    }

    private fun scanChildren(node: AccessibilityNodeInfo, out: MutableList<CoverTarget>) {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scan(child, out)
        }
    }

    private companion object {
        const val TAG = "BahisKalkani"

        // Model bundan kısa metinlere sorulmaz: bağlam yetersiz, yanlış
        // alarm üretiyor ("Kanal23 · 4g" gibi kaynak etiketleri)
        const val MODEL_MIN_CHARS = 15

        // Karar önbelleği üst sınırı (hash + boolean; metin tutulmaz)
        const val CACHE_LIMIT = 500

        // Olay fırtınası sönümleme süresi: son olaydan bu kadar ms sonra
        // tek tarama yapılır (100 ms'lik olay aralığının altında kalmalı)
        const val SCAN_DEBOUNCE_MS = 60L
    }
}

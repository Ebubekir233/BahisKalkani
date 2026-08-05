package com.teknofest.bahiskalkani.detection

/**
 * Ekran metnini değerlendiren tek değiştirilebilir nokta.
 * Faz 1: [KeywordDetector] (kelime listesi).
 * Faz 2: ML tabanlı implementasyon bu arayüzün arkasına takılacak;
 * servis ve overlay kodu değişmeyecek.
 */
fun interface Detector {
    fun isBettingContent(text: String): Boolean
}

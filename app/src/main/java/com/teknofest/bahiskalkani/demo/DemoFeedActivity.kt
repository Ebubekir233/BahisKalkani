package com.teknofest.bahiskalkani.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teknofest.bahiskalkani.ui.theme.BahisKalkaniTheme
import org.json.JSONArray

/**
 * Jüri demosu için yerleşik sosyal medya akışı (SosyalApp'in birebir kopyası).
 * Tek APK ile demo yapılabilsin diye kalkanın içinde durur.
 *
 * Kalkan normalde kendi uygulamasının ekranlarını taramaz (ayarlar ekranında
 * kullanıcı kilitlenmesin diye). Bu ekran açıkken [DemoState.gorunur] true
 * olur ve ScreenReaderService yalnızca bu durumda kendi paketini tarar.
 */
object DemoState {
    @Volatile
    var gorunur: Boolean = false

    /** Servis bağlanınca atanır; demo kapanırken kalan kapakları temizler. */
    var demoKapandi: (() -> Unit)? = null
}

data class DemoPost(
    val kullaniciAdi: String,
    val metin: String,
    val begeniSayisi: Int,
    val renk: Color,
    val gorselAdi: String? = null,
)

class DemoFeedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BahisKalkaniTheme {
                DemoFeedEkrani()
            }
        }
        // Compose ekran değişikliklerini erişilebilirlik servislerine en fazla
        // 100 ms'de bir bildirir; kalkanın kapakları kaydırmada bu yüzden
        // geriden gelir. Demo akışı (sosyal medya uygulamasını temsil eden
        // ekran) bu aralığı ~2 kareye indirir. Yalnız bu ekranı etkiler;
        // Compose sürümü değişip alan bulunamazsa sessizce varsayılanda kalır.
        window.decorView.post { hizliErisilebilirlikBildirimi() }
    }

    private fun hizliErisilebilirlikBildirimi() {
        try {
            val composeView = findComposeView(window.decorView) ?: return
            var cls: Class<*>? = composeView.javaClass
            while (cls != null) {
                val alan = cls.declaredFields.firstOrNull {
                    it.type.name.endsWith("AndroidComposeViewAccessibilityDelegateCompat")
                }
                if (alan != null) {
                    alan.isAccessible = true
                    val delegate = alan.get(composeView) ?: return
                    delegate.javaClass.methods
                        .firstOrNull { it.name.startsWith("setSendRecurringAccessibilityEventsIntervalMillis") }
                        ?.invoke(delegate, BILDIRIM_ARALIGI_MS)
                    return
                }
                cls = cls.superclass
            }
        } catch (e: Throwable) {
            // Varsayılan 100 ms ile devam
        }
    }

    private fun findComposeView(view: android.view.View): android.view.View? {
        if (view.javaClass.name.endsWith("AndroidComposeView")) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findComposeView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val BILDIRIM_ARALIGI_MS = 32L
    }

    override fun onResume() {
        super.onResume()
        DemoState.gorunur = true
    }

    override fun onPause() {
        DemoState.gorunur = false
        DemoState.demoKapandi?.invoke()
        super.onPause()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoFeedEkrani() {
    val context = LocalContext.current
    val gonderiler = remember {
        val json = JSONArray(
            context.assets.open("demo_posts.json").bufferedReader().use { it.readText() },
        )
        List(json.length()) { i ->
            val o = json.getJSONObject(i)
            DemoPost(
                kullaniciAdi = o.getString("kullaniciAdi"),
                metin = o.getString("metin"),
                begeniSayisi = o.getInt("begeniSayisi"),
                renk = Color(android.graphics.Color.parseColor(o.getString("renk"))),
                gorselAdi = if (o.has("gorselAdi") && !o.isNull("gorselAdi")) {
                    o.getString("gorselAdi").takeIf { it.isNotEmpty() }
                } else {
                    null
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("SosyalApp", fontWeight = FontWeight.Bold, fontSize = 20.sp) })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(gonderiler) { post ->
                DemoGonderiKarti(post)
                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun DemoGonderiKarti(post: DemoPost) {
    val context = LocalContext.current
    val gorselId = remember(post.gorselAdi) {
        post.gorselAdi?.let {
            context.resources.getIdentifier(it, "drawable", context.packageName)
        } ?: 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // Kart erişilebilirlik ağacında metni gönderi metni olan TEK düğüm:
            // kalkan profil + görsel + metni tek blok olarak kapatır
            .clearAndSetSemantics { text = AnnotatedString(post.metin) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(post.renk, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = post.kullaniciAdi.first().uppercaseChar().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = post.kullaniciAdi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (gorselId != 0) {
            Image(
                painter = painterResource(id = gorselId),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(text = post.metin, fontSize = 14.sp, lineHeight = 20.sp)

        Spacer(modifier = Modifier.height(10.dp))

        Row {
            Text(text = "❤️ ${post.begeniSayisi}", fontSize = 13.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "💬 Yorum yaz", fontSize = 13.sp, color = Color.Gray)
        }
    }
}

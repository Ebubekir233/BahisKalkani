package com.example.bahissandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.bahissandbox.ui.theme.BahisSandboxTheme
import org.json.JSONArray

data class Post(
    val kullaniciAdi: String,
    val metin: String,
    val begeniSayisi: Int,
    val renk: Color,
    val gorselAdi: String? = null
)

fun parseColor(hex: String): Color {
    return Color(android.graphics.Color.parseColor(hex))
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            BahisSandboxTheme {
                FeedEkrani()
            }
        }
        // Compose, ekran degisikliklerini erisilebilirlik servislerine en fazla
        // 100 ms'de bir bildirir; kalkanin kapaklari kaydirmada bu yuzden
        // geriden geliyordu. Bu ekranda aralik ~2 kareye indirilir. Compose
        // surumu degisip alan bulunamazsa sessizce varsayilanda kalir.
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
            // Varsayilan 100 ms ile devam
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedEkrani() {
    val context = LocalContext.current
    val gonderiler = remember {
        val jsonString = context.assets.open("posts.json")
            .bufferedReader()
            .use { it.readText() }
        val jsonArray = JSONArray(jsonString)
        val liste = mutableListOf<Post>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            liste.add(
                Post(
                    kullaniciAdi = obj.getString("kullaniciAdi"),
                    metin = obj.getString("metin"),
                    begeniSayisi = obj.getInt("begeniSayisi"),
                    renk = parseColor(obj.getString("renk")),
                    gorselAdi = if (obj.has("gorselAdi") && !obj.isNull("gorselAdi")) {
                        obj.getString("gorselAdi").takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }
                )
            )
        }
        liste
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SosyalApp",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(gonderiler) { post ->
                GonderiKarti(post = post)
                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun GonderiKarti(post: Post) {
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
            // Kart, erisilebilirlik agacinda metni gonderi metni olan TEK dugum:
            // kalkan profil + gorsel + metni tek blok halinde kapatabilsin diye.
            .clearAndSetSemantics { text = AnnotatedString(post.metin) }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(post.renk, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = post.kullaniciAdi.first().uppercaseChar().toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = post.kullaniciAdi,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
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
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(
            text = post.metin,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row {
            Text(text = "❤️ ${post.begeniSayisi}", fontSize = 13.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "💬 Yorum yaz", fontSize = 13.sp, color = Color.Gray)
        }
    }
}

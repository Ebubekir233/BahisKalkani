package com.example.bahissandbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
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
    val renk: Color
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
                    renk = parseColor(obj.getString("renk"))
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .semantics(mergeDescendants = true) {}
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
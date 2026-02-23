package com.example.shadowingplayerw

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.shadowingplayerw.ui.theme.ShadowingPlayerWTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadowingPlayerWTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // الجزء الأصلي مع تمرير الإضافات الجديدة
                    ShadowingScreen(
                        name = "وليد",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ShadowingScreen(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // إعداد المشغل
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            // ملاحظة: تأكد من وجود فيديو باسم video_source في res/raw
            val videoUri = Uri.parse("android.resource://${context.packageName}/raw/video_source")
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
        }
    }

    // إدارة حالة الفواصل
    var currentSegment by remember { mutableStateOf<VideoSegment?>(null) }
    val segments = remember {
        mutableStateListOf(
            VideoSegment(1, "الترحيب", 0, 5000),
            VideoSegment(2, "الجملة الأولى", 5000, 10000),
            VideoSegment(3, "الجملة الثانية", 10000, 15000)
        )
    }

    // منطق التكرار (Looping) للفاصل المحدد
    LaunchedEffect(currentSegment) {
        while (currentSegment != null) {
            if (exoPlayer.currentPosition >= currentSegment!!.endTimeMs) {
                exoPlayer.seekTo(currentSegment!!.startTimeMs)
            }
            delay(100)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // نداء الدالة الأصلية للحفاظ على الهيكل
        Greeting(name = name)

        Spacer(modifier = Modifier.height(8.dp))

        // عرض الفيديو
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )

        Text(
            text = "الفواصل الزمنية (تكرار المقطع):",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        // قائمة الفواصل - تم استخدام الـ weight المدمج مباشرة هنا لحل المشكلة
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(segments) { segment ->
                Button(
                    onClick = {
                        currentSegment = segment
                        exoPlayer.seekTo(segment.startTimeMs)
                        exoPlayer.play()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = if (currentSegment == segment)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    else ButtonDefaults.buttonColors()
                ) {
                    Text("${segment.name} (${segment.startTimeMs/1000}s - ${segment.endTimeMs/1000}s)")
                }
            }
        }

        // زر إيقاف التكرار
        Button(
            onClick = { currentSegment = null },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(16.dp)
        ) {
            Text("إيقاف التكرار والتشغيل العادي")
        }
    }

    // تنظيف الذاكرة عند إغلاق التطبيق
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "مرحبا $name!",
        modifier = modifier.padding(16.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ShadowingPlayerWTheme {
        Greeting("وليد")
    }
}


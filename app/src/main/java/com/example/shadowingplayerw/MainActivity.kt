package com.example.shadowingplayerw

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.media3.common.Player
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
                    // الجزء الأصلي
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

    // حالة لتخزين مسار الفيديو المختار من الهاتف
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var videoDuration by remember { mutableLongStateOf(0L) }

    // إعداد المشغل
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        videoDuration = duration
                    }
                }
            })
        }
    }

    // تحديث الفيديو في المشغل عند اختيار ملف جديد
    LaunchedEffect(selectedVideoUri) {
        selectedVideoUri?.let { uri ->
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
        }
    }

    // لاونشر لفتح مستعرض الملفات واختيار فيديو
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedVideoUri = uri
    }

    // إدارة حالة الفواصل
    var currentSegment by remember { mutableStateOf<VideoSegment?>(null) }
    val segments = remember { mutableStateListOf<VideoSegment>() }

    // حالة المؤشر (Range Slider) لتحديد المقطع
    var sliderPosition by remember { mutableStateOf(0f..1000f) }

    // منطق التكرار (Looping)
    LaunchedEffect(currentSegment) {
        while (currentSegment != null) {
            if (exoPlayer.currentPosition >= currentSegment!!.endTimeMs) {
                exoPlayer.seekTo(currentSegment!!.startTimeMs)
            }
            delay(100)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // نداء الدالة الأصلية
        Greeting(name = name)

        // زر اختيار الفيديو من الهاتف
        Button(
            onClick = { launcher.launch("video/*") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Text(if (selectedVideoUri == null) "اختر فيديو من الهاتف" else "تغيير الفيديو")
        }

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

        // مؤشر تحديد المقطع (Range Slider)
        if (selectedVideoUri != null && videoDuration > 0) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "تحديد نطاق المقطع (ثواني):", style = MaterialTheme.typography.labelMedium)
                RangeSlider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    valueRange = 0f..videoDuration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${(sliderPosition.start / 1000).toInt()}s")
                    Text("${(sliderPosition.endInclusive / 1000).toInt()}s")
                }
                Button(
                    onClick = {
                        val newSegment = VideoSegment(
                            id = segments.size + 1,
                            name = "مقطع ${segments.size + 1}",
                            startTimeMs = sliderPosition.start.toLong(),
                            endTimeMs = sliderPosition.endInclusive.toLong()
                        )
                        segments.add(newSegment)
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("إضافة مقطع")
                }
            }
        }

        Text(
            text = "الفواصل الزمنية المحددة:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // قائمة الفواصل
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(segments) { segment ->
                Button(
                    onClick = {
                        if (selectedVideoUri != null) {
                            currentSegment = segment
                            exoPlayer.seekTo(segment.startTimeMs)
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    enabled = selectedVideoUri != null,
                    colors = if (currentSegment == segment)
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    else ButtonDefaults.buttonColors()
                ) {
                    Text("${segment.name} (${segment.startTimeMs/1000}s - ${segment.endTimeMs/1000}s)")
                }
            }
        }

        // إيقاف التكرار
        Button(
            onClick = { currentSegment = null },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
        ) {
            Text("إيقاف التكرار")
        }
    }

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

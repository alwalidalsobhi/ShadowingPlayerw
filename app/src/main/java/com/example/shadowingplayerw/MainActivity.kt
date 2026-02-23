package com.example.shadowingplayerw

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadowingPlayerWTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // الهيكل الأصلي
                    ShadowingScreen(
                        name = "وليد",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// دالة مساعدة لتنسيق الوقت (00:00)
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@OptIn(UnstableApi::class)
@Composable
fun ShadowingScreen(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var videoDuration by remember { mutableLongStateOf(0L) }
    var currentPlaybackPosition by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) videoDuration = duration
                }
            })
        }
    }

    // تحديث موضع التشغيل الحالي لمزامنة السلايدر
    LaunchedEffect(exoPlayer.isPlaying) {
        while (true) {
            currentPlaybackPosition = exoPlayer.currentPosition
            delay(100)
        }
    }

    LaunchedEffect(selectedVideoUri) {
        selectedVideoUri?.let { uri ->
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedVideoUri = uri }

    val segments = remember { mutableStateListOf<VideoSegment>() }
    var currentSegment by remember { mutableStateOf<VideoSegment?>(null) }
    var sliderPosition by remember { mutableStateOf(0f..1000f) }

    var showNamingDialog by remember { mutableStateOf(false) }
    var tempSegmentName by remember { mutableStateOf("") }
    var segmentToEdit by remember { mutableStateOf<VideoSegment?>(null) }

    // منطق التكرار
    LaunchedEffect(currentSegment) {
        while (currentSegment != null) {
            if (exoPlayer.currentPosition >= currentSegment!!.endTimeMs) {
                exoPlayer.seekTo(currentSegment!!.startTimeMs)
            }
            delay(100)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Greeting(name = name)

        Button(
            onClick = { launcher.launch("video/*") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Text(if (selectedVideoUri == null) "اختر فيديو من الهاتف" else "تغيير الفيديو")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // منطقة الفيديو مع عرض الوقت فوقها
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            AndroidView(
                factory = { PlayerView(it).apply { player = exoPlayer; useController = true } },
                modifier = Modifier.fillMaxSize()
            )

            // عرض الوقت الحالي فوق الفيديو
            if (selectedVideoUri != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Text(
                        text = formatTime(currentPlaybackPosition),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // مؤشر التحديد المتزامن مع تحريك الفيديو
        if (selectedVideoUri != null && videoDuration > 0) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "حدد بداية ونهاية المقطع:", style = MaterialTheme.typography.labelMedium)

                Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    RangeSlider(
                        value = sliderPosition,
                        onValueChange = { newRange ->
                            // إذا تحرك المؤشر الأول (البداية)، حرك الفيديو معه
                            if (newRange.start != sliderPosition.start) {
                                exoPlayer.seekTo(newRange.start.toLong())
                            }
                            sliderPosition = newRange
                        },
                        valueRange = 0f..videoDuration.toFloat(),
                        modifier = Modifier.fillMaxSize()
                    )

                    // خط أحمر يمثل مكان التشغيل الحالي
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                        val progress = currentPlaybackPosition.toFloat() / videoDuration.toFloat()
                        val xPos = size.width * progress
                        drawLine(
                            color = Color.Red,
                            start = Offset(xPos, 0f),
                            end = Offset(xPos, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("البداية: ${formatTime(sliderPosition.start.toLong())}", color = MaterialTheme.colorScheme.primary)
                    Text("النهاية: ${formatTime(sliderPosition.endInclusive.toLong())}", color = MaterialTheme.colorScheme.secondary)
                }

                Button(
                    onClick = {
                        tempSegmentName = "مقطع ${segments.size + 1}"
                        showNamingDialog = true
                    },
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                ) {
                    Text("حفظ المقطع")
                }
            }
        }

        Text(text = "قائمة التدريب:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(segments) { segment ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentSegment == segment) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = segment.name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = "${formatTime(segment.startTimeMs)} - ${formatTime(segment.endTimeMs)}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = {
                            currentSegment = segment
                            exoPlayer.seekTo(segment.startTimeMs)
                            exoPlayer.play()
                        }) { Icon(Icons.Default.PlayArrow, contentDescription = "تشغيل") }

                        IconButton(onClick = {
                            segmentToEdit = segment
                            tempSegmentName = segment.name
                            showNamingDialog = true
                        }) { Icon(Icons.Default.Edit, "تعديل الاسم") }

                        IconButton(onClick = {
                            segments.remove(segment)
                            if(currentSegment == segment) currentSegment = null
                        }) { Icon(Icons.Default.Delete, "حذف") }
                    }
                }
            }
        }

        Button(
            onClick = { currentSegment = null },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
        ) { Text("إيقاف التكرار") }
    }

    if (showNamingDialog) {
        AlertDialog(
            onDismissRequest = { showNamingDialog = false; segmentToEdit = null },
            title = { Text(if (segmentToEdit == null) "تسمية المقطع" else "تعديل الاسم") },
            text = {
                TextField(value = tempSegmentName, onValueChange = { tempSegmentName = it }, label = { Text("اسم المقطع") })
            },
            confirmButton = {
                Button(onClick = {
                    if (segmentToEdit == null) {
                        segments.add(VideoSegment(segments.size + 1, tempSegmentName, sliderPosition.start.toLong(), sliderPosition.endInclusive.toLong()))
                    } else {
                        val index = segments.indexOf(segmentToEdit)
                        if (index != -1) segments[index] = segmentToEdit!!.copy(name = tempSegmentName)
                    }
                    showNamingDialog = false
                    segmentToEdit = null
                }) { Text("حفظ") }
            }
        )
    }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "مرحبا $name!", modifier = modifier.padding(16.dp))
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ShadowingPlayerWTheme { Greeting("وليد") }
}

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import kotlin.math.roundToLong

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadowingPlayerWTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ShadowingScreen(
                        name = "وليد",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (ms % 1000) / 10
    return String.format(Locale.getDefault(), "%02d:%02d.%02d", minutes, seconds, millis)
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

    // منطق وضع الدقة العالية (ثانية واحدة)
    var isPrecisionMode by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lastInteractionTime) {
        if (lastInteractionTime > 0) {
            delay(2000) // الانتظار لمدة ثانيتين
            isPrecisionMode = true
        }
    }

    LaunchedEffect(exoPlayer.isPlaying) {
        while (true) {
            currentPlaybackPosition = exoPlayer.currentPosition
            delay(30)
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

    LaunchedEffect(currentSegment) {
        while (currentSegment != null) {
            if (exoPlayer.currentPosition >= currentSegment!!.endTimeMs) {
                exoPlayer.seekTo(currentSegment!!.startTimeMs)
            }
            delay(30)
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

        // منطقة الفيديو مع عرض التوقيت الحالي
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            AndroidView(
                factory = { PlayerView(it).apply { player = exoPlayer; useController = true } },
                modifier = Modifier.fillMaxSize()
            )

            if (selectedVideoUri != null) {
                // عرض التوقيت الحالي للقطة فوق الفيديو
                Surface(
                    color = if (isPrecisionMode) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPlaybackPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        if (selectedVideoUri != null && videoDuration > 0) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isPrecisionMode) "وضع التدقيق مفعّل: التحرك الآن بالثانية" else "الضبط الفائق (50ms لكل نقرة):",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPrecisionMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    RangeSlider(
                        value = sliderPosition,
                        onValueChange = { newRange ->
                            lastInteractionTime = System.currentTimeMillis()

                            var finalRange = newRange
                            if (isPrecisionMode) {
                                val snappedStart = (newRange.start / 1000f).roundToLong() * 1000f
                                val snappedEnd = (newRange.endInclusive / 1000f).roundToLong() * 1000f
                                finalRange = snappedStart..snappedEnd
                            }

                            if (finalRange.start != sliderPosition.start) {
                                exoPlayer.seekTo(finalRange.start.toLong())
                            } else if (finalRange.endInclusive != sliderPosition.endInclusive) {
                                exoPlayer.seekTo(finalRange.endInclusive.toLong())
                            }
                            sliderPosition = finalRange
                        },
                        valueRange = 0f..videoDuration.toFloat(),
                        modifier = Modifier.fillMaxSize()
                    )

                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                        val progress = currentPlaybackPosition.toFloat() / videoDuration.toFloat()
                        val xPos = size.width * progress
                        drawLine(color = Color.Red, start = Offset(xPos, 0f), end = Offset(xPos, size.height), strokeWidth = 2.dp.toPx())
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            isPrecisionMode = false
                            val newStart = (sliderPosition.start - 50f).coerceAtLeast(0f)
                            sliderPosition = newStart..sliderPosition.endInclusive
                            exoPlayer.seekTo(newStart.toLong())
                        }) { Icon(Icons.Default.KeyboardArrowLeft, "نقص") }

                        Text(formatTime(sliderPosition.start.toLong()), style = MaterialTheme.typography.bodySmall)

                        IconButton(onClick = {
                            isPrecisionMode = false
                            val newStart = (sliderPosition.start + 50f).coerceAtMost(sliderPosition.endInclusive - 50f)
                            sliderPosition = newStart..sliderPosition.endInclusive
                            exoPlayer.seekTo(newStart.toLong())
                        }) { Icon(Icons.Default.KeyboardArrowRight, "زيادة") }
                    }

                    Button(onClick = { isPrecisionMode = !isPrecisionMode }) {
                        Text(if (isPrecisionMode) "إلغاء التدقيق" else "وضع الـ 1s")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            isPrecisionMode = false
                            val newEnd = (sliderPosition.endInclusive - 50f).coerceAtLeast(sliderPosition.start + 50f)
                            sliderPosition = sliderPosition.start..newEnd
                            exoPlayer.seekTo(newEnd.toLong())
                        }) { Icon(Icons.Default.KeyboardArrowLeft, "نقص") }

                        Text(formatTime(sliderPosition.endInclusive.toLong()), style = MaterialTheme.typography.bodySmall)

                        IconButton(onClick = {
                            isPrecisionMode = false
                            val newEnd = (sliderPosition.endInclusive + 50f).coerceAtMost(videoDuration.toFloat())
                            sliderPosition = sliderPosition.start..newEnd
                            exoPlayer.seekTo(newEnd.toLong())
                        }) { Icon(Icons.Default.KeyboardArrowRight, "زيادة") }
                    }
                }

                Button(
                    onClick = {
                        tempSegmentName = "مقطع ${segments.size + 1}"
                        showNamingDialog = true
                    },
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                ) { Text("حفظ المقطع") }
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

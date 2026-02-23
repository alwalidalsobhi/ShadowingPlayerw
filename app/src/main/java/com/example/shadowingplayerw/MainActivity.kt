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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.input.KeyboardType
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
    val millis = (ms % 1000) / 100
    return String.format(Locale.getDefault(), "%02d:%02d.%d", minutes, seconds, millis)
}

// دالة مساعدة لتحويل المدخلات إلى مللي ثانية
fun timeToMs(min: String, sec: String, msPart: String): Long {
    val m = min.toLongOrNull() ?: 0L
    val s = sec.toLongOrNull() ?: 0L
    val ms = msPart.toLongOrNull() ?: 0L
    return (m * 60 * 1000) + (s * 1000) + (ms * 100)
}

@OptIn(UnstableApi::class)
@Composable
fun ShadowingScreen(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var videoDuration by remember { mutableLongStateOf(0L) }
    var currentPlaybackPosition by remember { mutableLongStateOf(0L) }
    var isActuallyPlaying by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) videoDuration = duration
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    isActuallyPlaying = isPlaying
                }
            })
        }
    }

    LaunchedEffect(exoPlayer.isPlaying) {
        while (true) {
            currentPlaybackPosition = exoPlayer.currentPosition
            delay(50)
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

    // متغيرات ديالوج الوقت اليدوي
    var showTimeInputDialog by remember { mutableStateOf(false) }
    var isEditingStart by remember { mutableStateOf(true) }

    LaunchedEffect(currentSegment) {
        while (currentSegment != null) {
            if (exoPlayer.currentPosition >= currentSegment!!.endTimeMs) {
                exoPlayer.seekTo(currentSegment!!.startTimeMs)
            }
            delay(50)
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

        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            AndroidView(
                factory = { PlayerView(it).apply { player = exoPlayer; useController = true } },
                modifier = Modifier.fillMaxSize()
            )
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

        if (selectedVideoUri != null && videoDuration > 0) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "الضبط الدقيق (0.1 ثانية):", style = MaterialTheme.typography.labelSmall)
                Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    RangeSlider(
                        value = sliderPosition,
                        onValueChange = { newRange ->
                            if (newRange.start != sliderPosition.start) {
                                exoPlayer.seekTo(newRange.start.toLong())
                            } else if (newRange.endInclusive != sliderPosition.endInclusive) {
                                exoPlayer.seekTo(newRange.endInclusive.toLong())
                            }
                            sliderPosition = newRange
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
                    // تحكم البداية
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val newStart = (sliderPosition.start - 100f).coerceAtLeast(0f)
                            sliderPosition = newStart..sliderPosition.endInclusive
                            exoPlayer.seekTo(newStart.toLong())
                        }) { Icon(Icons.Default.KeyboardArrowLeft, "نقص") }

                        TextButton(onClick = {
                            isEditingStart = true
                            showTimeInputDialog = true
                        }) {
                            Text(formatTime(sliderPosition.start.toLong()), style = MaterialTheme.typography.bodySmall)
                        }

                        IconButton(onClick = {
                            val newStart = (sliderPosition.start + 100f).coerceAtMost(sliderPosition.endInclusive - 100f)
                            sliderPosition = newStart..sliderPosition.endInclusive
                            exoPlayer.seekTo(newStart.toLong())
                        }) { Icon(Icons.Default.KeyboardArrowRight, "زيادة") }
                    }

                    // تحكم النهاية
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            val newEnd = (sliderPosition.endInclusive - 100f).coerceAtLeast(sliderPosition.start + 100f)
                            sliderPosition = sliderPosition.start..newEnd
                            exoPlayer.seekTo(newEnd.toLong())
                        }) { Icon(Icons.Default.KeyboardArrowLeft, "نقص") }

                        TextButton(onClick = {
                            isEditingStart = false
                            showTimeInputDialog = true
                        }) {
                            Text(formatTime(sliderPosition.endInclusive.toLong()), style = MaterialTheme.typography.bodySmall)
                        }

                        IconButton(onClick = {
                            val newEnd = (sliderPosition.endInclusive + 100f).coerceAtMost(videoDuration.toFloat())
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

                        // المطلوب: إمكانية الإيقاف المؤقت عند الضغط على المقطع وهو يعمل
                        IconButton(onClick = {
                            if (currentSegment == segment && isActuallyPlaying) {
                                exoPlayer.pause()
                            } else {
                                currentSegment = segment
                                exoPlayer.seekTo(segment.startTimeMs)
                                exoPlayer.play()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = if (currentSegment == segment && isActuallyPlaying) "إيقاف مؤقت" else "تشغيل"
                            )
                        }

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
            onClick = {
                currentSegment = null
                exoPlayer.pause()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
        ) { Text("إيقاف التكرار") }
    }

    // ديالوج إدخال الوقت يدوياً
    if (showTimeInputDialog) {
        var min by remember { mutableStateOf("0") }
        var sec by remember { mutableStateOf("0") }
        var msp by remember { mutableStateOf("0") }

        AlertDialog(
            onDismissRequest = { showTimeInputDialog = false },
            title = { Text(if (isEditingStart) "ضبط وقت البداية" else "ضبط وقت النهاية") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = min,
                        onValueChange = { if (it.length <= 2) min = it },
                        label = { Text("دقائق") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = sec,
                        onValueChange = { if (it.length <= 2) sec = it },
                        label = { Text("ثواني") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = msp,
                        onValueChange = { if (it.length <= 1) msp = it },
                        label = { Text("0.1ث") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newMs = timeToMs(min, sec, msp).toFloat()
                    if (isEditingStart) {
                        val validatedStart = newMs.coerceIn(0f, sliderPosition.endInclusive - 100f)
                        sliderPosition = validatedStart..sliderPosition.endInclusive
                        exoPlayer.seekTo(validatedStart.toLong())
                    } else {
                        val validatedEnd = newMs.coerceIn(sliderPosition.start + 100f, videoDuration.toFloat())
                        sliderPosition = sliderPosition.start..validatedEnd
                        exoPlayer.seekTo(validatedEnd.toLong())
                    }
                    showTimeInputDialog = false
                }) { Text("تطبيق") }
            },
            dismissButton = {
                TextButton(onClick = { showTimeInputDialog = false }) { Text("إلغاء") }
            }
        )
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

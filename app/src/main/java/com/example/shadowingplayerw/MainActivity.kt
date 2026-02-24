package com.example.shadowingplayerw

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.shadowingplayerw.ui.theme.ShadowingPlayerWTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

// Constants for Persistence
private const val PREFS_NAME = "shadowing_prefs"
private const val KEY_VIDEO_URI = "selected_video_uri"
private const val KEY_SEGMENTS = "video_segments"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadowingPlayerWTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ShadowingScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// دالة لالتقاط لقطة من الفيديو وحفظها كملف
suspend fun captureVideoThumbnail(context: Context, videoUri: Uri, timeMs: Long): String? {
    return withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val fileName = "thumb_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                }
                file.absolutePath
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            retriever.release()
        }
    }
}

// دالة لنقل ملف الفيديو إلى مساحة التخزين الخاصة بالتطبيق
suspend fun copyVideoToInternalStorage(context: Context, sourceUri: Uri): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val fileName = "app_video_${System.currentTimeMillis()}.mp4"
            val destinationFile = File(context.filesDir, fileName)
            contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Uri.fromFile(destinationFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
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

fun timeToMs(min: String, sec: String, msPart: String): Long {
    val m = min.toLongOrNull() ?: 0L
    val s = sec.toLongOrNull() ?: 0L
    val ms = msPart.toLongOrNull() ?: 0L
    return (m * 60 * 1000) + (s * 1000) + (ms * 100)
}

@OptIn(UnstableApi::class)
@Composable
fun ShadowingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val gson = remember { Gson() }
    var selectedVideoUri by remember {
        mutableStateOf<Uri?>(sharedPrefs.getString(KEY_VIDEO_URI, null)?.let { Uri.parse(it) })
    }
    val segments = remember {
        val savedSegments = sharedPrefs.getString(KEY_SEGMENTS, null)
        val list = if (savedSegments != null) {
            val type = object : TypeToken<List<VideoSegment>>() {}.type
            gson.fromJson<List<VideoSegment>>(savedSegments, type)
        } else emptyList()
        mutableStateListOf<VideoSegment>().apply { addAll(list) }
    }
    fun saveSegments() {
        sharedPrefs.edit().putString(KEY_SEGMENTS, gson.toJson(segments.toList())).apply()
    }
    var videoDuration by remember { mutableLongStateOf(0L) }
    var currentPlaybackPosition by remember { mutableLongStateOf(0L) }
    var isActuallyPlaying by remember { mutableStateOf(false) }
    var isCopying by remember { mutableStateOf(false) }
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
    ) { uri: Uri? ->
        uri?.let { sourceUri ->
            isCopying = true
            scope.launch {
                val internalUri = copyVideoToInternalStorage(context, sourceUri)
                selectedVideoUri = internalUri
                sharedPrefs.edit().putString(KEY_VIDEO_URI, internalUri.toString()).apply()
                isCopying = false
            }
        }
    }
    var currentSegment by remember { mutableStateOf<VideoSegment?>(null) }
    var sliderPosition by remember { mutableStateOf(0f..1000f) }
    var showNamingDialog by remember { mutableStateOf(false) }
    var tempSegmentName by remember { mutableStateOf("") }
    var segmentToEdit by remember { mutableStateOf<VideoSegment?>(null) }
    var showTimeInputDialog by remember { mutableStateOf(false) }
    var isEditingStart by remember { mutableStateOf(true) }
    var isLoopingActive by remember { mutableStateOf(false) }
    LaunchedEffect(isLoopingActive, currentPlaybackPosition, sliderPosition) {
        if (isLoopingActive) {
            if (currentPlaybackPosition >= sliderPosition.endInclusive.toLong() || currentPlaybackPosition < sliderPosition.start.toLong()) {
                exoPlayer.seekTo(sliderPosition.start.toLong())
            }
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            player = exoPlayer
                            useController = true
                            // تم تغيير RESIZE_MODE_FIT إلى RESIZE_MODE_ZOOM لإزالة السواد
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (isCopying) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (selectedVideoUri != null && videoDuration > 0) {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = "الضبط الدقيق (0.1 ثانية):", style = MaterialTheme.typography.labelSmall)
                    Box(modifier = Modifier.fillMaxWidth().height(36.dp)) {
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
                            drawLine(color = Color(0xFF2196F3), start = Offset(xPos, 0f), end = Offset(xPos, size.height), strokeWidth = 2.dp.toPx())
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                            IconButton(onClick = {
                                val newStart = (sliderPosition.start - 100f).coerceAtLeast(0f)
                                sliderPosition = newStart..sliderPosition.endInclusive
                                exoPlayer.seekTo(newStart.toLong())
                            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "نقص", modifier = Modifier.size(18.dp)) }
                            TextButton(onClick = { isEditingStart = true; showTimeInputDialog = true }, contentPadding = PaddingValues(0.dp)) {
                                Text(formatTime(sliderPosition.start.toLong()), style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = {
                                val newStart = (sliderPosition.start + 100f).coerceAtMost(sliderPosition.endInclusive - 100f)
                                sliderPosition = newStart..sliderPosition.endInclusive
                                exoPlayer.seekTo(newStart.toLong())
                            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "زيادة", modifier = Modifier.size(18.dp)) }
                        }
                        Surface(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = formatTime(currentPlaybackPosition),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = {
                                val newEnd = (sliderPosition.endInclusive - 100f).coerceAtLeast(sliderPosition.start + 100f)
                                sliderPosition = sliderPosition.start..newEnd
                                exoPlayer.seekTo(newEnd.toLong())
                            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "نقص", modifier = Modifier.size(18.dp)) }
                            TextButton(onClick = { isEditingStart = false; showTimeInputDialog = true }, contentPadding = PaddingValues(0.dp)) {
                                Text(formatTime(sliderPosition.endInclusive.toLong()), style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = {
                                val newEnd = (sliderPosition.endInclusive + 100f).coerceAtMost(videoDuration.toFloat())
                                sliderPosition = sliderPosition.start..newEnd
                                exoPlayer.seekTo(newEnd.toLong())
                            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "زيادة", modifier = Modifier.size(18.dp)) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { isLoopingActive = !isLoopingActive },
                            modifier = Modifier.height(32.dp).padding(end = 8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isLoopingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (isLoopingActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer                             )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تكرار", style = MaterialTheme.typography.labelMedium)
                        }
                        FilledTonalButton(
                            onClick = { launcher.launch("video/*") },
                            modifier = Modifier.height(32.dp).padding(end = 8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text("تبديل الفيديو", style = MaterialTheme.typography.labelMedium)
                        }
                        FilledTonalButton(
                            onClick = {
                                tempSegmentName = "مقطع ${segments.size + 1}"
                                showNamingDialog = true
                            },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text("حفظ المقطع", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        } else if (selectedVideoUri == null && !isCopying) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Button(onClick = { launcher.launch("video/*") }) {
                    Text("فتح فيديو من الهاتف")
                }
            }
        }
        Text(text = "قائمة التدريب:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(segments) { segment ->
                val isThisSegmentSelected = currentSegment == segment
                val isThisSegmentPlaying = isThisSegmentSelected && isActuallyPlaying
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isThisSegmentSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        segment.thumbnailPath?.let { path ->
                            val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = segment.name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = "${formatTime(segment.startTimeMs)} - ${formatTime(segment.endTimeMs)}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(
                            onClick = {
                                if (isThisSegmentPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (!isThisSegmentSelected) {
                                        currentSegment = segment
                                        sliderPosition = segment.startTimeMs.toFloat()..segment.endTimeMs.toFloat()
                                        exoPlayer.seekTo(segment.startTimeMs)
                                    }
                                    isLoopingActive = true
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = if (isThisSegmentPlaying)
                                    painterResource(id = android.R.drawable.ic_media_pause)
                                else
                                    painterResource(id = android.R.drawable.ic_media_play),
                                contentDescription = "تشغيل/إيقاف",
                                modifier = Modifier.size(24.dp),
                                tint = if (isThisSegmentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            segmentToEdit = segment
                            tempSegmentName = segment.name
                            showNamingDialog = true
                        }) { Icon(Icons.Default.Edit, "تعديل", modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = {
                            segment.thumbnailPath?.let { File(it).delete() }
                            segments.remove(segment)
                            if (currentSegment == segment) currentSegment = null
                            saveSegments()
                        }) { Icon(Icons.Default.Delete, "حذف", modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    }
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
                    scope.launch {
                        if (segmentToEdit == null) {
                            val thumbPath = selectedVideoUri?.let { captureVideoThumbnail(context, it, sliderPosition.start.toLong()) }
                            segments.add(VideoSegment(
                                id = segments.size + 1,
                                name = tempSegmentName,
                                startTimeMs = sliderPosition.start.toLong(),
                                endTimeMs = sliderPosition.endInclusive.toLong(),
                                thumbnailPath = thumbPath
                            ))
                        } else {
                            val index = segments.indexOf(segmentToEdit)
                            if (index != -1) segments[index] = segmentToEdit!!.copy(name = tempSegmentName)
                        }
                        saveSegments()
                        showNamingDialog = false
                        segmentToEdit = null
                    }
                }) { Text("حفظ") }
            }
        )
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ShadowingPlayerWTheme { ShadowingScreen() }
}

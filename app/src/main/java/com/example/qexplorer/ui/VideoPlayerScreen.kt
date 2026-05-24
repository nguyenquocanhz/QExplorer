package com.example.qexplorer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

enum class HudType {
    VOLUME, BRIGHTNESS
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun VideoPlayerScreen(
    filePath: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val file = remember(filePath) { File(filePath) }
    val activity = remember(context) { context.findActivity() }
    
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    
    var showHud by remember { mutableStateOf(false) }
    var hudType by remember { mutableStateOf<HudType?>(null) }
    var hudValue by remember { mutableStateOf(0f) }
    
    val coroutineScope = rememberCoroutineScope()
    var hudJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F)) // Obsidian black background
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    val controller = MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    mediaController = controller
                    setVideoURI(Uri.fromFile(file))
                    setOnPreparedListener {
                        start()
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        )

        // Gesture Overlay Layer (detects tap and drag)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            mediaController?.let { controller ->
                                if (controller.isShowing) {
                                    controller.hide()
                                } else {
                                    controller.show(3000)
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    var isLeftSide = false
                    var startBrightness = 0f
                    var startVolumeFraction = 0f

                    detectDragGestures(
                        onDragStart = { offset ->
                            isLeftSide = offset.x < size.width / 2
                            
                            // Get current brightness
                            val lp = activity?.window?.attributes
                            val currentB = lp?.screenBrightness ?: -1f
                            // Default to 0.5f if brightness is system automatic (represented by negative values)
                            startBrightness = if (currentB < 0f) 0.5f else currentB
                            
                            // Get current volume
                            val currentV = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            startVolumeFraction = currentV.toFloat() / maxVolume.coerceAtLeast(1)
                            
                            hudType = if (isLeftSide) HudType.BRIGHTNESS else HudType.VOLUME
                            hudValue = if (isLeftSide) startBrightness else startVolumeFraction
                            showHud = true
                            hudJob?.cancel()
                        },
                        onDrag = { change, dragAmount ->
                            val height = size.height
                            val deltaFraction = -dragAmount.y / height.coerceAtLeast(1)
                            
                            if (isLeftSide) {
                                // Clamp minimum brightness at 0.05f to prevent the screen from going completely pitch black
                                val newB = (startBrightness + deltaFraction).coerceIn(0.05f, 1f)
                                startBrightness = newB
                                activity?.window?.let { window ->
                                    val lp = window.attributes
                                    lp.screenBrightness = newB
                                    window.attributes = lp
                                }
                                hudValue = newB
                            } else {
                                val newVF = (startVolumeFraction + deltaFraction).coerceIn(0f, 1f)
                                startVolumeFraction = newVF
                                val targetVolume = (newVF * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
                                hudValue = newVF
                            }
                            showHud = true
                        },
                        onDragEnd = {
                            hudJob?.cancel()
                            hudJob = coroutineScope.launch {
                                delay(1500)
                                showHud = false
                            }
                        },
                        onDragCancel = {
                            hudJob?.cancel()
                            hudJob = coroutineScope.launch {
                                delay(1500)
                                showHud = false
                            }
                        }
                    )
                }
        )

        // HUD Indicator Card
        if (showHud) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .width(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val icon = when (hudType) {
                        HudType.VOLUME -> {
                            val volPct = hudValue
                            when {
                                volPct == 0f -> Icons.Rounded.VolumeMute
                                volPct < 0.5f -> Icons.Rounded.VolumeDown
                                else -> Icons.Rounded.VolumeUp
                            }
                        }
                        HudType.BRIGHTNESS -> {
                            val brightPct = hudValue
                            when {
                                brightPct < 0.35f -> Icons.Rounded.BrightnessLow
                                brightPct < 0.7f -> Icons.Rounded.BrightnessMedium
                                else -> Icons.Rounded.BrightnessHigh
                            }
                        }
                        null -> Icons.Rounded.VolumeUp
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${(hudValue * 100).roundToInt()}%",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = hudValue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }

        // Title and Back Button Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }
    }
}

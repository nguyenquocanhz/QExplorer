package com.example.qexplorer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qexplorer.data.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PhotoViewerScreen(
    filePath: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialFile = remember(filePath) { File(filePath) }
    var siblingImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var initialIndex by remember { mutableStateOf(-1) }
    var isListLoading by remember { mutableStateOf(true) }

    LaunchedEffect(filePath) {
        isListLoading = true
        val list = withContext(Dispatchers.IO) {
            val parent = initialFile.parentFile
            if (parent != null && parent.isDirectory) {
                parent.listFiles()?.filter {
                    it.isFile && it.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
                }?.sortedBy { it.name } ?: listOf(initialFile)
            } else {
                listOf(initialFile)
            }
        }
        siblingImages = list
        initialIndex = list.indexOfFirst { it.absolutePath == filePath }.coerceAtLeast(0)
        isListLoading = false
    }

    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F)) // Obsidian black background
    ) {
        if (isListLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (siblingImages.isEmpty() || initialIndex == -1) {
            Text(
                text = "Không có hình ảnh nào",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val pagerState = rememberPagerState(
                initialPage = initialIndex,
                pageCount = { siblingImages.size }
            )

            // Dynamic header file determination
            val currentFile = remember(pagerState.currentPage, siblingImages) {
                if (pagerState.currentPage in siblingImages.indices) {
                    siblingImages[pagerState.currentPage]
                } else {
                    initialFile
                }
            }

            // Sync zoomed state when page changes
            LaunchedEffect(pagerState.currentPage) {
                isCurrentPageZoomed = false
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isCurrentPageZoomed,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val isActive = pagerState.currentPage == page
                PhotoPage(
                    file = siblingImages[page],
                    isActive = isActive,
                    onZoomChanged = { zoomed ->
                        if (isActive) {
                            isCurrentPageZoomed = zoomed
                        }
                    }
                )
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
                        text = currentFile.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                    val subtitleText = remember(currentFile, siblingImages, pagerState.currentPage) {
                        val sizeStr = if (currentFile.exists()) formatSize(currentFile.length()) else ""
                        if (siblingImages.size > 1) {
                            "$sizeStr  •  ${pagerState.currentPage + 1}/${siblingImages.size}"
                        } else {
                            sizeStr
                        }
                    }
                    Text(
                        text = subtitleText,
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoPage(
    file: File,
    isActive: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(file) {
        isLoading = true
        bitmap = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)
            } catch (e: Exception) {
                null
            }
        }
        isLoading = false
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset zoom when page becomes inactive
    LaunchedEffect(isActive) {
        if (!isActive) {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(scale) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    val zoomed = newScale > 1f
                    
                    if (zoomed) {
                        // Drag is active only when zoomed
                        offset = Offset(
                            x = offset.x + pan.x,
                            y = offset.y + pan.y
                        )
                    } else {
                        offset = Offset.Zero
                    }
                    
                    scale = newScale
                    onZoomChanged(zoomed)
                }
            }
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (bitmap == null) {
            Text(
                text = "Không thể tải hình ảnh này",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        }
    }
}

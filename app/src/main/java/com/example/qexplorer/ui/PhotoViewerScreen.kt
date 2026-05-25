package com.example.qexplorer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qexplorer.data.formatSize
import com.example.qexplorer.data.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun PhotoViewerScreen(
    filePath: String,
    directoryPath: String = "",
    category: String = "",
    sortBy: String = "name",
    sortAsc: Boolean = true,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val initialFile = remember(filePath) { File(filePath) }
    var siblingImages by remember { mutableStateOf<List<File>>(emptyList()) }
    var initialIndex by remember { mutableStateOf(-1) }
    var isListLoading by remember { mutableStateOf(true) }

    LaunchedEffect(filePath) {
        isListLoading = true
        val list = withContext(Dispatchers.IO) {
            if (category.isNotEmpty()) {
                StorageManager.getCategoryFiles(category)
                    .map { File(it.path) }
            } else if (directoryPath.isNotEmpty()) {
                StorageManager.getFiles(directoryPath, sortBy, sortAsc)
                    .filter { !it.isDirectory && it.extension.lowercase(Locale.getDefault()) in StorageManager.imageExtensions }
                    .map { File(it.path) }
            } else {
                val parent = initialFile.parentFile
                if (parent != null && parent.isDirectory) {
                    parent.listFiles()?.filter {
                        it.isFile && it.extension.lowercase(Locale.getDefault()) in StorageManager.imageExtensions
                    }?.sortedBy { it.name } ?: listOf(initialFile)
                } else {
                    listOf(initialFile)
                }
            }
        }
        siblingImages = list
        initialIndex = list.indexOfFirst { it.absolutePath == filePath }.coerceAtLeast(0)
        isListLoading = false
    }

    var isCurrentPageZoomed by remember { mutableStateOf(false) }
    var isSlideshowPlaying by remember { mutableStateOf(false) }

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

            // Slideshow autoplay effect
            LaunchedEffect(isSlideshowPlaying) {
                if (isSlideshowPlaying) {
                    while (true) {
                        delay(3000)
                        val nextPage = if (pagerState.currentPage + 1 < siblingImages.size) {
                            pagerState.currentPage + 1
                        } else {
                            0
                        }
                        if (siblingImages.isNotEmpty()) {
                            pagerState.animateScrollToPage(nextPage)
                        }
                    }
                }
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
                            if (zoomed) isSlideshowPlaying = false // stop slideshow on zoom
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

            // Bottom Carousel & Quick actions Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Slideshow control & quick actions row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        shareSingleImage(context, currentFile)
                    }) {
                        Icon(imageVector = Icons.Rounded.Share, contentDescription = "Chia sẻ hình ảnh", tint = Color.White)
                    }

                    IconButton(
                        onClick = { isSlideshowPlaying = !isSlideshowPlaying },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isSlideshowPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Slideshow",
                            tint = Color.White
                        )
                    }

                    var showDeleteConfirm by remember { mutableStateOf(false) }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(imageVector = Icons.Rounded.DeleteOutline, contentDescription = "Xóa", tint = Color.Red)
                    }
                    
                    if (showDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirm = false },
                            title = { Text("Chuyển vào Thùng rác?", fontWeight = FontWeight.Bold) },
                            text = { Text("Bạn có chắc chắn muốn chuyển hình ảnh này vào Thùng rác?") },
                            confirmButton = {
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                    onClick = {
                                        showDeleteConfirm = false
                                        isSlideshowPlaying = false
                                        coroutineScope.launch {
                                            val trashPath = StorageManager.TrashManager.moveToTrash(currentFile.absolutePath)
                                            if (trashPath != null) {
                                                Toast.makeText(context, "Đã chuyển vào Thùng rác", Toast.LENGTH_SHORT).show()
                                                val newList = siblingImages.toMutableList()
                                                newList.remove(currentFile)
                                                siblingImages = newList
                                                if (newList.isEmpty()) {
                                                    onBack()
                                                }
                                            } else {
                                                Toast.makeText(context, "Xóa thất bại", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text("Xóa", color = Color.White)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirm = false }) {
                                    Text("Hủy")
                                }
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Thumbnail Strip
                val listState = rememberLazyListState()
                LaunchedEffect(pagerState.currentPage) {
                    if (siblingImages.isNotEmpty() && pagerState.currentPage in siblingImages.indices) {
                        listState.animateScrollToItem(pagerState.currentPage)
                    }
                }
                
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(siblingImages) { index, file ->
                        ThumbnailItem(
                            file = file,
                            isSelected = index == pagerState.currentPage,
                            onClick = {
                                isSlideshowPlaying = false
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThumbnailItem(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) {
        bitmap = withContext(Dispatchers.IO) {
            decodeSampledBitmapFromFile(file.absolutePath, 80, 80)
        }
    }
    
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E212B))
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun shareSingleImage(context: android.content.Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.example.qexplorer.fileprovider", file)
        val mime = context.contentResolver.getType(uri) ?: "image/*"
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mime
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Chia sẻ hình ảnh"))
    } catch (e: Exception) {
        Toast.makeText(context, "Không thể chia sẻ hình ảnh", Toast.LENGTH_SHORT).show()
    }
}

fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        BitmapFactory.decodeFile(path, options)
    } catch (e: Exception) {
        null
    }
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
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
                detectTransformGesturesCustom(
                    isZoomed = { scale > 1f }
                ) { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    val zoomed = newScale > 1f
                    
                    if (zoomed) {
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

suspend fun PointerInputScope.detectTransformGesturesCustom(
    panZoomLock: Boolean = false,
    consumeOnlyIfZoomed: Boolean = true,
    isZoomed: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation) * 45f
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (zoomChange != 1f || rotationChange != 0f || panChange != Offset.Zero) {
                        onGesture(centroid, panChange, zoomChange, rotationChange)
                    }
                    val shouldConsume = !consumeOnlyIfZoomed || isZoomed() || event.changes.size > 1
                    if (shouldConsume) {
                        event.changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

package com.example.qexplorer.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qexplorer.data.FileItem
import com.example.qexplorer.data.StorageManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var trashFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFiles = remember { mutableStateListOf<FileItem>() }

    // Dialog states
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var itemForActions by remember { mutableStateOf<FileItem?>(null) }
    var showSingleDeleteDialog by remember { mutableStateOf(false) }
    var showMultiDeleteDialog by remember { mutableStateOf(false) }

    fun refreshTrash() {
        coroutineScope.launch {
            isLoading = true
            trashFiles = StorageManager.TrashManager.getTrashFiles()
            selectedFiles.clear()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshTrash()
    }

    val isSelectionMode = selectedFiles.isNotEmpty()

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("Đã chọn ${selectedFiles.size} mục", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = { selectedFiles.clear() }) {
                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Hủy chọn")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (selectedFiles.size == trashFiles.size) {
                                selectedFiles.clear()
                            } else {
                                selectedFiles.clear()
                                selectedFiles.addAll(trashFiles)
                            }
                        }) {
                            Icon(
                                imageVector = if (selectedFiles.size == trashFiles.size) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                contentDescription = "Chọn tất cả"
                            )
                        }
                        IconButton(onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                var allSuccess = true
                                for (item in selectedFiles) {
                                    val success = StorageManager.TrashManager.restoreFromTrash(item.path)
                                    if (!success) allSuccess = false
                                }
                                isLoading = false
                                if (allSuccess) {
                                    Toast.makeText(context, "Đã khôi phục các tập tin", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Có tập tin khôi phục thất bại", Toast.LENGTH_SHORT).show()
                                }
                                refreshTrash()
                            }
                        }) {
                            Icon(imageVector = Icons.Rounded.Restore, contentDescription = "Khôi phục")
                        }
                        IconButton(onClick = { showMultiDeleteDialog = true }) {
                            Icon(imageVector = Icons.Rounded.DeleteForever, contentDescription = "Xóa vĩnh viễn", tint = Color.Red)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("Thùng rác", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (trashFiles.isNotEmpty()) {
                            IconButton(onClick = { showEmptyTrashDialog = true }) {
                                Icon(imageVector = Icons.Rounded.DeleteSweep, contentDescription = "Dọn sạch thùng rác", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (trashFiles.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Thùng rác trống",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Các tập tin bị xóa tạm thời sẽ hiển thị ở đây",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(trashFiles) { fileItem ->
                        val isSelected = selectedFiles.contains(fileItem)
                        TrashFileRow(
                            item = fileItem,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    if (isSelected) selectedFiles.remove(fileItem) else selectedFiles.add(fileItem)
                                } else {
                                    itemForActions = fileItem
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    selectedFiles.add(fileItem)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Dialog: Empty Trash
    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text("Dọn sạch thùng rác?", fontWeight = FontWeight.Bold) },
            text = { Text("Tất cả các tập tin trong thùng rác sẽ bị xóa vĩnh viễn. Thao tác này không thể hoàn tác.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        coroutineScope.launch {
                            showEmptyTrashDialog = false
                            isLoading = true
                            StorageManager.TrashManager.emptyTrash()
                            refreshTrash()
                        }
                    }
                ) {
                    Text("Dọn sạch", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Dialog: Multi Delete Confirm
    if (showMultiDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showMultiDeleteDialog = false },
            title = { Text("Xóa vĩnh viễn các mục đã chọn?", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc muốn xóa vĩnh viễn ${selectedFiles.size} mục đã chọn? Thao tác này không thể hoàn tác.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        coroutineScope.launch {
                            showMultiDeleteDialog = false
                            isLoading = true
                            var allSuccess = true
                            for (item in selectedFiles) {
                                val success = StorageManager.TrashManager.deletePermanently(item.path)
                                if (!success) allSuccess = false
                            }
                            isLoading = false
                            if (allSuccess) {
                                Toast.makeText(context, "Đã xóa vĩnh viễn các tập tin", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Có tập tin xóa thất bại", Toast.LENGTH_SHORT).show()
                            }
                            refreshTrash()
                        }
                    }
                ) {
                    Text("Xóa vĩnh viễn", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Sheet / Dialog for single item actions
    itemForActions?.let { item ->
        AlertDialog(
            onDismissRequest = { itemForActions = null },
            title = {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                val originalPath = remember(item) { StorageManager.TrashManager.getOriginalPath(item.path) }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Đường dẫn gốc: $originalPath",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .clickable {
                                itemForActions = null
                                coroutineScope.launch {
                                    isLoading = true
                                    val success = StorageManager.TrashManager.restoreFromTrash(item.path)
                                    isLoading = false
                                    if (success) {
                                        Toast.makeText(context, "Đã khôi phục tập tin", Toast.LENGTH_SHORT).show()
                                        refreshTrash()
                                    } else {
                                        Toast.makeText(context, "Khôi phục thất bại", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Rounded.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Khôi phục tập tin", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                            .clickable {
                                showSingleDeleteDialog = true
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Xóa vĩnh viễn", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { itemForActions = null }) {
                    Text("Đóng")
                }
            }
        )
    }

    // Dialog: Single delete confirmation
    if (showSingleDeleteDialog && itemForActions != null) {
        val fileItem = itemForActions!!
        AlertDialog(
            onDismissRequest = { showSingleDeleteDialog = false },
            title = { Text("Xóa vĩnh viễn?", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn xóa vĩnh viễn ${fileItem.name}? Thao tác này không thể khôi phục.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        coroutineScope.launch {
                            showSingleDeleteDialog = false
                            itemForActions = null
                            isLoading = true
                            val success = StorageManager.TrashManager.deletePermanently(fileItem.path)
                            isLoading = false
                            if (success) {
                                Toast.makeText(context, "Đã xóa vĩnh viễn", Toast.LENGTH_SHORT).show()
                                refreshTrash()
                            } else {
                                Toast.makeText(context, "Xóa thất bại", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Xóa vĩnh viễn", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSingleDeleteDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrashFileRow(
    item: FileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val fileIcon = getIconForFile(item)
    val iconColor = getIconColorForFile(item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .background(iconColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fileIcon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.formattedSize,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                )
                Text(
                    text = "Đã xóa: " + item.formattedDate,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

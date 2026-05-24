package com.example.qexplorer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation3.runtime.NavKey
import com.example.qexplorer.Explorer
import com.example.qexplorer.data.FileItem
import com.example.qexplorer.data.StorageManager
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    path: String,
    category: String,
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("name") } // name, size, date
    var sortAsc by remember { mutableStateOf(true) }
    
    // File Action states
    var selectedFileForActions by remember { mutableStateOf<FileItem?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    val isCategoryMode = category.isNotEmpty()
    val currentPath = if (isCategoryMode) "" else path.ifEmpty { StorageManager.rootPath }

    // Fetch Files
    fun refreshFiles() {
        coroutineScope.launch {
            isLoading = true
            files = if (isCategoryMode) {
                StorageManager.getCategoryFiles(category)
            } else {
                StorageManager.getFiles(currentPath, sortBy, sortAsc)
            }
            isLoading = false
        }
    }

    LaunchedEffect(currentPath, category, sortBy, sortAsc) {
        refreshFiles()
    }

    // Filtered files
    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.trim().isEmpty()) {
            files
        } else {
            files.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (isCategoryMode) {
                                    category.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                                } else {
                                    val f = File(currentPath)
                                    if (f.absolutePath == StorageManager.rootPath) "Bộ nhớ trong" else f.name
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            if (!isCategoryMode && filteredFiles.isNotEmpty()) {
                                Text(
                                    text = "${filteredFiles.size} mục",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Search Button
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search"
                            )
                        }

                        // Layout Toggle
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.Rounded.List else Icons.Rounded.GridView,
                                contentDescription = "Toggle Layout"
                            )
                        }

                        // Sort Menu
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(imageVector = Icons.Rounded.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sắp xếp theo Tên (A-Z)") },
                                    leadingIcon = { Icon(Icons.Rounded.Abc, contentDescription = null) },
                                    onClick = {
                                        sortBy = "name"
                                        sortAsc = true
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sắp xếp theo Ngày") },
                                    leadingIcon = { Icon(Icons.Rounded.CalendarToday, contentDescription = null) },
                                    onClick = {
                                        sortBy = "date"
                                        sortAsc = false
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sắp xếp theo Kích thước") },
                                    leadingIcon = { Icon(Icons.Rounded.FormatLineSpacing, contentDescription = null) },
                                    onClick = {
                                        sortBy = "size"
                                        sortAsc = false
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Search Bar
                AnimatedVisibility(
                    visible = isSearchActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Tìm kiếm tập tin, thư mục...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }

                // Breadcrumbs (Only in folder mode)
                if (!isCategoryMode) {
                    BreadcrumbNavigation(
                        rootPath = StorageManager.rootPath,
                        currentPath = currentPath,
                        onBreadcrumbClick = { targetPath ->
                            onNavigate(Explorer(path = targetPath))
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (!isCategoryMode) {
                FloatingActionButton(
                    onClick = { showCreateFolderDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.CreateNewFolder, contentDescription = "Tạo thư mục mới")
                }
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
            } else if (filteredFiles.isEmpty()) {
                EmptyStateView(
                    isSearch = searchQuery.isNotEmpty(),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredFiles) { fileItem ->
                            GridFileItemView(
                                item = fileItem,
                                onClick = {
                                    handleItemClick(context, fileItem, onNavigate)
                                },
                                onLongClick = {
                                    selectedFileForActions = fileItem
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredFiles) { fileItem ->
                            ListFileItemView(
                                item = fileItem,
                                onClick = {
                                    handleItemClick(context, fileItem, onNavigate)
                                },
                                onActionClick = {
                                    selectedFileForActions = fileItem
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Action Menu Dialog Sheet
    selectedFileForActions?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedFileForActions = null },
            title = {
                Text(
                    text = item.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionRow(icon = Icons.Rounded.OpenInNew, title = "Mở tập tin", tint = MaterialTheme.colorScheme.primary) {
                        selectedFileForActions = null
                        openFile(context, item)
                    }
                    val isTextFile = remember(item) {
                        val textExtensions = setOf("txt", "md", "json", "xml", "html", "css", "kt", "java", "ini")
                        !item.isDirectory && textExtensions.contains(item.extension.lowercase(Locale.getDefault()))
                    }
                    if (isTextFile) {
                        ActionRow(icon = Icons.Rounded.Description, title = "Chỉnh sửa tập tin", tint = MaterialTheme.colorScheme.primary) {
                            selectedFileForActions = null
                            onNavigate(com.example.qexplorer.Editor(path = item.path))
                        }
                    }
                    ActionRow(icon = Icons.Rounded.Archive, title = "Nén thành ZIP", tint = MaterialTheme.colorScheme.primary) {
                        selectedFileForActions = null
                        val parentFile = File(item.path)
                        val parentDir = parentFile.parent ?: StorageManager.rootPath
                        val zipName = if (item.isDirectory) "${item.name}.zip" else "${parentFile.nameWithoutExtension}.zip"
                        val destZipPath = parentDir + File.separator + zipName
                        
                        coroutineScope.launch {
                            isLoading = true
                            val success = StorageManager.zip(item.path, destZipPath)
                            isLoading = false
                            if (success) {
                                Toast.makeText(context, "Đã nén thành công", Toast.LENGTH_SHORT).show()
                                refreshFiles()
                            } else {
                                Toast.makeText(context, "Nén thất bại", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    if (item.extension.lowercase(Locale.getDefault()) == "zip") {
                        ActionRow(icon = Icons.Rounded.Unarchive, title = "Giải nén ZIP", tint = MaterialTheme.colorScheme.primary) {
                            selectedFileForActions = null
                            val parentFile = File(item.path)
                            val parentDir = parentFile.parent ?: StorageManager.rootPath
                            val destName = parentFile.nameWithoutExtension
                            val destPath = parentDir + File.separator + destName
                            
                            coroutineScope.launch {
                                isLoading = true
                                val success = StorageManager.unzip(item.path, destPath)
                                isLoading = false
                                if (success) {
                                    Toast.makeText(context, "Đã giải nén thành công", Toast.LENGTH_SHORT).show()
                                    refreshFiles()
                                } else {
                                    Toast.makeText(context, "Giải nén thất bại", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    ActionRow(icon = Icons.Rounded.Edit, title = "Đổi tên", tint = MaterialTheme.colorScheme.onSurface) {
                        showRenameDialog = true
                    }
                    if (!item.isDirectory) {
                        ActionRow(icon = Icons.Rounded.Share, title = "Chia sẻ", tint = MaterialTheme.colorScheme.onSurface) {
                            selectedFileForActions = null
                            shareFile(context, item)
                        }
                    }
                    ActionRow(icon = Icons.Rounded.Info, title = "Chi tiết", tint = MaterialTheme.colorScheme.onSurface) {
                        showDetailsDialog = true
                    }
                    ActionRow(icon = Icons.Rounded.Delete, title = "Xóa", tint = Color.Red) {
                        showDeleteConfirmDialog = true
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedFileForActions = null }) {
                    Text("Đóng")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Rename Dialog
    if (showRenameDialog && selectedFileForActions != null) {
        val item = selectedFileForActions!!
        var newName by remember { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Đổi tên", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.trim().isNotEmpty()) {
                            coroutineScope.launch {
                                val success = StorageManager.renameFile(item.path, newName)
                                if (success) {
                                    Toast.makeText(context, "Đổi tên thành công", Toast.LENGTH_SHORT).show()
                                    showRenameDialog = false
                                    selectedFileForActions = null
                                    refreshFiles()
                                } else {
                                    Toast.makeText(context, "Đổi tên thất bại", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Đồng ý")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && selectedFileForActions != null) {
        val item = selectedFileForActions!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Xác nhận xóa", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc chắn muốn xóa ${item.name}? Thao tác này không thể hoàn tác.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    onClick = {
                        coroutineScope.launch {
                            val success = StorageManager.deleteFile(item.path)
                            if (success) {
                                Toast.makeText(context, "Đã xóa tập tin/thư mục", Toast.LENGTH_SHORT).show()
                                showDeleteConfirmDialog = false
                                selectedFileForActions = null
                                refreshFiles()
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
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Details Dialog
    if (showDetailsDialog && selectedFileForActions != null) {
        val item = selectedFileForActions!!
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text("Thông tin chi tiết", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow(label = "Tên", value = item.name)
                    DetailRow(label = "Đường dẫn", value = item.path)
                    DetailRow(label = "Kích thước", value = item.formattedSize)
                    DetailRow(label = "Cập nhật cuối", value = item.formattedDate)
                    DetailRow(label = "Loại", value = if (item.isDirectory) "Thư mục" else "Tập tin (${item.extension.uppercase()})")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDetailsDialog = false
                    selectedFileForActions = null
                }) {
                    Text("Đóng")
                }
            }
        )
    }

    // Create Folder Dialog
    if (showCreateFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Tạo thư mục mới", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("Tên thư mục...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.trim().isNotEmpty()) {
                            coroutineScope.launch {
                                val success = StorageManager.createDirectory(currentPath, folderName)
                                if (success) {
                                    Toast.makeText(context, "Đã tạo thư mục", Toast.LENGTH_SHORT).show()
                                    showCreateFolderDialog = false
                                    refreshFiles()
                                } else {
                                    Toast.makeText(context, "Tạo thư mục thất bại (có thể đã tồn tại)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Tạo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

fun handleItemClick(context: Context, item: FileItem, onNavigate: (NavKey) -> Unit) {
    if (item.isDirectory) {
        onNavigate(Explorer(path = item.path))
    } else {
        openFile(context, item)
    }
}

fun openFile(context: Context, fileItem: FileItem) {
    val file = File(fileItem.path)
    try {
        val uri = FileProvider.getUriForFile(context, "com.example.qexplorer.fileprovider", file)
        val mime = context.contentResolver.getType(uri) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Mở bằng"))
    } catch (e: Exception) {
        Toast.makeText(context, "Không thể mở tập tin: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

fun shareFile(context: Context, fileItem: FileItem) {
    val file = File(fileItem.path)
    try {
        val uri = FileProvider.getUriForFile(context, "com.example.qexplorer.fileprovider", file)
        val mime = context.contentResolver.getType(uri) ?: "*/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ tập tin"))
    } catch (e: Exception) {
        Toast.makeText(context, "Không thể chia sẻ tập tin", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun BreadcrumbNavigation(
    rootPath: String,
    currentPath: String,
    onBreadcrumbClick: (String) -> Unit
) {
    val breadcrumbs = remember(currentPath) {
        val list = mutableListOf<Pair<String, String>>()
        list.add(Pair("Bộ nhớ trong", rootPath))
        
        if (currentPath != rootPath && currentPath.startsWith(rootPath)) {
            val relativePath = currentPath.substring(rootPath.length).trim { it == '/' || it == '\\' }
            if (relativePath.isNotEmpty()) {
                val parts = relativePath.split(File.separatorChar)
                var accumulated = rootPath
                for (part in parts) {
                    accumulated += File.separator + part
                    list.add(Pair(part, accumulated))
                }
            }
        }
        list
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(breadcrumbs.size) { index ->
            val (name, path) = breadcrumbs[index]
            val isLast = index == breadcrumbs.size - 1

            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onBreadcrumbClick(path) }
                    .padding(vertical = 4.dp, horizontal = 6.dp)
            )

            if (!isLast) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun ActionRow(
    icon: ImageVector,
    title: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyStateView(isSearch: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSearch) Icons.Rounded.SearchOff else Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isSearch) "Không tìm thấy kết quả phù hợp" else "Thư mục này trống",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListFileItemView(
    item: FileItem,
    onClick: () -> Unit,
    onActionClick: () -> Unit
) {
    val fileIcon = getIconForFile(item)
    val iconColor = getIconColorForFile(item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onActionClick
            )
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                    text = item.formattedDate,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onActionClick) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridFileItemView(
    item: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val fileIcon = getIconForFile(item)
    val iconColor = getIconColorForFile(item)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = item.name,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = item.formattedSize,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun getIconForFile(item: FileItem): ImageVector {
    if (item.isDirectory) return Icons.Rounded.Folder
    val ext = item.extension.lowercase(Locale.getDefault())
    return when {
        StorageManager.imageExtensions.contains(ext) -> Icons.Rounded.Image
        StorageManager.videoExtensions.contains(ext) -> Icons.Rounded.PlayCircle
        StorageManager.audioExtensions.contains(ext) -> Icons.Rounded.MusicNote
        StorageManager.docExtensions.contains(ext) -> Icons.Rounded.Description
        StorageManager.apkExtensions.contains(ext) -> Icons.Rounded.Android
        else -> Icons.Rounded.InsertDriveFile
    }
}

fun getIconColorForFile(item: FileItem): Color {
    if (item.isDirectory) return Color(0xFF00ADB5) // Teal
    val ext = item.extension.lowercase(Locale.getDefault())
    return when {
        StorageManager.imageExtensions.contains(ext) -> Color(0xFF00ADB5)
        StorageManager.videoExtensions.contains(ext) -> Color(0xFF8A2BE2)
        StorageManager.audioExtensions.contains(ext) -> Color(0xFFE91E63)
        StorageManager.docExtensions.contains(ext) -> Color(0xFFFF9800)
        StorageManager.apkExtensions.contains(ext) -> Color(0xFF4CAF50)
        else -> Color(0xFF6B7280) // Gray
    }
}

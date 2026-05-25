package com.example.qexplorer.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qexplorer.data.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiManagerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Detect connected SSID
    val detectedSsid = remember {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            if (info != null && info.ssid != "<unknown ssid>") {
                info.ssid.trim('"')
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    var ssid by remember { mutableStateOf(detectedSsid) }
    var password by remember { mutableStateOf("") }
    var security by remember { mutableStateOf("WPA") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Saved WiFi Lists states
    var localHistory by remember { mutableStateOf<List<StorageManager.WifiNetwork>>(emptyList()) }
    var savedNetworksRoot by remember { mutableStateOf<List<StorageManager.WifiNetwork>>(emptyList()) }
    var isLoadingRoot by remember { mutableStateOf(false) }
    var hasAttemptedRoot by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0: Local History, 1: Root System

    fun loadLocalHistory() {
        localHistory = getWifiHistory(context)
    }

    fun refreshSavedNetworksRoot() {
        coroutineScope.launch {
            isLoadingRoot = true
            hasAttemptedRoot = true
            savedNetworksRoot = StorageManager.getSavedWifiPasswords()
            isLoadingRoot = false
            if (savedNetworksRoot.isEmpty()) {
                Toast.makeText(context, "Không thể lấy cấu hình Root (Thiết bị chưa root)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadLocalHistory()
    }

    // QR Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val contentResolver = context.contentResolver
                val qrText = withContext(Dispatchers.IO) {
                    try {
                        contentResolver.openInputStream(uri).use { inputStream ->
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            if (bitmap != null) {
                                decodeQrCode(bitmap)
                            } else null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                if (qrText != null) {
                    val (scannedSsid, scannedPsk, scannedSec) = parseWifiQrContent(qrText)
                    if (scannedSsid.isNotEmpty()) {
                        ssid = scannedSsid
                        password = scannedPsk
                        security = scannedSec
                        qrBitmap = StorageManager.generateWifiQrCode(scannedSsid, scannedPsk, scannedSec)
                        
                        // Save to local history
                        saveWifiToHistory(context, scannedSsid, scannedPsk, scannedSec)
                        loadLocalHistory()
                        
                        Toast.makeText(context, "Đã đọc thành công Wifi: $scannedSsid", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Mã QR không đúng định dạng WiFi", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Không tìm thấy mã QR WiFi trong hình ảnh này", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quản lý mật khẩu Wifi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Section 1: Manual Wifi QR Code Generator & QR Import
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tạo mã QR kết nối nhanh",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            // Import from QR Image Button
                            TextButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Quét từ ảnh thư viện", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = ssid,
                            onValueChange = { ssid = it },
                            label = { Text("Tên mạng Wifi (SSID)") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Mật khẩu") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Rounded.VpnKey, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Security selector options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            listOf("WPA", "WEP", "nopass").forEach { sec ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { security = sec }
                                ) {
                                    RadioButton(selected = security == sec, onClick = { security = sec })
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (sec) {
                                            "nopass" -> "Không mật khẩu"
                                            else -> sec
                                        },
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (ssid.trim().isNotEmpty()) {
                                    qrBitmap = StorageManager.generateWifiQrCode(ssid, password, security)
                                    if (qrBitmap != null) {
                                        // Save to history
                                        saveWifiToHistory(context, ssid, password, security)
                                        loadLocalHistory()
                                    } else {
                                        Toast.makeText(context, "Không thể tạo QR", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Vui lòng nhập tên Wifi", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.QrCode, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tạo mã QR & Lưu lịch sử")
                            }
                        }

                        // Display QR Code
                        AnimatedVisibility(
                            visible = qrBitmap != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            qrBitmap?.let { bmp ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(200.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.White)
                                            .padding(16.dp)
                                    ) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "WiFi QR Code",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Đưa camera điện thoại khác quét mã này để tự động kết nối nhanh!",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Tabs for Saved Networks
            item {
                TabRow(
                    selectedTabIndex = activeTab,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("Đã lưu (Cục bộ)", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("Hệ thống (Root)", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Section 3: Render Lists based on Active Tab
            if (activeTab == 0) {
                // Local History
                if (localHistory.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Chưa có lịch sử WiFi nào", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                Text("Nhập từ QR hoặc tạo QR để lưu vào lịch sử", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    items(localHistory) { net ->
                        SavedWifiRow(
                            network = net,
                            showDelete = true,
                            onDelete = {
                                deleteWifiFromHistory(context, net.ssid)
                                loadLocalHistory()
                            },
                            onCopy = {
                                copyToClipboard(context, net.preSharedKey)
                                Toast.makeText(context, "Đã sao chép mật khẩu Wifi", Toast.LENGTH_SHORT).show()
                            },
                            onGenerateQr = {
                                ssid = net.ssid
                                password = net.preSharedKey
                                security = net.security
                                qrBitmap = StorageManager.generateWifiQrCode(net.ssid, net.preSharedKey, net.security)
                                Toast.makeText(context, "Đã hiển thị QR cho ${net.ssid}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            } else {
                // Root system networks
                if (!hasAttemptedRoot) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = { refreshSavedNetworksRoot() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Security, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Quét hệ thống (Yêu cầu Root)")
                                }
                            }
                        }
                    }
                } else if (isLoadingRoot) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (savedNetworksRoot.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Thiết bị chưa root hoặc không thể truy cập cấu hình hệ thống. Bạn hãy sử dụng tab 'Đã lưu (Cục bộ)' để tạo và lưu mật khẩu WiFi.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(savedNetworksRoot) { net ->
                        SavedWifiRow(
                            network = net,
                            showDelete = false,
                            onDelete = {},
                            onCopy = {
                                copyToClipboard(context, net.preSharedKey)
                                Toast.makeText(context, "Đã sao chép mật khẩu Wifi", Toast.LENGTH_SHORT).show()
                            },
                            onGenerateQr = {
                                ssid = net.ssid
                                password = net.preSharedKey
                                security = net.security
                                qrBitmap = StorageManager.generateWifiQrCode(net.ssid, net.preSharedKey, net.security)
                                Toast.makeText(context, "Đã hiển thị QR cho ${net.ssid}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedWifiRow(
    network: StorageManager.WifiNetwork,
    showDelete: Boolean,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onGenerateQr: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = network.ssid,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (network.preSharedKey.isEmpty()) "Không có mật khẩu" else "Mật khẩu: ${network.preSharedKey}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onGenerateQr) {
                    Icon(
                        imageVector = Icons.Rounded.QrCode,
                        contentDescription = "Mã QR",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Sao chép",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Xóa lịch sử",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = android.content.ClipData.newPlainText("Wifi Password", text)
    clipboard.setPrimaryClip(clip)
}

// QR Decoder from bitmap using ZXing
fun decodeQrCode(bitmap: Bitmap): String? {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
    val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
    return try {
        val result = com.google.zxing.MultiFormatReader().decode(binaryBitmap)
        result.text
    } catch (e: Exception) {
        null
    }
}

// Parse WiFi QR: WIFI:T:WPA;S:MySSID;P:myPassword;;
fun parseWifiQrContent(content: String): Triple<String, String, String> {
    if (!content.startsWith("WIFI:", ignoreCase = true)) {
        return Triple("", "", "")
    }
    val temp = content.substring(5)
    val ssid = getValueForField("S:", temp)
    val psk = getValueForField("P:", temp)
    val sec = getValueForField("T:", temp).ifEmpty { "WPA" }
    return Triple(ssid, psk, sec)
}

private fun getValueForField(field: String, content: String): String {
    val index = content.indexOf(field)
    if (index == -1) return ""
    val start = index + field.length
    var end = content.indexOf(";", start)
    if (end == -1) end = content.length
    return content.substring(start, end)
        .replace("\\;", ";")
        .replace("\\:", ":")
        .replace("\\\\", "\\")
        .trim('"')
}

// SharedPreferences Wi-Fi History helpers
fun saveWifiToHistory(context: Context, ssid: String, psk: String, security: String) {
    val prefs = context.getSharedPreferences("qexplorer_wifi_history", Context.MODE_PRIVATE)
    val raw = prefs.getString("history", "") ?: ""
    val list = raw.split("\u001E").filter { it.isNotEmpty() }.toMutableList()
    
    // Remove if SSID already exists in history to update it
    list.removeAll { it.split("\u001F").firstOrNull() == ssid }
    
    val record = "$ssid\u001F$psk\u001F$security"
    list.add(0, record)
    
    prefs.edit().putString("history", list.joinToString("\u001E")).apply()
}

fun getWifiHistory(context: Context): List<StorageManager.WifiNetwork> {
    val prefs = context.getSharedPreferences("qexplorer_wifi_history", Context.MODE_PRIVATE)
    val raw = prefs.getString("history", "") ?: ""
    if (raw.isEmpty()) return emptyList()
    return raw.split("\u001E").filter { it.isNotEmpty() }.mapNotNull { record ->
        val parts = record.split("\u001F")
        if (parts.size >= 3) {
            StorageManager.WifiNetwork(parts[0], parts[1], parts[2])
        } else null
    }
}

fun deleteWifiFromHistory(context: Context, ssid: String) {
    val prefs = context.getSharedPreferences("qexplorer_wifi_history", Context.MODE_PRIVATE)
    val raw = prefs.getString("history", "") ?: ""
    val list = raw.split("\u001E").filter { it.isNotEmpty() }.toMutableList()
    list.removeAll { it.split("\u001F").firstOrNull() == ssid }
    prefs.edit().putString("history", list.joinToString("\u001E")).apply()
}

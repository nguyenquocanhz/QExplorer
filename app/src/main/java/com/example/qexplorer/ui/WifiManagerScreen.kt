package com.example.qexplorer.ui

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.launch

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

    // Root Config Recovery States
    var savedNetworks by remember { mutableStateOf<List<StorageManager.WifiNetwork>>(emptyList()) }
    var isLoadingRoot by remember { mutableStateOf(false) }
    var hasAttemptedRoot by remember { mutableStateOf(false) }

    fun refreshSavedNetworks() {
        coroutineScope.launch {
            isLoadingRoot = true
            hasAttemptedRoot = true
            savedNetworks = StorageManager.getSavedWifiPasswords()
            isLoadingRoot = false
            if (savedNetworks.isEmpty()) {
                Toast.makeText(context, "Không thể lấy cấu hình root", Toast.LENGTH_SHORT).show()
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
            // Section 1: Manual Wifi QR Code Generator
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Tạo mã QR kết nối nhanh",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

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
                                    if (qrBitmap == null) {
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
                                Text("Tạo mã QR")
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

            // Section 2: Root WiFi Saved Networks Recovery
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
                            Column {
                                Text(
                                    text = "Xem mật khẩu đã lưu",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Yêu cầu thiết bị đã ROOT",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isLoadingRoot) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = { refreshSavedNetworks() }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Quét mạng", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (!hasAttemptedRoot) {
                            Button(
                                onClick = { refreshSavedNetworks() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Kiểm tra & Quét thiết bị")
                            }
                        } else if (savedNetworks.isEmpty()) {
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
                                    text = "Không tìm thấy cấu hình hoặc thiết bị chưa Root. Bạn vẫn có thể tạo mã QR thủ công ở trên để chia sẻ.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // List of saved networks (if root successfully loaded them)
            if (savedNetworks.isNotEmpty()) {
                items(savedNetworks) { net ->
                    SavedWifiRow(
                        network = net,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Wifi Password", net.preSharedKey)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Đã sao chép mật khẩu Wifi", Toast.LENGTH_SHORT).show()
                        },
                        onGenerateQr = {
                            ssid = net.ssid
                            password = net.preSharedKey
                            security = net.security
                            qrBitmap = StorageManager.generateWifiQrCode(net.ssid, net.preSharedKey, net.security)
                            Toast.makeText(context, "Đã tạo QR cho ${net.ssid}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SavedWifiRow(
    network: StorageManager.WifiNetwork,
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
                    text = "Mật khẩu: ${network.preSharedKey}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }
        }
    }
}

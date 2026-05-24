package com.example.qexplorer.data

import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val fileCount: Int = 0,
    val extension: String = ""
) {
    val formattedSize: String
        get() = if (isDirectory) "$fileCount items" else formatSize(size)

    val formattedDate: String
        get() = java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.SHORT,
            java.text.DateFormat.SHORT
        ).format(java.util.Date(lastModified))
}

data class StorageInfo(
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val usedPercentage: Float
) {
    val formattedTotal: String get() = formatSize(totalSpace)
    val formattedFree: String get() = formatSize(freeSpace)
    val formattedUsed: String get() = formatSize(usedSpace)
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val clampedGroups = digitGroups.coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, clampedGroups.toDouble()), units[clampedGroups])
}

object StorageManager {

    val rootPath: String
        get() = Environment.getExternalStorageDirectory().absolutePath

    fun getStorageInfo(): StorageInfo {
        return try {
            val stat = StatFs(rootPath)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val used = total - free
            val pct = if (total > 0) (used.toFloat() / total.toFloat()) * 100f else 0f

            StorageInfo(total, free, used, pct)
        } catch (e: Exception) {
            StorageInfo(0, 0, 0, 0f)
        }
    }

    suspend fun getFiles(dirPath: String, sortBy: String = "name", sortAsc: Boolean = true): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        val files = dir.listFiles() ?: return@withContext emptyList()
        val list = files.map { file ->
            val count = if (file.isDirectory) {
                file.listFiles()?.size ?: 0
            } else {
                0
            }
            FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0 else file.length(),
                lastModified = file.lastModified(),
                fileCount = count,
                extension = if (file.isDirectory) "" else file.extension.lowercase(Locale.getDefault())
            )
        }

        val comparator = when (sortBy) {
            "size" -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.size }
            "date" -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.lastModified }
            "type" -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.extension }
            else -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
        }

        val sorted = list.sortedWith(comparator)
        val finalSorted = if (sortAsc) sorted else sorted.reversed()
        
        // Group: directories always first
        val (dirs, normalFiles) = finalSorted.partition { it.isDirectory }
        dirs + normalFiles
    }

    // Classify extensions
    val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    val videoExtensions = setOf("mp4", "mkv", "avi", "3gp", "webm", "flv")
    val audioExtensions = setOf("mp3", "wav", "ogg", "m4a", "flac", "wma")
    val docExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "zip", "rar")
    val apkExtensions = setOf("apk")

    data class CategoryStats(
        val imagesSize: Long = 0,
        val imagesCount: Int = 0,
        val videosSize: Long = 0,
        val videosCount: Int = 0,
        val audiosSize: Long = 0,
        val audiosCount: Int = 0,
        val docsSize: Long = 0,
        val docsCount: Int = 0,
        val apksSize: Long = 0,
        val apksCount: Int = 0,
        val downloadsSize: Long = 0,
        val downloadsCount: Int = 0
    )

    suspend fun getCategoryStats(): CategoryStats = withContext(Dispatchers.IO) {
        var imagesSize = 0L; var imagesCount = 0
        var videosSize = 0L; var videosCount = 0
        var audiosSize = 0L; var audiosCount = 0
        var docsSize = 0L; var docsCount = 0
        var apksSize = 0L; var apksCount = 0
        var downloadsSize = 0L; var downloadsCount = 0

        val root = File(rootPath)
        val downloadFolder = File(root, "Download")

        // Quick traversal to keep UX snappy (depth limit 3)
        fun traverse(file: File, depth: Int = 0) {
            if (depth > 3) return
            val files = file.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    if (f.name.equals("Android", ignoreCase = true) || f.name.startsWith(".")) continue
                    traverse(f, depth + 1)
                } else {
                    val size = f.length()
                    val ext = f.extension.lowercase(Locale.getDefault())

                    if (f.absolutePath.startsWith(downloadFolder.absolutePath)) {
                        downloadsSize += size
                        downloadsCount++
                    }

                    when {
                        imageExtensions.contains(ext) -> {
                            imagesSize += size
                            imagesCount++
                        }
                        videoExtensions.contains(ext) -> {
                            videosSize += size
                            videosCount++
                        }
                        audioExtensions.contains(ext) -> {
                            audiosSize += size
                            audiosCount++
                        }
                        docExtensions.contains(ext) -> {
                            docsSize += size
                            docsCount++
                        }
                        apkExtensions.contains(ext) -> {
                            apksSize += size
                            apksCount++
                        }
                    }
                }
            }
        }

        if (root.exists() && root.isDirectory) {
            traverse(root)
        }

        CategoryStats(
            imagesSize = imagesSize, imagesCount = imagesCount,
            videosSize = videosSize, videosCount = videosCount,
            audiosSize = audiosSize, audiosCount = audiosCount,
            docsSize = docsSize, docsCount = docsCount,
            apksSize = apksSize, apksCount = apksCount,
            downloadsSize = downloadsSize, downloadsCount = downloadsCount
        )
    }

    suspend fun getCategoryFiles(category: String): List<FileItem> = withContext(Dispatchers.IO) {
        val root = File(rootPath)
        val list = mutableListOf<File>()
        val downloadFolder = File(root, "Download")

        fun traverse(file: File, depth: Int = 0) {
            if (depth > 3) return
            val files = file.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    if (f.name.equals("Android", ignoreCase = true) || f.name.startsWith(".")) continue
                    traverse(f, depth + 1)
                } else {
                    val ext = f.extension.lowercase(Locale.getDefault())
                    val isMatch = when (category.lowercase()) {
                        "images" -> imageExtensions.contains(ext)
                        "videos" -> videoExtensions.contains(ext)
                        "audio" -> audioExtensions.contains(ext)
                        "documents" -> docExtensions.contains(ext)
                        "apks" -> apkExtensions.contains(ext)
                        "downloads" -> f.absolutePath.startsWith(downloadFolder.absolutePath)
                        else -> false
                    }
                    if (isMatch) {
                        list.add(f)
                    }
                }
            }
        }

        traverse(root)
        list.map { f ->
            FileItem(
                name = f.name,
                path = f.absolutePath,
                isDirectory = false,
                size = f.length(),
                lastModified = f.lastModified(),
                extension = f.extension.lowercase(Locale.getDefault())
            )
        }.sortedByDescending { it.lastModified }
    }

    // Helper functions for file operations
    suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        return@withContext if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }

    suspend fun renameFile(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext false
        val parent = file.parentFile ?: return@withContext false
        val newFile = File(parent, newName)
        return@withContext file.renameTo(newFile)
    }

    suspend fun createDirectory(parentPath: String, folderName: String): Boolean = withContext(Dispatchers.IO) {
        val folder = File(parentPath, folderName)
        if (folder.exists()) return@withContext false
        return@withContext folder.mkdirs()
    }

    // Zip Compression
    suspend fun zip(sourcePath: String, destZipPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourcePath)
            val zipFile = File(destZipPath)
            java.io.FileOutputStream(zipFile).use { fos ->
                java.util.zip.ZipOutputStream(fos).use { zos ->
                    zipFileOrDirectory(sourceFile, sourceFile.name, zos)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun zipFileOrDirectory(fileToZip: File, fileName: String, zos: java.util.zip.ZipOutputStream) {
        if (fileToZip.isHidden) return
        if (fileToZip.isDirectory) {
            val children = fileToZip.listFiles() ?: return
            if (children.isEmpty()) {
                val entry = java.util.zip.ZipEntry(if (fileName.endsWith("/")) fileName else "$fileName/")
                zos.putNextEntry(entry)
                zos.closeEntry()
                return
            }
            for (childFile in children) {
                zipFileOrDirectory(childFile, "$fileName/${childFile.name}", zos)
            }
        } else {
            java.io.FileInputStream(fileToZip).use { fis ->
                val entry = java.util.zip.ZipEntry(fileName)
                zos.putNextEntry(entry)
                val buffer = ByteArray(2048)
                var length: Int
                while (fis.read(buffer).also { length = it } >= 0) {
                    zos.write(buffer, 0, length)
                }
                zos.closeEntry()
            }
        }
    }

    // Zip Decompression
    suspend fun unzip(zipFilePath: String, destDirectoryPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val destDir = File(destDirectoryPath)
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            java.io.FileInputStream(zipFilePath).use { fis ->
                java.util.zip.ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val filePath = destDirectoryPath + File.separator + entry.name
                        if (!entry.isDirectory) {
                            val file = File(filePath)
                            val parentDir = file.parentFile
                            if (parentDir != null && !parentDir.exists()) {
                                parentDir.mkdirs()
                            }
                            java.io.FileOutputStream(filePath).use { fos ->
                                val buffer = ByteArray(2048)
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        } else {
                            val dir = File(filePath)
                            dir.mkdirs()
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Text Editor Core
    suspend fun readTextFile(path: String): String = withContext(Dispatchers.IO) {
        try {
            File(path).readText(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun writeTextFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(path).writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    // Granular format stats helper
    suspend fun getDetailedExtensionStats(): List<Pair<String, Long>> = withContext(Dispatchers.IO) {
        val extMap = mutableMapOf<String, Long>()
        val root = File(rootPath)

        fun traverse(file: File, depth: Int = 0) {
            if (depth > 3) return
            val files = file.listFiles() ?: return
            for (f in files) {
                if (f.isDirectory) {
                    if (f.name.equals("Android", ignoreCase = true) || f.name.startsWith(".")) continue
                    traverse(f, depth + 1)
                } else {
                    val ext = f.extension.lowercase(Locale.getDefault())
                    if (ext.isNotEmpty()) {
                        extMap[ext] = (extMap[ext] ?: 0L) + f.length()
                    }
                }
            }
        }

        if (root.exists() && root.isDirectory) {
            traverse(root)
        }

        // Return top 6 file types by size
        extMap.toList().sortedByDescending { it.second }.take(6)
    }

    // Clipboard State
    data class ClipboardItem(val file: FileItem, val isCut: Boolean)
    private val _clipboard = kotlinx.coroutines.flow.MutableStateFlow<ClipboardItem?>(null)
    val clipboard: kotlinx.coroutines.flow.StateFlow<ClipboardItem?> = _clipboard

    fun setClipboard(file: FileItem, isCut: Boolean) {
        _clipboard.value = ClipboardItem(file, isCut)
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    // Copy File or Directory
    suspend fun copyFileOrDirectory(sourcePath: String, destDirPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            val destDir = File(destDirPath)
            if (!destDir.exists()) destDir.mkdirs()
            val dest = File(destDir, source.name)
            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = true)
            } else {
                source.copyTo(dest, overwrite = true)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Move File or Directory
    suspend fun moveFileOrDirectory(sourcePath: String, destDirPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val source = File(sourcePath)
            val destDir = File(destDirPath)
            if (!destDir.exists()) destDir.mkdirs()
            val dest = File(destDir, source.name)
            source.renameTo(dest)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // WiFi Config details model
    data class WifiNetwork(val ssid: String, val preSharedKey: String, val security: String = "WPA")

    // Parse WiFi configs (requires root)
    suspend fun getSavedWifiPasswords(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        val list = mutableListOf<WifiNetwork>()
        try {
            // Android 9+ WifiConfigStore.xml
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/misc/wifi/WifiConfigStore.xml"))
            val reader = process.inputStream.bufferedReader()
            var line = reader.readLine()
            var currentSSID = ""
            var currentPassword = ""
            while (line != null) {
                if (line.contains("name=\"SSID\"")) {
                    currentSSID = line.substringAfter("value=\"").substringBefore("\"").trim('"')
                }
                if (line.contains("name=\"PreSharedKey\"")) {
                    currentPassword = line.substringAfter("value=\"").substringBefore("\"").trim('"')
                    if (currentSSID.isNotEmpty()) {
                        list.add(WifiNetwork(currentSSID, currentPassword))
                        currentSSID = ""
                        currentPassword = ""
                    }
                }
                line = reader.readLine()
            }
            process.destroy()
        } catch (e: Exception) {
            // Root failed or file missing
        }

        if (list.isEmpty()) {
            try {
                // Older devices wpa_supplicant.conf
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/misc/wifi/wpa_supplicant.conf"))
                val reader = process.inputStream.bufferedReader()
                var line = reader.readLine()
                var currentSSID = ""
                var currentPassword = ""
                while (line != null) {
                    if (line.contains("ssid=")) {
                        currentSSID = line.substringAfter("ssid=\"").substringBefore("\"").trim('"')
                    }
                    if (line.contains("psk=")) {
                        currentPassword = line.substringAfter("psk=\"").substringBefore("\"").trim('"')
                        if (currentSSID.isNotEmpty() && currentPassword.isNotEmpty()) {
                            list.add(WifiNetwork(currentSSID, currentPassword))
                            currentSSID = ""
                            currentPassword = ""
                        }
                    }
                    line = reader.readLine()
                }
                process.destroy()
            } catch (e: Exception) {
                // Root failed
            }
        }
        list.distinctBy { it.ssid }
    }

    // Generate offline WiFi QR code
    fun generateWifiQrCode(ssid: String, psk: String, security: String): android.graphics.Bitmap? {
        val formatContent = "WIFI:T:$security;S:$ssid;P:$psk;;"
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(formatContent, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

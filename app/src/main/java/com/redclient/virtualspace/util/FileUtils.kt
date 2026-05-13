package com.redclient.virtualspace.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat

object FileUtils {

    fun copyUriToFile(context: Context, uri: Uri, destFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        name = cursor.getString(idx)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        var size: Long = -1
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) {
                        size = cursor.getLong(idx)
                    }
                }
            }
        }
        if (size < 0) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    size = it.statSize
                }
            } catch (_: Exception) {}
        }
        return size
    }

    fun getMimeType(context: Context, uri: Uri): String? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.getType(uri)
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            )
        }
    }

    fun isApkFile(name: String): Boolean =
        name.endsWith(".apk", ignoreCase = true)

    fun isXapkFile(name: String): Boolean =
        name.endsWith(".xapk", ignoreCase = true)

    fun formatFileSize(bytes: Long): String {
        val df = DecimalFormat("0.00")
        return when {
            bytes >= 1_073_741_824 -> "${df.format(bytes / 1_073_741_824.0)} GB"
            bytes >= 1_048_576 -> "${df.format(bytes / 1_048_576.0)} MB"
            bytes >= 1_024 -> "${df.format(bytes / 1_024.0)} KB"
            else -> "$bytes B"
        }
    }

    fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return file.delete()
    }

    fun ensureDir(dir: File): Boolean {
        return dir.exists() || dir.mkdirs()
    }
}

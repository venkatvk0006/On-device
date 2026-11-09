package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val TAG = "ImageUtil"

object ImageUtil {

    /**
     * Loads a downscaled Bitmap from Uri suitable for Gemini multimodal input (keeping it under 2MB in memory).
     */
    suspend fun loadScaledBitmap(context: Context, uri: Uri, maxDimension: Int = 1024): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                } ?: return@withContext null

                var inSampleSize = 1
                if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                        inSampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode bitmap from uri: $uri", e)
                null
            }
        }

    /**
     * Saves a temporary local copy of the picked image to internal storage so it can be previewed later.
     */
    suspend fun saveImageLocally(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image to internal files", e)
            null
        }
    }
}

package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageUploadHelper {
    private const val MAX_DIMENSION_PX = 1280
    private const val JPEG_QUALITY = 85

    suspend fun readCompressedJpeg(context: Context, uri: Uri): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to open selected image")
                if (rawBytes.isEmpty()) error("Selected image is empty")

                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)

                if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                    // Fallback if platform decoder cannot parse bounds
                    return@runCatching rawBytes
                }

                var sampleSize = 1
                while (
                    (boundsOpts.outWidth / sampleSize) > MAX_DIMENSION_PX ||
                    (boundsOpts.outHeight / sampleSize) > MAX_DIMENSION_PX
                ) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                    ?: return@runCatching rawBytes

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
                bitmap.recycle()
                baos.toByteArray()
            }
        }
}

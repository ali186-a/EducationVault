package com.educationvault.app.docs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shrinks camera photos (often 4-8 MB) so they fit the upload limit and upload quickly. */
object ImageCompressor {
    const val MAX_DIM = 2400
    const val QUALITY = 85

    /** Largest power-of-two sampling that still leaves the picture at least [max] pixels wide/high. */
    fun sampleSizeFor(w: Int, h: Int, max: Int = MAX_DIM): Int {
        val longest = maxOf(w, h)
        var s = 1
        while (longest / (s * 2) >= max) s *= 2
        return s
    }

    fun targetSize(w: Int, h: Int, max: Int = MAX_DIM): Pair<Int, Int> {
        val longest = maxOf(w, h)
        if (longest <= max) return w to h
        val f = max.toDouble() / longest
        return maxOf(1, Math.round(w * f).toInt()) to maxOf(1, Math.round(h * f).toInt())
    }

    fun cameraFileName(millis: Long): String =
        "scan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(millis)) + ".jpg"

    /** JPEG bytes of the photo, rotated upright and scaled down; null if it cannot be decoded. */
    fun compressJpeg(file: File): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
            var bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null

            val degrees = when (
                ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
            }
            val (tw, th) = targetSize(bmp.width, bmp.height)
            if (tw != bmp.width || th != bmp.height) bmp = Bitmap.createScaledBitmap(bmp, tw, th, true)

            val out = ByteArrayOutputStream()
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) return null
            out.toByteArray()
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}

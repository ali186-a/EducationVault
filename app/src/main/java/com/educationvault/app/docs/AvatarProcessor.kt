package com.educationvault.app.docs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream

class ProcessedAvatar(val jpeg: ByteArray, val bitmap: Bitmap)

/** Turns any picked photo into a small square JPEG (256x256, about 15-40 KB) for the profile picture. */
object AvatarProcessor {
    const val SIZE = 256
    const val QUALITY = 85

    /** Biggest centered square inside a w x h picture: (left, top, side). */
    fun centerSquare(w: Int, h: Int): Triple<Int, Int, Int> {
        val side = minOf(w, h)
        return Triple((w - side) / 2, (h - side) / 2, side)
    }

    fun process(source: ByteArray): ProcessedAvatar? {
        return try {
            val full = PreviewRenderer.decodeImage(source) ?: return null
            val (x, y, side) = centerSquare(full.width, full.height)
            if (side <= 0) return null
            val square = Bitmap.createBitmap(full, x, y, side, side)
            val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
            // JPEG has no transparency: put PNGs with see-through areas on white
            val flat = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(scaled, 0f, 0f, null)
            }
            val out = ByteArrayOutputStream()
            if (!flat.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) return null
            ProcessedAvatar(out.toByteArray(), flat)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}

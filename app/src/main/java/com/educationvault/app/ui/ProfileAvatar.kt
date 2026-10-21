package com.educationvault.app.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.educationvault.app.auth.UserProfile
import com.educationvault.app.docs.FileSupport
import com.educationvault.app.docs.PreviewRenderer
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Downloads a small profile picture (HTTPS only, max 2 MB) without any extra library. */
object AvatarLoader {
    private val cache = LruCache<String, Bitmap>(8)

    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        val bmp = withContext(Dispatchers.IO) { download(url) }
        if (bmp != null) cache.put(url, bmp)
        return bmp
    }

    private fun download(url: String): Bitmap? {
        if (!url.startsWith("https://")) return null
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                if (conn.responseCode !in 200..299) return null
                val bytes = conn.inputStream.use { FileSupport.readLimited(it, 2L * 1024 * 1024) } ?: return null
                PreviewRenderer.decodeImage(bytes)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** Round profile picture; shows the first letter of the name (or email) when there is no picture. */
@Composable
fun ProfileAvatar(profile: UserProfile, email: String, size: Dp, modifier: Modifier = Modifier) {
    val url = profile.photoUrl
    val bitmap by produceState<Bitmap?>(initialValue = url?.let { AvatarLoader.cached(it) }, url) {
        value = if (url == null) null else AvatarLoader.load(url)
    }
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val b = bitmap
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(bitmap = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                profile.initial(email),
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

package com.educationvault.app.auth

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** Shows which signing key this installed app has (Google sign-in is tied to its SHA-1). */
object AppSignature {
    fun hex(bytes: ByteArray): String = bytes.joinToString(":") { String.format("%02X", it.toInt() and 0xFF) }

    @Suppress("DEPRECATION")
    fun sha1(context: Context): String? = try {
        val pm = context.packageManager
        val signature = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }
        signature?.let { hex(MessageDigest.getInstance("SHA-1").digest(it.toByteArray())) }
    } catch (e: Exception) {
        null
    }
}

package com.passwordmanager.android.data

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import com.passwordmanager.util.FaviconService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FaviconRepository @Inject constructor(
    private val faviconService: FaviconService
) {
    suspend fun getFavicon(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bytes = faviconService.getFavicon(url) ?: return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getCachedFavicon(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val bytes = faviconService.getCachedFavicon(url) ?: return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}

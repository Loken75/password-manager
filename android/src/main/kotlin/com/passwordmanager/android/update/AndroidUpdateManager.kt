package com.passwordmanager.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.passwordmanager.update.UpdateChecker
import com.passwordmanager.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of an update check.
 */
sealed class UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult()
    data object UpToDate : UpdateResult()
    data class Error(val message: String?) : UpdateResult()
}

/**
 * Manages update checks for the Android app.
 * Opens the browser to the GitHub release page when an update is available.
 */
object AndroidUpdateManager {

    private val checker = UpdateChecker()

    /**
     * Checks for updates on a background thread.
     */
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val info = checker.checkForUpdate()
            if (info != null) UpdateResult.Available(info) else UpdateResult.UpToDate
        } catch (e: Exception) {
            UpdateResult.Error(e.message)
        }
    }

    /**
     * Opens the release page in the default browser.
     */
    fun openReleasePage(context: Context, info: UpdateInfo) {
        val url = info.releaseNotesUrl ?: return
        if (!url.startsWith("https://github.com/")) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

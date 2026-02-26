package com.passwordmanager.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.passwordmanager.update.UpdateChecker
import com.passwordmanager.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages update checks for the Android app.
 * Opens the browser to the GitHub release page when an update is available.
 */
object AndroidUpdateManager {

    private val checker = UpdateChecker()

    /**
     * Checks for updates on a background thread.
     * @return UpdateInfo if a newer version is available, null otherwise
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            checker.checkForUpdate()
        } catch (e: Exception) {
            null
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

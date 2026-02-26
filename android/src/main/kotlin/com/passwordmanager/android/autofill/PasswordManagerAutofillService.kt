package com.passwordmanager.android.autofill

import android.app.assist.AssistStructure
import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.*
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.passwordmanager.android.MainActivity
import com.passwordmanager.android.R
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.vault.VaultEntry

class PasswordManagerAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: run {
            callback.onSuccess(null)
            return
        }

        val fields = parseStructure(structure)
        if (fields.usernameId == null && fields.passwordId == null) {
            callback.onSuccess(null)
            return
        }

        // If vault is locked, return an authentication response
        if (!SessionHolder.isUnlocked()) {
            val authIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, authIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
                setTextViewText(R.id.autofill_text, getString(R.string.autofill_unlock_prompt))
            }
            val response = FillResponse.Builder()
                .setAuthentication(
                    arrayOfNulls<AutofillId>(0),
                    pendingIntent.intentSender,
                    presentation
                )
                .build()
            callback.onSuccess(response)
            return
        }

        // Find matching entries
        val service = SessionHolder.vaultService ?: run {
            callback.onSuccess(null)
            return
        }

        val domain = fields.webDomain ?: fields.packageName ?: ""
        val entries = service.search("").filter { entry ->
            matchesEntry(entry, domain)
        }.take(5)

        if (entries.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        val responseBuilder = FillResponse.Builder()
        for (entry in entries) {
            val datasetBuilder = Dataset.Builder()
            val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
                setTextViewText(R.id.autofill_text, entry.title ?: entry.username ?: "")
            }

            fields.usernameId?.let { id ->
                val value = entry.username ?: entry.email ?: ""
                datasetBuilder.setValue(id, AutofillValue.forText(value), presentation)
            }

            fields.passwordId?.let { id ->
                val pwd = entry.password
                if (pwd != null) {
                    // AutofillValue.forText() requires CharSequence — String conversion unavoidable.
                    // The char[] clone from getPassword() is wiped after use.
                    val pwdText = String(pwd)
                    datasetBuilder.setValue(id, AutofillValue.forText(pwdText), presentation)
                    com.passwordmanager.util.SecureWiper.wipe(pwd)
                }
            }

            try {
                responseBuilder.addDataset(datasetBuilder.build())
            } catch (_: Exception) {
                // Skip datasets that couldn't be built
            }
        }

        try {
            callback.onSuccess(responseBuilder.build())
        } catch (_: Exception) {
            callback.onSuccess(null)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // Don't auto-capture credentials
        callback.onSuccess()
    }

    private fun matchesEntry(entry: VaultEntry, domain: String): Boolean {
        if (domain.isBlank()) return false
        val entryUrl = entry.url?.lowercase() ?: return false
        val domainLower = domain.lowercase()
        return entryUrl.contains(domainLower) || domainLower.contains(extractDomain(entryUrl))
    }

    private fun extractDomain(url: String): String {
        var domain = url
        val protoIdx = domain.indexOf("://")
        if (protoIdx >= 0) domain = domain.substring(protoIdx + 3)
        val pathIdx = domain.indexOf('/')
        if (pathIdx >= 0) domain = domain.substring(0, pathIdx)
        val portIdx = domain.indexOf(':')
        if (portIdx >= 0) domain = domain.substring(0, portIdx)
        return domain
    }

    private data class ParsedFields(
        val usernameId: AutofillId? = null,
        val passwordId: AutofillId? = null,
        val webDomain: String? = null,
        val packageName: String? = null
    )

    private fun parseStructure(structure: AssistStructure): ParsedFields {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var webDomain: String? = null
        var appPackage: String? = null

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val rootNode = windowNode.rootViewNode
            traverseNode(rootNode) { node ->
                if (node.webDomain != null) webDomain = node.webDomain
                if (node.idPackage != null) appPackage = node.idPackage

                val hints = node.autofillHints
                if (hints != null) {
                    for (hint in hints) {
                        when {
                            hint.contains("username", true) ||
                            hint.contains("email", true) ||
                            hint == android.view.View.AUTOFILL_HINT_USERNAME ||
                            hint == android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS -> {
                                if (usernameId == null) usernameId = node.autofillId
                            }
                            hint.contains("password", true) ||
                            hint == android.view.View.AUTOFILL_HINT_PASSWORD -> {
                                if (passwordId == null) passwordId = node.autofillId
                            }
                        }
                    }
                }

                // Heuristic fallback: check HTML attributes and input type
                if (usernameId == null || passwordId == null) {
                    val htmlInfo = node.htmlInfo
                    val inputType = node.inputType
                    val idEntry = node.idEntry?.lowercase() ?: ""

                    if (passwordId == null && (
                        inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD != 0 ||
                        inputType and android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD != 0 ||
                        inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD != 0 ||
                        idEntry.contains("password") || idEntry.contains("passwd"))) {
                        passwordId = node.autofillId
                    } else if (usernameId == null && (
                        inputType and android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS != 0 ||
                        inputType and android.text.InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS != 0 ||
                        idEntry.contains("username") || idEntry.contains("email") || idEntry.contains("login"))) {
                        usernameId = node.autofillId
                    }
                }
            }
        }

        return ParsedFields(usernameId, passwordId, webDomain, appPackage)
    }

    private fun traverseNode(node: AssistStructure.ViewNode, visitor: (AssistStructure.ViewNode) -> Unit) {
        visitor(node)
        for (i in 0 until node.childCount) {
            traverseNode(node.getChildAt(i), visitor)
        }
    }
}

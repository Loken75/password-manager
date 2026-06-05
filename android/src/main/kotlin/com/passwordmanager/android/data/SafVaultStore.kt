package com.passwordmanager.android.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.passwordmanager.vault.store.VaultStore
import java.io.FileNotFoundException
import java.io.IOException

/**
 * [VaultStore] backed by a Storage Access Framework document tree, so the vault folder can live
 * anywhere the user picked (e.g. {@code Documents/PasswordManager}) and stay visible to file
 * managers and other apps. Access goes through {@code ContentResolver} streams — there is no
 * filesystem path, so [pathOf] is unsupported (callers use {@code readVaultBytes} + staging).
 *
 * Caveats vs [com.passwordmanager.vault.store.FileVaultStore]:
 * - No POSIX permissions (the SAF grant governs access).
 * - No truly atomic replace; [writeAtomic] overwrites in place. The {@code .bak} that
 *   {@code VaultManager.saveVault} writes beforehand is the corruption safety net.
 * - Name-based lookup assumes the document provider preserves the exact display name passed to
 *   {@code createFile} (true for the primary {@code ExternalStorageProvider}, i.e. on-device
 *   folders like {@code Documents/}). Providers that rewrite names are not supported.
 */
class SafVaultStore(
    private val context: Context,
    private val treeUri: Uri
) : VaultStore {

    private val mimeType = "application/octet-stream"

    private fun tree(): DocumentFile =
        DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("Workspace folder is no longer accessible")

    private fun find(name: String): DocumentFile? = tree().findFile(name)

    override fun list(): List<String> =
        tree().listFiles().mapNotNull { if (it.isFile) it.name else null }

    override fun exists(name: String): Boolean = find(name) != null

    override fun size(name: String): Long =
        find(name)?.length() ?: throw FileNotFoundException(name)

    override fun read(name: String): ByteArray {
        val doc = find(name) ?: throw FileNotFoundException(name)
        return context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() }
            ?: throw IOException("Cannot open $name for reading")
    }

    override fun writeAtomic(name: String, data: ByteArray) {
        val dir = tree()
        val doc = dir.findFile(name) ?: dir.createFile(mimeType, name)
            ?: throw IOException("Cannot create $name")
        // "wt" truncates before writing so a shorter payload doesn't leave trailing bytes.
        context.contentResolver.openOutputStream(doc.uri, "wt")?.use { it.write(data) }
            ?: throw IOException("Cannot open $name for writing")
    }

    override fun copy(from: String, to: String) {
        writeAtomic(to, read(from))
    }

    override fun delete(name: String) {
        find(name)?.let { if (!it.delete()) throw IOException("Cannot delete $name") }
    }

    override fun lastModified(name: String): Long = find(name)?.lastModified() ?: 0

    override fun pathOf(name: String): String =
        throw UnsupportedOperationException("SAF store has no filesystem path; use readVaultBytes")

    override fun describe(): String = tree().name ?: treeUri.toString()
}

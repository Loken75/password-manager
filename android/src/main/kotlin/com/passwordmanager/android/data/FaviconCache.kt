package com.passwordmanager.android.data

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide, reactive in-memory favicon cache keyed by domain. Shared between
 * [com.passwordmanager.android.ui.vault.EntryEditViewModel] (which warms it when a
 * URL is saved) and the vault list, so a favicon fetched on create/edit appears
 * immediately without any network request at display time.
 */
@Singleton
class FaviconCache @Inject constructor() {
    private val _favicons = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val favicons: StateFlow<Map<String, Bitmap>> = _favicons.asStateFlow()

    fun get(domain: String): Bitmap? = _favicons.value[domain]

    fun put(domain: String, bitmap: Bitmap) {
        _favicons.update { it + (domain to bitmap) }
    }
}

package com.passwordmanager.android.ui.settings

import androidx.lifecycle.ViewModel
import com.passwordmanager.android.data.SessionHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class CategoryManagementUiState(
    val categories: List<String> = emptyList(),
    val newCategoryName: String = "",
    val error: String? = null
)

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val sessionHolder: SessionHolder
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryManagementUiState())
    val uiState: StateFlow<CategoryManagementUiState> = _uiState.asStateFlow()

    fun load() {
        val vault = sessionHolder.vault ?: return
        _uiState.value = CategoryManagementUiState(categories = vault.categories.sorted())
    }

    fun updateNewCategoryName(name: String) {
        _uiState.update { it.copy(newCategoryName = name, error = null) }
    }

    fun addCategory() {
        val name = _uiState.value.newCategoryName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "category_name_required") }
            return
        }
        if (name.length > 50) {
            _uiState.update { it.copy(error = "category_name_too_long") }
            return
        }
        val existingCategories = sessionHolder.vault?.categories ?: emptyList()
        if (existingCategories.contains(name)) {
            _uiState.update { it.copy(error = "category_already_exists") }
            return
        }
        val service = sessionHolder.vaultService ?: return
        service.addCategory(name)
        sessionHolder.save()
        _uiState.update {
            it.copy(
                categories = sessionHolder.vault?.categories?.sorted() ?: emptyList(),
                newCategoryName = "",
                error = null
            )
        }
    }

    fun removeCategory(category: String) {
        val service = sessionHolder.vaultService ?: return
        // Reassign entries in this category to uncategorized
        val vault = sessionHolder.vault ?: return
        for (entry in vault.entries) {
            if (entry.category == category) {
                entry.category = ""
            }
        }
        service.removeCategory(category)
        sessionHolder.save()
        _uiState.update {
            it.copy(categories = sessionHolder.vault?.categories?.sorted() ?: emptyList())
        }
    }
}

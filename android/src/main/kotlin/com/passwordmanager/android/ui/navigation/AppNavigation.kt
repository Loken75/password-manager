package com.passwordmanager.android.ui.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.passwordmanager.android.data.AndroidConfigRepository
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.ui.audit.SecurityAuditScreen
import com.passwordmanager.android.ui.generator.GeneratorScreen
import com.passwordmanager.android.ui.login.LoginScreen
import com.passwordmanager.android.ui.settings.ChangeMasterPasswordScreen
import com.passwordmanager.android.ui.settings.SettingsScreen
import com.passwordmanager.android.ui.vault.EntryDetailScreen
import com.passwordmanager.android.ui.vault.EntryEditScreen
import com.passwordmanager.android.ui.vault.VaultListScreen

object Routes {
    const val LOGIN = "login"
    const val VAULT_LIST = "vault_list"
    const val ENTRY_DETAIL = "entry_detail/{entryId}"
    const val ENTRY_EDIT = "entry_edit?entryId={entryId}"
    const val GENERATOR = "generator?returnPassword={returnPassword}"
    const val SETTINGS = "settings"
    const val CHANGE_MASTER_PASSWORD = "change_master_password"
    const val SECURITY_AUDIT = "security_audit"

    fun entryDetail(entryId: String) = "entry_detail/$entryId"
    fun entryEdit(entryId: String? = null) =
        if (entryId != null) "entry_edit?entryId=$entryId" else "entry_edit"
    fun generator(returnPassword: Boolean = false) = "generator?returnPassword=$returnPassword"
}

@Composable
fun AppNavigation(configRepo: AndroidConfigRepository) {
    val navController = rememberNavController()
    val isUnlocked by SessionHolder.isUnlockedFlow.collectAsStateWithLifecycle()

    // Navigate to login when session is locked
    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.LOGIN
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.VAULT_LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.VAULT_LIST) {
            VaultListScreen(
                onEntryClick = { entryId ->
                    navController.navigate(Routes.entryDetail(entryId))
                },
                onNewEntry = {
                    navController.navigate(Routes.entryEdit())
                },
                onSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onSecurityAudit = {
                    navController.navigate(Routes.SECURITY_AUDIT)
                },
                onGenerator = {
                    navController.navigate(Routes.generator())
                },
                onLock = {
                    SessionHolder.lock()
                }
            )
        }

        composable(
            route = Routes.ENTRY_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId") ?: return@composable
            EntryDetailScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.entryEdit(entryId)) },
                onDeleted = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ENTRY_EDIT,
            arguments = listOf(
                navArgument("entryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId")
            val generatedPassword = backStackEntry.savedStateHandle
                ?.get<String>("generated_password")
            EntryEditScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onNavigateToGenerator = {
                    navController.navigate(Routes.generator(returnPassword = true))
                },
                generatedPassword = generatedPassword
            )
        }

        composable(
            route = Routes.GENERATOR,
            arguments = listOf(
                navArgument("returnPassword") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val returnPassword = backStackEntry.arguments?.getBoolean("returnPassword") ?: false
            GeneratorScreen(
                onBack = { navController.popBackStack() },
                onUsePassword = if (returnPassword) { password ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("generated_password", String(password))
                    navController.popBackStack()
                } else null
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                configRepo = configRepo,
                onBack = { navController.popBackStack() },
                onChangeMasterPassword = {
                    navController.navigate(Routes.CHANGE_MASTER_PASSWORD)
                }
            )
        }

        composable(Routes.CHANGE_MASTER_PASSWORD) {
            ChangeMasterPasswordScreen(
                onBack = { navController.popBackStack() },
                onChanged = { navController.popBackStack() }
            )
        }

        composable(Routes.SECURITY_AUDIT) {
            SecurityAuditScreen(
                onBack = { navController.popBackStack() },
                onEntryClick = { entryId ->
                    navController.navigate(Routes.entryDetail(entryId))
                }
            )
        }
    }
}

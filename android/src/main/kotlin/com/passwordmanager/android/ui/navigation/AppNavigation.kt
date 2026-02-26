package com.passwordmanager.android.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.passwordmanager.android.R
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

    // Tab routes
    const val TAB_VAULT = "tab_vault"
    const val TAB_GENERATOR = "tab_generator"
    const val TAB_AUDIT = "tab_audit"
    const val TAB_SETTINGS = "tab_settings"

    // Modal routes (no bottom bar)
    const val ENTRY_DETAIL = "entry_detail/{entryId}"
    const val ENTRY_EDIT = "entry_edit?entryId={entryId}"
    const val GENERATOR = "generator?returnPassword={returnPassword}"
    const val CHANGE_MASTER_PASSWORD = "change_master_password"

    fun entryDetail(entryId: String) = "entry_detail/$entryId"
    fun entryEdit(entryId: String? = null) =
        if (entryId != null) "entry_edit?entryId=$entryId" else "entry_edit"
    fun generator(returnPassword: Boolean = false) = "generator?returnPassword=$returnPassword"
}

enum class BottomNavTab(
    val route: String,
    val icon: ImageVector,
    val labelResId: Int
) {
    VAULT(Routes.TAB_VAULT, Icons.Default.Shield, R.string.nav_vault),
    GENERATOR(Routes.TAB_GENERATOR, Icons.Default.Key, R.string.nav_generator),
    AUDIT(Routes.TAB_AUDIT, Icons.Default.Security, R.string.nav_audit),
    SETTINGS(Routes.TAB_SETTINGS, Icons.Default.Settings, R.string.nav_settings),
}

private val tabRoutes = BottomNavTab.entries.map { it.route }.toSet()

@Composable
fun AppNavigation() {
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

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomNav = currentRoute in tabRoutes

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    BottomNavTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelResId)) }
                        )
                    }
                }
            }
        }
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.padding(outerPadding),
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            // ── Login ──
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Routes.TAB_VAULT) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            // ── Tab: Vault ──
            composable(Routes.TAB_VAULT) {
                VaultListScreen(
                    onEntryClick = { entryId ->
                        navController.navigate(Routes.entryDetail(entryId))
                    },
                    onNewEntry = {
                        navController.navigate(Routes.entryEdit())
                    },
                    onLock = {
                        SessionHolder.lock()
                    }
                )
            }

            // ── Tab: Generator ──
            composable(Routes.TAB_GENERATOR) {
                GeneratorScreen(
                    onBack = {},
                    onUsePassword = null,
                    showBackNavigation = false
                )
            }

            // ── Tab: Audit ──
            composable(Routes.TAB_AUDIT) {
                SecurityAuditScreen(
                    onBack = {},
                    onEntryClick = { entryId ->
                        navController.navigate(Routes.entryDetail(entryId))
                    },
                    showBackNavigation = false
                )
            }

            // ── Tab: Settings ──
            composable(Routes.TAB_SETTINGS) {
                SettingsScreen(
                    onBack = {},
                    onChangeMasterPassword = {
                        navController.navigate(Routes.CHANGE_MASTER_PASSWORD)
                    },
                    showBackNavigation = false
                )
            }

            // ── Modal: Entry Detail ──
            composable(
                route = Routes.ENTRY_DETAIL,
                arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
                enterTransition = {
                    slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = {
                    slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                }
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.getString("entryId") ?: return@composable
                EntryDetailScreen(
                    entryId = entryId,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.entryEdit(entryId)) },
                    onDeleted = { navController.popBackStack() }
                )
            }

            // ── Modal: Entry Edit ──
            composable(
                route = Routes.ENTRY_EDIT,
                arguments = listOf(
                    navArgument("entryId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = {
                    slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                },
                exitTransition = { fadeOut(animationSpec = tween(300)) },
                popEnterTransition = { fadeIn(animationSpec = tween(300)) },
                popExitTransition = {
                    slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                }
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

            // ── Modal: Generator (return password mode) ──
            composable(
                route = Routes.GENERATOR,
                arguments = listOf(
                    navArgument("returnPassword") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                ),
                enterTransition = {
                    slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                },
                popExitTransition = {
                    slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                }
            ) { backStackEntry ->
                val returnPassword = backStackEntry.arguments?.getBoolean("returnPassword") ?: false
                GeneratorScreen(
                    onBack = { navController.popBackStack() },
                    showBackNavigation = true,
                    onUsePassword = if (returnPassword) { password ->
                        val passwordString = String(password)
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("generated_password", passwordString)
                        password.fill('\u0000')
                        navController.popBackStack()
                    } else null
                )
            }

            // ── Modal: Change Master Password ──
            composable(
                route = Routes.CHANGE_MASTER_PASSWORD,
                enterTransition = {
                    slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                },
                popExitTransition = {
                    slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                }
            ) {
                ChangeMasterPasswordScreen(
                    onBack = { navController.popBackStack() },
                    onChanged = { navController.popBackStack() }
                )
            }
        }
    }
}

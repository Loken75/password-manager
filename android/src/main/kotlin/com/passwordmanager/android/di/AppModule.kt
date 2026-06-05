package com.passwordmanager.android.di

import android.content.Context
import com.passwordmanager.android.data.AndroidConfigRepository
import com.passwordmanager.android.data.AndroidVaultRepository
import com.passwordmanager.android.data.BiometricHelper
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.FaviconRepository
import com.passwordmanager.android.data.AndroidWorkspaceManager
import com.passwordmanager.android.data.SessionHolder
import com.passwordmanager.android.data.WorkspaceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/** Qualifies the background dispatcher used for blocking vault I/O (overridable in tests). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideWorkspaceManager(
        @ApplicationContext context: Context,
        configRepository: ConfigRepository
    ): WorkspaceManager = AndroidWorkspaceManager(context, configRepository)

    @Provides
    @Singleton
    fun provideVaultRepository(workspaceManager: WorkspaceManager): AndroidVaultRepository =
        AndroidVaultRepository(workspaceManager.currentStore())

    @Provides
    @Singleton
    fun provideConfigRepository(@ApplicationContext context: Context): ConfigRepository =
        AndroidConfigRepository(context)

    @Provides
    @Singleton
    fun provideSessionHolder(repository: AndroidVaultRepository): SessionHolder {
        SessionHolder.init(repository)
        return SessionHolder
    }

    @Provides
    @Singleton
    fun provideBiometricHelper(@ApplicationContext context: Context): BiometricHelper =
        BiometricHelper(context)

    @Provides
    @Singleton
    fun provideSshHostKeyStore(@ApplicationContext context: Context): com.passwordmanager.android.data.SshHostKeyStore =
        com.passwordmanager.android.data.SshHostKeyStore(java.io.File(context.filesDir, "ssh/known_hosts"))

    @Provides
    @Singleton
    fun provideFaviconService(@ApplicationContext context: Context): com.passwordmanager.util.FaviconService =
        com.passwordmanager.util.FaviconService(context.cacheDir.absolutePath + "/favicons")

    @Provides
    @Singleton
    fun provideFaviconRepository(faviconService: com.passwordmanager.util.FaviconService): FaviconRepository =
        FaviconRepository(faviconService)
}

package com.passwordmanager.android.di

import android.content.Context
import com.passwordmanager.android.data.AndroidConfigRepository
import com.passwordmanager.android.data.AndroidVaultRepository
import com.passwordmanager.android.data.ConfigRepository
import com.passwordmanager.android.data.SessionHolder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVaultRepository(@ApplicationContext context: Context): AndroidVaultRepository =
        AndroidVaultRepository(context.filesDir.absolutePath + "/vaults")

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
}

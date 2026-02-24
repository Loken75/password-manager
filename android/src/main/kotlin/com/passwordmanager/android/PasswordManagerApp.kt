package com.passwordmanager.android

import android.app.Application
import com.passwordmanager.android.data.AndroidVaultRepository
import com.passwordmanager.android.data.SessionHolder

class PasswordManagerApp : Application() {

    lateinit var vaultRepository: AndroidVaultRepository
        private set

    override fun onCreate() {
        super.onCreate()
        vaultRepository = AndroidVaultRepository(filesDir.absolutePath + "/vaults")
        SessionHolder.init(vaultRepository)
    }
}

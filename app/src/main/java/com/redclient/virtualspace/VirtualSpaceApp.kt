package com.redclient.virtualspace

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.redclient.virtualspace.data.database.AppDatabase
import com.redclient.virtualspace.data.repository.VirtualAppRepository
import com.redclient.virtualspace.engine.NativeBridge
import com.redclient.virtualspace.util.PermissionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "virtualspace_settings")

class VirtualSpaceApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var appRepository: VirtualAppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        val db = AppDatabase.getDatabase(this)
        appRepository = VirtualAppRepository(db.virtualAppDao())

        applicationScope.launch {
            initializeNativeEngine()
        }
    }

    private fun initializeNativeEngine() {
        val basePath = getExternalFilesDir(null)?.absolutePath
            ?: filesDir.absolutePath

        NativeBridge.initialize(basePath)

        // Ensure log directory exists
        val logDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .resolve("VirtualSpace/logs")
        logDir.mkdirs()
    }

    companion object {
        lateinit var instance: VirtualSpaceApp
            private set

        fun getContext(): Context = instance.applicationContext
    }
}

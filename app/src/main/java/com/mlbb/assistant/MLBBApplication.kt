package com.mlbb.assistant

import android.app.Application
import com.mlbb.assistant.data.local.crashlog.AppLogTree
import com.mlbb.assistant.data.local.crashlog.installCrashHandler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MLBBApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Install the global crash-to-file handler first so even crashes during
        // startup are captured before the process dies.
        installCrashHandler(applicationContext)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Always plant the persistent log tree so WARN/ERROR entries are saved
        // regardless of build type.  The tree writes synchronously to avoid
        // losing entries when the process is terminated immediately after a crash.
        Timber.plant(AppLogTree(applicationContext))
    }
}

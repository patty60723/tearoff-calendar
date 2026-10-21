package com.patty60723.tearoff

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File

/**
 * 閃退時把錯誤存下來，下次打開 App 先顯示出來，方便截圖回報。
 */
class TearoffApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                CrashLog.file(this).writeText(
                    "版本 ${BuildConfigInfo.version(this)}・Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）・${Build.MODEL}\n\n" +
                        error.stackTraceToString()
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

object CrashLog {
    fun file(context: Context) = File(context.filesDir, "crash.txt")

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }
}

private object BuildConfigInfo {
    fun version(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
}

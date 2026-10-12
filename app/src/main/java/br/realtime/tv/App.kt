package br.realtime.tv

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        Ui.density = resources.displayMetrics.density
        val um = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val pm = packageManager
        Ui.isTv = um.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature("android.software.leanback") ||
            !pm.hasSystemFeature("android.hardware.touchscreen")
    }

    companion object {
        lateinit var ctx: Context
    }
}

object Config {
    const val SERVER = "http://186.235.127.27:30021"
    const val NAME = "Realtime"
    const val VERSION = "1.0.0"
}

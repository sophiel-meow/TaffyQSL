package moe.zzy040330.taffyqsl.data

import android.content.Context
import androidx.core.content.edit

class AppPreferences private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("taffyqsl_prefs", Context.MODE_PRIVATE)

    var isDebugMode: Boolean
        get() = prefs.getBoolean("debug_mode", false)
        set(value) {
            prefs.edit { putBoolean("debug_mode", value) }
        }

    companion object {
        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences =
            instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
    }
}

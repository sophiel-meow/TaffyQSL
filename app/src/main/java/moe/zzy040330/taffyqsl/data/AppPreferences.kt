package moe.zzy040330.taffyqsl.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class DateFormatOption(
    val longPattern: String,   // full display: year-month-day
    val shortPattern: String,  // for parsing user input later
) {
    YMD_DASH("yyyy-MM-dd", "MMdd"),
    YMD_SLASH("yyyy/MM/dd", "MMdd"),
    MDY_SLASH("MM/dd/yyyy", "MMdd"),
    DMY_SLASH("dd/MM/yyyy", "ddMM"),
    DMY_DOT("dd.MM.yyyy", "ddMM");

    val longFormatter: DateTimeFormatter get() = DateTimeFormatter.ofPattern(longPattern)

    fun formatDate(date: LocalDate): String = date.format(longFormatter)

    fun preview(): String = formatDate(LocalDate.of(2026, 12, 30))
}

class AppPreferences private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("taffyqsl_prefs", Context.MODE_PRIVATE)

    var isDebugMode: Boolean
        get() = prefs.getBoolean("debug_mode", false)
        set(value) {
            prefs.edit { putBoolean("debug_mode", value) }
        }

    var dateFormat: DateFormatOption
        get() = prefs.getString("date_format", null)
            ?.let { name -> DateFormatOption.entries.find { it.name == name } }
            ?: DateFormatOption.YMD_DASH
        set(value) {
            prefs.edit { putString("date_format", value.name) }
        }

    var useLocalTime: Boolean
        get() = prefs.getBoolean("use_local_time", false)
        set(value) {
            prefs.edit { putBoolean("use_local_time", value) }
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

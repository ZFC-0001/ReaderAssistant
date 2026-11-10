package com.example.localtaskassistant.data

import android.content.Context
import com.example.localtaskassistant.contract.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** B：最小可运行实现；接口已隔离，后续替换 DataStore 不影响 A/C。 */
class LocalSettingsStore(context: Context) : SettingsStore {
    private val preferences = context.getSharedPreferences("assistant-settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    override val settings = mutable.asStateFlow()
    private fun read(): UserSettings = runCatching {
        UserSettings(
            capture = CapturePolicy(preferences.getBoolean("capture", false),
                preferences.getStringSet("apps", emptySet()).orEmpty().intersect(TargetApps.supported)),
            promptMode = PromptMode.valueOf(preferences.getString("mode", PromptMode.TIME_BASED.name)!!),
            cardWindowHours = preferences.getInt("window", 24), sourceMaxAgeDays = preferences.getInt("age", 7),
            defaultLeadMinutes = preferences.getInt("lead", 30), snoozeMinutes = preferences.getInt("snooze", 10),
        )
    }.getOrDefault(UserSettings())
    override suspend fun update(settings: UserSettings) = withContext(Dispatchers.IO) {
        // TODO(B-10): 换为 DataStore；保留默认关闭、应用白名单和一次性更新语义。
        val saved = preferences.edit().putBoolean("capture", settings.capture.enabled)
            .putStringSet("apps", settings.capture.allowedPackages.toSet()).putString("mode", settings.promptMode.name)
            .putInt("window", settings.cardWindowHours).putInt("age", settings.sourceMaxAgeDays)
            .putInt("lead", settings.defaultLeadMinutes).putInt("snooze", settings.snoozeMinutes).commit()
        check(saved) { "设置保存失败" }
        mutable.value = settings
    }
}

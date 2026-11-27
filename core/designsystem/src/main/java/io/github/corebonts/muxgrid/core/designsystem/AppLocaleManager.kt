package io.github.corebonts.muxgrid.core.designsystem

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * One selectable app language entry shown in Settings → Language.
 *
 * [tag] is an empty string for "System default", or a BCP-47 language tag (e.g. `"en"`, `"hu"`).
 * [nativeName] is `null` for System default (localized via string resource) and the native endonym
 * (e.g. `"English"`, `"Magyar"`) for concrete locales so users can always recognize their language.
 */
data class AppLanguageOption(
    val tag: String,
    val nativeName: String?,
)

/**
 * Coordinates per-app language selection across Android 9–12 (API 28–32) and Android 13+ (API 33+).
 *
 * - On API 33+, delegates directly to [LocaleManager.setApplicationLocales], staying 100% in sync
 *   with Android System Settings → Apps → MuxGrid → Language (backed by AGP `generateLocaleConfig`).
 * - On API 28–32, persists the BCP-47 tag in a tiny synchronous `SharedPreferences` file read from
 *   `MainActivity.attachBaseContext` and calls `Activity.recreate()`.
 */
object AppLocaleManager {

    const val TAG_SYSTEM = ""

    private const val PREFS_NAME = "muxgrid_locale"
    private const val KEY_LOCALE_TAG = "app_locale_tag"

    /**
     * Supported locales in display order. When adding a new community `values-<tag>/strings.xml`,
     * append an entry here with its native endonym.
     */
    val supportedLanguages: List<AppLanguageOption> = listOf(
        AppLanguageOption(tag = TAG_SYSTEM, nativeName = null),
        AppLanguageOption(tag = "en", nativeName = "English"),
        AppLanguageOption(tag = "hu", nativeName = "Magyar"),
    )

    fun currentLanguageTag(context: Context): String {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java)
            val locales = lm?.applicationLocales
            if (locales == null || locales.isEmpty) {
                TAG_SYSTEM
            } else {
                locales[0]?.toLanguageTag().orEmpty()
            }
        } else {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LOCALE_TAG, TAG_SYSTEM)
                .orEmpty()
        }
        return normalizeToSupportedTag(raw)
    }

    fun setLanguageTag(context: Context, tag: String) {
        val normalized = normalizeToSupportedTag(tag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val lm = context.getSystemService(LocaleManager::class.java) ?: return
            lm.applicationLocales = if (normalized.isEmpty()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(normalized)
            }
        } else {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val previous = prefs.getString(KEY_LOCALE_TAG, TAG_SYSTEM).orEmpty()
            if (previous == normalized) return
            prefs.edit().putString(KEY_LOCALE_TAG, normalized).apply()
            context.findActivity()?.recreate()
        }
    }

    /**
     * Wraps [base] with the stored locale override on API 28–32. No-op on API 33+ where the
     * framework's [LocaleManager] already applies the per-app locale to the activity context.
     */
    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCALE_TAG, TAG_SYSTEM)
            .orEmpty()
        if (tag.isBlank()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    internal fun normalizeToSupportedTag(raw: String): String {
        if (raw.isBlank()) return TAG_SYSTEM
        val exact = supportedLanguages.firstOrNull {
            it.tag.isNotEmpty() && it.tag.equals(raw, ignoreCase = true)
        }
        if (exact != null) return exact.tag
        val primary = raw.substringBefore('-').substringBefore('_')
        return supportedLanguages.firstOrNull {
            it.tag.isNotEmpty() && it.tag.equals(primary, ignoreCase = true)
        }?.tag ?: TAG_SYSTEM
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

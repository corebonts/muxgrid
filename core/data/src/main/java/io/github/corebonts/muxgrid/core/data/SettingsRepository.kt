package io.github.corebonts.muxgrid.core.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import io.github.corebonts.muxgrid.core.model.FormFactor
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.LocalDvrProgress
import io.github.corebonts.muxgrid.core.model.ServerProfile
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets

private val Context.muxGridDataStore: DataStore<Preferences> by preferencesDataStore(name = "muxgrid_settings")

class SettingsRepository(
    private val context: Context,
    private val formFactor: FormFactor,
) {
    private val aead: Aead? by lazy { createOrRecoverAead() }

    val serverProfile: Flow<ServerProfile> = context.muxGridDataStore.data.map { prefs ->
        val encryptedPass = prefs[KEY_PASSWORD_ENC]
        val password = if (!encryptedPass.isNullOrEmpty()) decryptPassword(encryptedPass) else ""
        ServerProfile(
            id = "default",
            name = prefs[KEY_NAME] ?: "Homelab Tvheadend",
            host = prefs[KEY_HOST] ?: "",
            htspPort = prefs[KEY_HTSP_PORT] ?: 9982,
            httpPort = prefs[KEY_HTTP_PORT] ?: 9981,
            username = prefs[KEY_USERNAME] ?: "",
            password = password,
        )
    }

    val favoriteChannelIds: Flow<Set<Long>> = context.muxGridDataStore.data.map { prefs ->
        prefs[KEY_FAVORITES]?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }

    val hwDecPreset: Flow<HwDecPreset> = context.muxGridDataStore.data.map { prefs ->
        val raw = prefs[KEY_HWDEC_PRESET]
        raw?.let { runCatching { HwDecPreset.valueOf(it) }.getOrNull() }
            ?: HwDecPreset.defaultFor(formFactor)
    }

    val timeshiftWindow: Flow<TimeshiftWindow> = context.muxGridDataStore.data.map { prefs ->
        TimeshiftWindow.fromPersisted(prefs[KEY_TIMESHIFT_WINDOW], formFactor)
    }

    val backgroundPlayEnabled: Flow<Boolean> = context.muxGridDataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_PLAY] ?: false
    }

    val pipEnabled: Flow<Boolean> = context.muxGridDataStore.data.map { prefs ->
        prefs[KEY_PIP_ENABLED] ?: (formFactor == FormFactor.PHONE)
    }

    val localDvrProgress: Flow<Map<Long, LocalDvrProgress>> = context.muxGridDataStore.data.map { prefs ->
        val entries = prefs[KEY_LOCAL_DVR_PROGRESS].orEmpty()
        buildMap {
            for (raw in entries) {
                val parts = raw.split(':')
                if (parts.size == 3) {
                    val id = parts[0].toLongOrNull() ?: continue
                    val pos = parts[1].toLongOrNull() ?: continue
                    val count = parts[2].toIntOrNull() ?: continue
                    put(id, LocalDvrProgress(playPositionSec = pos, playCount = count))
                }
            }
        }
    }

    suspend fun saveLocalDvrProgress(dvrId: Long, playPositionSec: Long, playCount: Int) {
        context.muxGridDataStore.edit { prefs ->
            val current = prefs[KEY_LOCAL_DVR_PROGRESS].orEmpty()
                .filterNot { it.startsWith("$dvrId:") }
                .toMutableSet()
            current.add("$dvrId:${playPositionSec.coerceAtLeast(0L)}:${playCount.coerceAtLeast(0)}")
            prefs[KEY_LOCAL_DVR_PROGRESS] = current
        }
    }

    suspend fun removeLocalDvrProgress(dvrId: Long) {
        context.muxGridDataStore.edit { prefs ->
            val current = prefs[KEY_LOCAL_DVR_PROGRESS] ?: return@edit
            val filtered = current.filterNot { it.startsWith("$dvrId:") }.toSet()
            if (filtered.size != current.size) {
                prefs[KEY_LOCAL_DVR_PROGRESS] = filtered
            }
        }
    }

    suspend fun saveServerProfile(profile: ServerProfile) {
        val encrypted = if (profile.password.isNotEmpty()) encryptPassword(profile.password) else ""
        context.muxGridDataStore.edit { prefs ->
            prefs[KEY_NAME] = profile.name
            prefs[KEY_HOST] = profile.host.trim()
            prefs[KEY_HTSP_PORT] = profile.htspPort
            prefs[KEY_HTTP_PORT] = profile.httpPort
            prefs[KEY_USERNAME] = profile.username.trim()
            prefs[KEY_PASSWORD_ENC] = encrypted
        }
    }

    suspend fun toggleFavorite(channelId: Long) {
        context.muxGridDataStore.edit { prefs ->
            val current = prefs[KEY_FAVORITES]?.toMutableSet() ?: mutableSetOf()
            val idStr = channelId.toString()
            if (!current.add(idStr)) {
                current.remove(idStr)
            }
            prefs[KEY_FAVORITES] = current
        }
    }

    suspend fun setHwDecPreset(preset: HwDecPreset) {
        context.muxGridDataStore.edit { prefs ->
            prefs[KEY_HWDEC_PRESET] = preset.name
        }
    }

    suspend fun setTimeshiftWindow(window: TimeshiftWindow) {
        context.muxGridDataStore.edit { prefs ->
            prefs[KEY_TIMESHIFT_WINDOW] = window.name
        }
    }

    suspend fun setBackgroundPlayEnabled(enabled: Boolean) {
        context.muxGridDataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_PLAY] = enabled
        }
    }

    suspend fun setPipEnabled(enabled: Boolean) {
        context.muxGridDataStore.edit { prefs ->
            prefs[KEY_PIP_ENABLED] = enabled
        }
    }

    private fun createOrRecoverAead(): Aead? = runCatching {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }.getOrNull()

    private fun encryptPassword(plaintext: String): String {
        val cipher = aead ?: return ""
        val bytes = cipher.encrypt(
            plaintext.toByteArray(StandardCharsets.UTF_8),
            ASSOCIATED_DATA,
        )
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decryptPassword(encoded: String): String {
        val cipher = aead ?: return ""
        return runCatching {
            val raw = Base64.decode(encoded, Base64.NO_WRAP)
            val plain = cipher.decrypt(raw, ASSOCIATED_DATA)
            String(plain, StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    companion object {
        private val KEY_NAME = stringPreferencesKey("server_name")
        private val KEY_HOST = stringPreferencesKey("server_host")
        private val KEY_HTSP_PORT = intPreferencesKey("server_htsp_port")
        private val KEY_HTTP_PORT = intPreferencesKey("server_http_port")
        private val KEY_USERNAME = stringPreferencesKey("server_username")
        private val KEY_PASSWORD_ENC = stringPreferencesKey("server_password_enc")
        private val KEY_FAVORITES = stringSetPreferencesKey("favorite_channel_ids")
        private val KEY_HWDEC_PRESET = stringPreferencesKey("hwdec_preset")
        private val KEY_TIMESHIFT_WINDOW = stringPreferencesKey("timeshift_window")
        private val KEY_BACKGROUND_PLAY = booleanPreferencesKey("background_play_enabled")
        private val KEY_PIP_ENABLED = booleanPreferencesKey("pip_enabled")
        private val KEY_LOCAL_DVR_PROGRESS = stringSetPreferencesKey("dvr_local_progress")

        private const val KEYSET_NAME = "muxgrid_master_keyset"
        private const val KEYSET_PREF_FILE = "muxgrid_tink_prefs"
        private const val MASTER_KEY_URI = "android-keystore://muxgrid_master_key"
        private val ASSOCIATED_DATA = "muxgrid-server-creds".toByteArray(StandardCharsets.UTF_8)
    }
}

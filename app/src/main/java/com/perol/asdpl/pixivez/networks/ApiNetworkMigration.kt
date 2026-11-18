package com.perol.asdpl.pixivez.networks

import android.content.SharedPreferences
import androidx.core.content.edit

/** Migrate the old built-in origin profile once; keep proxy/plain and custom profiles. */
internal fun migrateLegacyApiNetwork(preferences: SharedPreferences) {
    // Historical development builds used this key; retain it to avoid re-migrating user choices.
    val marker = "apiEchMigration226"
    if (preferences.getBoolean(marker, false)) return
    val savedSni = preferences.getString(SniMode.PREF_KEY, null)
    // 全新安装(无任何已保存值)不应被迁移逻辑强改成 ECH，否则新装的 App 会连不上
    val migrate = savedSni != null && shouldMigrateLegacyApiNetwork(
        preferences.getString(DnsMode.PREF_KEY, null),
        savedSni,
        preferences.getString(PixivDirectDns.PREF_KEY, null),
        preferences.getString(SniReplaceConfig.PREF_KEY, null),
    )
    preferences.edit {
        if (migrate) putString(SniMode.PREF_KEY, SniMode.ECH.code)
        putBoolean(marker, true)
    }
}

internal fun shouldMigrateLegacyApiNetwork(
    dns: String?, sni: String?, directIps: String?, replacementHost: String?,
): Boolean = (dns == null || dns == "direct") &&
    sni in setOf(null, "replace", "empty") && directIps.isNullOrBlank() &&
    (replacementHost.isNullOrBlank() || replacementHost.trim() == "pixiv.me")

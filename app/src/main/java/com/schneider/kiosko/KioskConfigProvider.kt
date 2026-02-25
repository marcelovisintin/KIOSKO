package com.schneider.kiosko

import android.content.Context
import android.content.RestrictionsManager

data class KioskConfig(
    val allowedPackages: Set<String>,
    val allowSystemUi: Boolean,
    val adminModeEnabled: Boolean,
    val pinRequired: Boolean,
    val userPin: String,
    val adminPin: String,
)

object KioskConfigProvider {

    private const val KEY_ALLOWED_PACKAGES_CSV = "allowed_packages_csv"
    private const val KEY_ALLOW_SYSTEM_UI = "allow_system_ui"
    private const val KEY_ADMIN_MODE_ENABLED = "admin_mode_enabled"
    private const val KEY_PIN_REQUIRED = "pin_required"
    private const val KEY_USER_PIN = "user_pin"
    private const val KEY_ADMIN_PIN = "admin_pin"

    fun load(context: Context): KioskConfig {
        val restrictionsManager = context.getSystemService(RestrictionsManager::class.java)
        val bundle = restrictionsManager?.applicationRestrictions
        val managedCsv = bundle?.getString(KEY_ALLOWED_PACKAGES_CSV).orEmpty()
        val managedPackages = managedCsv
            .split(',', ';', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        val defaultPackages = context.resources
            .getStringArray(R.array.default_allowed_packages)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        val allowSystemUi = bundle?.getBoolean(KEY_ALLOW_SYSTEM_UI, false) ?: false
        val adminModeEnabled = bundle?.getBoolean(KEY_ADMIN_MODE_ENABLED, false) ?: false
        val pinRequired = bundle?.getBoolean(KEY_PIN_REQUIRED, true) ?: true
        val defaultUserPin = context.getString(R.string.default_user_pin)
        val defaultAdminPin = context.getString(R.string.default_admin_pin)
        val userPin = sanitizePin(bundle?.getString(KEY_USER_PIN), defaultUserPin)
        val adminPin = sanitizePin(bundle?.getString(KEY_ADMIN_PIN), defaultAdminPin)

        return KioskConfig(
            allowedPackages = (defaultPackages + managedPackages + context.packageName),
            allowSystemUi = allowSystemUi,
            adminModeEnabled = adminModeEnabled,
            pinRequired = pinRequired,
            userPin = userPin,
            adminPin = adminPin,
        )
    }

    private fun sanitizePin(raw: String?, fallback: String): String {
        val candidate = raw.orEmpty().trim().filter { it.isDigit() }
        return if (candidate.length == 4) candidate else fallback
    }
}

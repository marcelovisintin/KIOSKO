package com.schneider.kiosko

import android.net.Uri

data class KioskWebLink(
    val id: String,
    val name: String,
    val url: String,
) {
    val permissionKey: String
        get() = permissionKeyFor(id)

    companion object {
        private const val KEY_PREFIX = "url::"

        fun permissionKeyFor(id: String): String = "$KEY_PREFIX$id"

        fun idFromPermissionKey(permissionKey: String): String? {
            if (!permissionKey.startsWith(KEY_PREFIX)) return null
            return permissionKey.removePrefix(KEY_PREFIX).ifBlank { null }
        }

        fun isPermissionKey(value: String): Boolean = value.startsWith(KEY_PREFIX)

        fun normalizeUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null

            val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                trimmed
            } else {
                "https://$trimmed"
            }

            val uri = Uri.parse(withScheme)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host
            if ((scheme != "http" && scheme != "https") || host.isNullOrBlank()) {
                return null
            }
            return uri.toString()
        }
    }
}

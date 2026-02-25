package com.schneider.kiosko

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object KioskUserStore {

    private const val PREFS_NAME = "kiosk_user_store"
    private const val KEY_USERS_JSON = "users_json"

    fun load(context: Context, config: KioskConfig): MutableList<KioskUserProfile> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_USERS_JSON, null).orEmpty()
        val defaultAllowedPackages = config.allowedPackages - context.packageName

        val users = mutableListOf<KioskUserProfile>()
        val usedPins = mutableSetOf<String>()

        if (rawJson.isNotBlank()) {
            runCatching {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                    val name = obj.optString("name").trim().ifBlank { "Usuario ${users.size + 1}" }
                    val pinRaw = obj.optString("pin").trim().filter { it.isDigit() }
                    if (pinRaw.length != 4) continue
                    if (!usedPins.add(pinRaw)) continue

                    val allowedArray = obj.optJSONArray("allowedPackages") ?: JSONArray()
                    val allowedPackages = mutableSetOf<String>()
                    for (j in 0 until allowedArray.length()) {
                        val pkg = allowedArray.optString(j).trim()
                        if (pkg.isNotBlank() && pkg != context.packageName) {
                            allowedPackages.add(pkg)
                        }
                    }

                    users.add(
                        KioskUserProfile(
                            id = id,
                            name = name,
                            pin = pinRaw,
                            allowedPackages = allowedPackages,
                        ),
                    )
                }
            }
        }

        if (users.isEmpty()) {
            users.add(
                KioskUserProfile(
                    id = UUID.randomUUID().toString(),
                    name = "Usuario 1",
                    pin = config.userPin,
                    allowedPackages = defaultAllowedPackages,
                ),
            )
            persist(context, users)
            return users
        }
        return users
    }

    fun persist(context: Context, users: List<KioskUserProfile>) {
        val arr = JSONArray()
        for (user in users) {
            val obj = JSONObject()
            obj.put("id", user.id)
            obj.put("name", user.name)
            obj.put("pin", user.pin)
            val pkgs = JSONArray()
            user.allowedPackages.sorted().forEach { pkgs.put(it) }
            obj.put("allowedPackages", pkgs)
            arr.put(obj)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERS_JSON, arr.toString())
            .apply()
    }
}

package com.schneider.kiosko

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object KioskWebLinkStore {

    private const val PREFS_NAME = "kiosk_web_link_store"
    private const val KEY_WEB_LINKS_JSON = "web_links_json"

    private data class DefaultWebLink(
        val name: String,
        val url: String,
    )

    private val DEFAULT_WEB_LINKS = listOf(
        DefaultWebLink(
            name = "Odoo Schneider",
            url = "https://schneider-srl.odoo.com/web/login?redirect=%2Fodoo%3F",
        ),
        DefaultWebLink(
            name = "Parte diario produccion",
            url = "https://script.google.com/macros/s/AKfycbxvlVMdxC8apqve0VIOv_tye63_H5xufDVbyUdo3TrJRwjwncW2rnCSKFHhEVK8SV1UCg/exec",
        ),
    )

    fun load(context: Context): MutableList<KioskWebLink> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_WEB_LINKS_JSON, null).orEmpty()

        val links = mutableListOf<KioskWebLink>()
        if (rawJson.isNotBlank()) {
            runCatching {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                    val name = obj.optString("name").trim()
                    val normalizedUrl = KioskWebLink.normalizeUrl(obj.optString("url"))
                    if (name.isBlank() || normalizedUrl == null) continue

                    links.add(
                        KioskWebLink(
                            id = id,
                            name = name,
                            url = normalizedUrl,
                        ),
                    )
                }
            }
        }
        var defaultsAdded = false
        DEFAULT_WEB_LINKS.forEach { defaultLink ->
            val normalizedUrl = KioskWebLink.normalizeUrl(defaultLink.url) ?: return@forEach
            val exists = links.any { it.url.equals(normalizedUrl, ignoreCase = true) }
            if (!exists) {
                links.add(
                    KioskWebLink(
                        id = UUID.randomUUID().toString(),
                        name = defaultLink.name,
                        url = normalizedUrl,
                    ),
                )
                defaultsAdded = true
            }
        }

        if (defaultsAdded) {
            persist(context, links)
        }

        return links
    }

    fun persist(context: Context, links: List<KioskWebLink>) {
        val arr = JSONArray()
        links.forEach { link ->
            val obj = JSONObject()
            obj.put("id", link.id)
            obj.put("name", link.name)
            obj.put("url", link.url)
            arr.put(obj)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WEB_LINKS_JSON, arr.toString())
            .apply()
    }
}

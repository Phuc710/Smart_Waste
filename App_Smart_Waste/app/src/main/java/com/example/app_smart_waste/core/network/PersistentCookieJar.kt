package com.example.app_smart_waste.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "smartwaste_secure_cookies_storage",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("smartwaste_cookies_storage", Context.MODE_PRIVATE)
    }

    // Map: Host/Domain Key -> (CookieName -> Cookie)
    private val memoryStore: MutableMap<String, MutableMap<String, Cookie>> = mutableMapOf()

    init {
        loadFromPreferences()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val hostMap = memoryStore.getOrPut(host) { mutableMapOf() }

        var changed = false
        for (cookie in cookies) {
            // Remove expired cookie or update/add
            if (cookie.expiresAt <= System.currentTimeMillis()) {
                if (hostMap.remove(cookie.name) != null) {
                    changed = true
                }
            } else {
                hostMap[cookie.name] = cookie
                changed = true
            }
        }

        if (changed) {
            persistHostToPreferences(host)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val validCookies = mutableListOf<Cookie>()
        val expiredEntries = mutableListOf<Pair<String, String>>() // (hostKey, cookieName)

        for ((hostKey, hostMap) in memoryStore) {
            for ((name, cookie) in hostMap) {
                if (cookie.expiresAt <= now) {
                    expiredEntries.add(hostKey to name)
                } else if (cookie.matches(url)) {
                    validCookies.add(cookie)
                }
            }
        }

        if (expiredEntries.isNotEmpty()) {
            val affectedHosts = mutableSetOf<String>()
            for ((hostKey, name) in expiredEntries) {
                memoryStore[hostKey]?.remove(name)
                affectedHosts.add(hostKey)
            }
            for (hostKey in affectedHosts) {
                persistHostToPreferences(hostKey)
            }
        }

        return validCookies
    }

    @Synchronized
    fun getCookieHeader(url: HttpUrl): String? = loadForRequest(url)
        .joinToString("; ") { "${it.name}=${it.value}" }
        .takeIf { it.isNotBlank() }

    @Synchronized
    fun clear() {
        memoryStore.clear()
        prefs.edit().clear().apply()
    }

    private fun persistHostToPreferences(host: String) {
        val hostMap = memoryStore[host]
        val editor = prefs.edit()
        if (hostMap.isNullOrEmpty()) {
            editor.remove(host)
        } else {
            val jsonArray = JSONArray()
            for (cookie in hostMap.values) {
                val json = JSONObject().apply {
                    put("name", cookie.name)
                    put("value", cookie.value)
                    put("expiresAt", cookie.expiresAt)
                    put("domain", cookie.domain)
                    put("path", cookie.path)
                    put("secure", cookie.secure)
                    put("httpOnly", cookie.httpOnly)
                    put("hostOnly", cookie.hostOnly)
                }
                jsonArray.put(json)
            }
            editor.putString(host, jsonArray.toString())
        }
        editor.apply()
    }

    private fun loadFromPreferences() {
        memoryStore.clear()
        val allEntries = prefs.all
        val now = System.currentTimeMillis()

        for ((host, rawValue) in allEntries) {
            if (rawValue !is String) continue
            try {
                val jsonArray = JSONArray(rawValue)
                val hostMap = mutableMapOf<String, Cookie>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val expiresAt = obj.optLong("expiresAt", 0L)
                    if (expiresAt > now) {
                        val name = obj.getString("name")
                        val value = obj.getString("value")
                        val domain = obj.getString("domain")
                        val path = obj.optString("path", "/")
                        val secure = obj.optBoolean("secure", false)
                        val httpOnly = obj.optBoolean("httpOnly", false)
                        val hostOnly = obj.optBoolean("hostOnly", false)

                        val builder = Cookie.Builder()
                            .name(name)
                            .value(value)
                            .expiresAt(expiresAt)
                            .path(path)

                        if (hostOnly) {
                            builder.hostOnlyDomain(domain)
                        } else {
                            builder.domain(domain)
                        }

                        if (secure) builder.secure()
                        if (httpOnly) builder.httpOnly()

                        val cookie = builder.build()
                        hostMap[name] = cookie
                    }
                }

                if (hostMap.isNotEmpty()) {
                    memoryStore[host] = hostMap
                }
            } catch (_: Exception) {
                // Ignore corrupted entry
            }
        }
    }
}

package com.flipfate.lbsroc.entry

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

internal data class BrowserDestination(val url: String, val credential: String? = null)

internal fun browserDestination(reply: String): BrowserDestination? {
    val body = reply.trimStart()
    if (body.startsWith("{") || body.startsWith("[")) {
        val stream = JSONTokener(body)
        try {
            while (stream.nextClean() != '\u0000') {
                stream.back()
                findRedirect(stream.nextValue())?.let { return BrowserDestination(it) }
            }
        } catch (_: JSONException) {
            return null
        }
        return null
    }

    val parts = reply.split('#', limit = 2)
    if (parts.size != 2 || parts.any { it.isBlank() }) return null
    return BrowserDestination(parts[1], parts[0])
}

private fun findRedirect(value: Any?): String? {
    when (value) {
        is JSONObject -> {
            val candidate = (value.opt("rdstr") as? String)?.trim()
            if (candidate != null && candidate.toHttpUrlOrNull() != null) return candidate
            for (key in value.keys()) {
                findRedirect(value.opt(key))?.let { return it }
            }
        }
        is JSONArray -> for (index in 0 until value.length()) {
            findRedirect(value.opt(index))?.let { return it }
        }
    }
    return null
}

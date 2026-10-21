package com.gameshield.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Api {
    // >>> PUT YOUR RENDER URL HERE (no slash at the end) <<<
    const val BASE = "https://YOUR-SERVER.onrender.com"

    class Result(val code: Int, val json: JSONObject)

    // blocking: always call from a background thread
    fun request(method: String, path: String, token: String?, body: JSONObject? = null): Result {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 20000
            c.readTimeout = 70000 // free hosting can take ~1 min to wake up
            c.setRequestProperty("Content-Type", "application/json")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.readText() ?: "{}"
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            return Result(code, json)
        } finally {
            c.disconnect()
        }
    }
}

package com.gameshield.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONObject

object Access {
    private val main = Handler(Looper.getMainLooper())

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("g", 0)
    fun token(ctx: Context): String? = prefs(ctx).getString("token", null)

    fun save(ctx: Context, token: String?, u: JSONObject) {
        val status = u.optString("status", "pending")
        val role = u.optString("role", "user")
        val ok = role == "admin" || status == "approved"
        val e = prefs(ctx).edit()
        if (token != null) e.putString("token", token)
        e.putString("email", u.optString("email"))
            .putString("status", status)
            .putString("role", role)
            .putBoolean("allowed", ok)
            .apply()
        GameState.allowed = ok
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove("token").remove("email").remove("status").remove("role")
            .putBoolean("allowed", false).apply()
        GameState.allowed = false
        GameState.active = false
    }

    // asks the server for the current approval status
    fun refresh(ctx: Context, done: (String, String) -> Unit, fail: (String) -> Unit) {
        val t = token(ctx)
        if (t == null) { fail("signed_out"); return }
        Thread {
            try {
                val r = Api.request("GET", "/api/me", t)
                main.post {
                    if (r.code == 200) {
                        val u = r.json.optJSONObject("user")
                        if (u != null) {
                            save(ctx, null, u)
                            done(u.optString("status", "pending"), u.optString("role", "user"))
                        } else fail("Bad server reply")
                    } else if (r.code == 401) {
                        clear(ctx)
                        fail("signed_out")
                    } else fail("Server error " + r.code)
                }
            } catch (e: Exception) {
                main.post { fail(e.message ?: "No connection") }
            }
        }.start()
    }
}

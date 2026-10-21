package com.gameshield.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class GateActivity : AppCompatActivity() {
    private lateinit var info: TextView
    private lateinit var email: EditText
    private lateinit var pass: EditText
    private lateinit var bIn: Button
    private lateinit var bUp: Button
    private lateinit var bRefresh: Button
    private lateinit var bOut: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = Color.parseColor("#12121F")
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(48, 120, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "GameShield"
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        info = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(0, 24, 0, 24)
        }
        root.addView(info)
        email = field("Email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        pass = field("Password (6+ characters)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        bIn = btn("Sign in") { auth("/api/auth/login") }
        bUp = btn("Request access (new user)") { auth("/api/auth/register") }
        bRefresh = btn("Refresh") { route() }
        bOut = btn("Sign out") {
            Access.clear(this)
            showLogin("")
        }
        listOf<View>(email, pass, bIn, bUp, bRefresh, bOut).forEach {
            root.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(root)
        route()
    }

    private fun field(h: String, type: Int) = EditText(this).apply {
        hint = h
        inputType = type
        setTextColor(Color.WHITE)
        setHintTextColor(Color.GRAY)
    }

    private fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun show(msg: String, fields: Boolean, login: Boolean, wait: Boolean) {
        info.text = msg
        fun vis(v: View, on: Boolean) { v.visibility = if (on) View.VISIBLE else View.GONE }
        vis(email, fields); vis(pass, fields)
        vis(bIn, login); vis(bUp, login)
        vis(bRefresh, wait); vis(bOut, wait)
    }

    private fun showLogin(msg: String) =
        show(if (msg.isEmpty()) "Sign in, or request access. The admin must approve you before the app works." else msg, true, true, false)

    private fun showWaiting(msg: String) = show(msg, false, false, true)
    private fun busy(msg: String) = show(msg, false, false, false)

    private fun go() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun auth(path: String) {
        val e = email.text.toString().trim()
        val p = pass.text.toString()
        if (e.isEmpty() || p.isEmpty()) { info.text = "Enter your email and password."; return }
        busy("Contacting server... (the first request can take up to a minute)")
        Thread {
            try {
                val body = JSONObject().put("email", e).put("password", p)
                val r = Api.request("POST", path, null, body)
                runOnUiThread {
                    val u = r.json.optJSONObject("user")
                    if ((r.code == 200 || r.code == 201) && u != null) {
                        Access.save(this, r.json.optString("token"), u)
                        route()
                    } else {
                        showLogin(r.json.optString("error", "Failed (code " + r.code + ")"))
                    }
                }
            } catch (ex: Exception) {
                runOnUiThread { showLogin("No connection: " + ex.message) }
            }
        }.start()
    }

    private fun route() {
        if (Access.token(this) == null) { showLogin(""); return }
        busy("Checking your access... (the first request can take up to a minute)")
        Access.refresh(this, { status, role ->
            when {
                role == "admin" || status == "approved" -> go()
                status == "denied" -> showWaiting("The admin denied your request.")
                else -> showWaiting("Request sent. Waiting for the admin to approve you.\n\nTap Refresh after they approve.")
            }
        }, { err ->
            when {
                err == "signed_out" -> showLogin("Please sign in again.")
                Access.prefs(this).getBoolean("allowed", false) -> go() // offline but already approved
                else -> showWaiting("Can't reach the server ($err). Check your internet, then tap Refresh.")
            }
        })
    }
}

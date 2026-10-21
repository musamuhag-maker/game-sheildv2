package com.gameshield.app

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class AdminActivity : AppCompatActivity() {
    class U(val id: String, val email: String, val status: String, val role: String)

    private val users = ArrayList<U>()
    private lateinit var adapter: ArrayAdapter<U>
    private lateinit var title: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = Color.parseColor("#12121F")
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(32, 48, 32, 16)
        }
        title = TextView(this).apply {
            text = "Admin panel"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title)
        root.addView(Button(this).apply {
            text = "Refresh"
            isAllCaps = false
            setOnClickListener { load() }
        })
        root.addView(TextView(this).apply {
            text = "Tap a person to approve or deny them."
            setTextColor(Color.GRAY)
            textSize = 12f
        })

        val list = ListView(this).apply { divider = null }
        adapter = object : ArrayAdapter<U>(this, 0, users) {
            override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                val u = users[p]
                return TextView(context).apply {
                    val label = if (u.role == "admin") "admin" else u.status
                    text = u.email + "\n" + label
                    textSize = 16f
                    setPadding(8, 24, 8, 24)
                    setTextColor(
                        when {
                            u.role == "admin" -> Color.CYAN
                            u.status == "approved" -> Color.parseColor("#00E5A0")
                            u.status == "denied" -> Color.parseColor("#FF6B6B")
                            else -> Color.parseColor("#FFC107")
                        }
                    )
                }
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val u = users[pos]
            if (u.role == "admin") {
                Toast.makeText(this, "That is an admin account", Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(u.email)
                    .setItems(arrayOf("Approve", "Deny", "Set back to pending")) { _, which ->
                        setStatus(u, arrayOf("approved", "denied", "pending")[which])
                    }
                    .show()
            }
        }
        root.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        load()
    }

    private fun load() {
        title.text = "Loading..."
        Thread {
            try {
                val r = Api.request("GET", "/api/admin/users", Access.token(this))
                runOnUiThread {
                    if (r.code == 200) {
                        users.clear()
                        val arr = r.json.getJSONArray("users")
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            users.add(U(o.getString("id"), o.getString("email"), o.getString("status"), o.getString("role")))
                        }
                        users.sortWith(compareBy<U>({ if (it.status == "pending" && it.role != "admin") 0 else 1 }, { it.email }))
                        adapter.notifyDataSetChanged()
                        val waiting = users.count { it.status == "pending" && it.role != "admin" }
                        title.text = "Admin panel ($waiting waiting)"
                    } else {
                        title.text = r.json.optString("error", "Error " + r.code)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { title.text = "No connection" }
            }
        }.start()
    }

    private fun setStatus(u: U, status: String) {
        Thread {
            try {
                val r = Api.request("POST", "/api/admin/users/" + u.id + "/status", Access.token(this), JSONObject().put("status", status))
                runOnUiThread {
                    if (r.code == 200) load()
                    else Toast.makeText(this, r.json.optString("error", "Failed"), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "No connection", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}

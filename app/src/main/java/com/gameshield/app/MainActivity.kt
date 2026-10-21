package com.gameshield.app

import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    class App(val pkg: String, val label: String, val icon: Drawable)

    private lateinit var prefs: SharedPreferences
    private lateinit var tvAcc: TextView
    private lateinit var tvRole: TextView
    private lateinit var tvLive: TextView
    private lateinit var adapter: ArrayAdapter<App>
    private val apps = ArrayList<App>()
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }
    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { updateStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("g", 0)
        GameState.load(this)
        val bg = Color.parseColor("#12121F")
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(32, 48, 32, 16)
        }
        root.addView(label("GameShield", 28f, Color.WHITE, true))
        root.addView(label("Rejects incoming calls while your chosen games are open.", 14f, Color.LTGRAY, false))

        val role = prefs.getString("role", "user")
        val who = prefs.getString("email", "") ?: ""
        root.addView(label("Signed in: " + who + (if (role == "admin") " (admin)" else ""), 12f, Color.GRAY, false).apply { setPadding(0, 8, 0, 0) })
        val bar = LinearLayout(this)
        if (role == "admin") {
            bar.addView(button("Admin panel") { startActivity(Intent(this, AdminActivity::class.java)) }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        bar.addView(button("Sign out") { signOut() }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(bar)

        tvAcc = label("", 16f, Color.WHITE, true).apply { setPadding(0, 32, 0, 0) }
        root.addView(tvAcc)
        root.addView(button("1. Turn on game detection") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(label(
            "Switch is greyed out? Settings > Apps > Manage apps > GameShield > ⋮ (top right) > Allow restricted settings, then try again.",
            12f, Color.GRAY, false
        ))

        tvRole = label("", 16f, Color.WHITE, true).apply { setPadding(0, 32, 0, 0) }
        root.addView(tvRole)
        root.addView(button("2. Set as call screening app") {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            } else {
                Toast.makeText(this, "Call screening is not available on this phone", Toast.LENGTH_LONG).show()
            }
        })

        tvLive = label("", 14f, Color.parseColor("#00E5A0"), false).apply { setPadding(0, 24, 0, 0) }
        root.addView(tvLive)

        root.addView(label("3. Pick your games", 16f, Color.WHITE, true).apply { setPadding(0, 32, 0, 8) })

        val list = ListView(this).apply { divider = null }
        adapter = object : ArrayAdapter<App>(this, 0, apps) {
            override fun getView(p: Int, v: View?, parent: ViewGroup): View {
                val a = apps[p]
                val row = LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(8, 14, 8, 14)
                }
                row.addView(ImageView(context).apply { setImageDrawable(a.icon) }, LinearLayout.LayoutParams(100, 100))
                row.addView(TextView(context).apply {
                    text = a.label
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setPadding(32, 0, 0, 0)
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                row.addView(CheckBox(context).apply {
                    isChecked = a.pkg in GameState.games
                    isClickable = false
                    isFocusable = false
                })
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val a = apps[pos]
            val s = GameState.games.toMutableSet()
            if (!s.add(a.pkg)) s.remove(a.pkg)
            prefs.edit().putStringSet("games", s).apply()
            GameState.games = s
            adapter.notifyDataSetChanged()
        }
        root.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(root)
        loadApps()
    }

    private fun label(t: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun loadApps() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps.clear()
        apps.addAll(
            packageManager.queryIntentActivities(i, 0)
                .map { App(it.activityInfo.packageName, it.loadLabel(packageManager).toString(), it.loadIcon(packageManager)) }
                .filter { it.pkg != packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        )
        adapter.notifyDataSetChanged()
    }

    private fun accessibilityOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains(packageName)
    }

    private fun updateStatus() {
        val acc = GameState.alive || accessibilityOn()
        tvAcc.text = if (acc) "1. Game detection: ON ✓" else "1. Game detection: OFF ✗"
        tvAcc.setTextColor(if (acc) Color.parseColor("#00E5A0") else Color.parseColor("#FF6B6B"))
        val role = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        tvRole.text = if (role) "2. Call blocking: ON ✓" else "2. Call blocking: OFF ✗"
        tvRole.setTextColor(if (role) Color.parseColor("#00E5A0") else Color.parseColor("#FF6B6B"))
        tvLive.text = if (GameState.active) "In a game right now: calls will be rejected"
        else "No chosen game open: calls ring normally"
    }

    private fun toGate() {
        startActivity(Intent(this, GateActivity::class.java))
        finish()
    }

    private fun signOut() {
        Access.clear(this)
        toGate()
    }

    override fun onResume() {
        super.onResume()
        if (Access.token(this) == null) { toGate(); return }
        // re-check approval every time the app is opened
        Access.refresh(this, { status, r ->
            if (!(r == "admin" || status == "approved")) toGate()
        }, { err ->
            if (err == "signed_out") toGate()
        })
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }
}

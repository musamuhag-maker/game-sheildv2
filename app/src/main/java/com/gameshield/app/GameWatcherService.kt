package com.gameshield.app

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

class GameWatcherService : AccessibilityService() {

    // overlays that should not count as "leaving the game"
    private val ignore = setOf(
        "android", "com.android.systemui", "com.miui.gamebooster",
        "com.miui.securitycenter", "com.miui.notification"
    )
    private var imePkgs: Set<String> = emptySet()
    private var receiverRegistered = false

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            GameState.active = false   // phone locked = not gaming, let calls ring
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        GameState.load(this)
        GameState.alive = true
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imePkgs = imm.enabledInputMethodList.map { it.packageName }.toSet()
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignore || pkg in imePkgs) return
        GameState.active = pkg in GameState.games
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        GameState.alive = false
        GameState.active = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(screenOff) } catch (e: Exception) { }
            receiverRegistered = false
        }
        super.onDestroy()
    }
}

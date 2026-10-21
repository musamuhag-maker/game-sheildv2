package com.gameshield.app

import android.content.Context

object GameState {
    @Volatile var active = false          // a chosen game is in the foreground right now
    @Volatile var allowed = false         // admin approved this account
    @Volatile var alive = false           // accessibility service is connected
    @Volatile var games: Set<String> = emptySet()

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("g", 0)
        games = p.getStringSet("games", emptySet())!!.toSet()
        allowed = p.getBoolean("allowed", false)
    }
}

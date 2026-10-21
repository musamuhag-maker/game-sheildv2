package com.gameshield.app

import android.telecom.Call
import android.telecom.CallScreeningService

class GameCallScreener : CallScreeningService() {
    override fun onCreate() {
        super.onCreate()
        GameState.load(this)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val response = CallResponse.Builder()
        if (GameState.allowed && GameState.active && callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
            response
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(false) // you still see "missed call" afterwards
        }
        respondToCall(callDetails, response.build())
    }
}

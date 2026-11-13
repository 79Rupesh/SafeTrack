package com.safetrack.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ServiceStopActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LocationTrackingService.ACTION_STOP_SHARING -> {
                LocationTrackingService.stopService(context)
            }
            LocationTrackingService.ACTION_PAUSE_SHARING -> {
                LocationTrackingService.pauseService(context)
            }
            LocationTrackingService.ACTION_RESUME_SHARING -> {
                LocationTrackingService.resumeService(context)
            }
        }
    }
}

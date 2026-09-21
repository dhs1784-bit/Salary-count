package com.example.severancecounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SeverancePrefs.isRunning(context)) return

        val serviceIntent = Intent(context, SeveranceForegroundService::class.java)
            .setAction(SeveranceForegroundService.ACTION_START)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

package com.example.loancounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!LoanPrefs.isRunning(context)) return

        val serviceIntent = Intent(context, LoanForegroundService::class.java)
            .setAction(LoanForegroundService.ACTION_START)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

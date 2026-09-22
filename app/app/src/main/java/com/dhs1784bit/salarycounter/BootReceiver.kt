package com.dhs1784bit.salarycounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!SalaryPrefs.isRunning(context)) return

        val serviceIntent = Intent(context, SalaryForegroundService::class.java)
            .setAction(SalaryForegroundService.ACTION_START)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

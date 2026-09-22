package com.dhs1784bit.salarycounter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dhs1784bit.salarycounter.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCounting()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editSalary.setText(SalaryPrefs.getAnnualManwon(this).toString())
        binding.editPayday.setText(SalaryPrefs.getPayday(this).toString())

        binding.btnStart.setOnClickListener {
            val manwon = binding.editSalary.text.toString().toIntOrNull() ?: 6000
            val payday = (binding.editPayday.text.toString().toIntOrNull() ?: 25).coerceIn(1, 31)
            SalaryPrefs.setAnnualManwon(this, manwon)
            SalaryPrefs.setPayday(this, payday)
            ensureNotificationPermissionThenStart()
        }

        binding.btnStop.setOnClickListener {
            val intent = Intent(this, SalaryForegroundService::class.java)
                .setAction(SalaryForegroundService.ACTION_STOP)
            startService(intent)
            binding.statusText.text = "중지됨"
        }

        updateStatusText()
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
    }

    private fun updateStatusText() {
        binding.statusText.text = if (SalaryPrefs.isRunning(this)) {
            "카운팅 중 · 잠금화면 알림을 확인해보세요"
        } else {
            ""
        }
    }

    private fun ensureNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startCounting()
    }

    private fun startCounting() {
        val intent = Intent(this, SalaryForegroundService::class.java)
            .setAction(SalaryForegroundService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        binding.statusText.text = "카운팅 중 · 잠금화면 알림을 확인해보세요"
    }
}

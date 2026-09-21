package com.example.severancecounter

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.severancecounter.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedHireDateMillis: Long = 0L
    private val dateFormat = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA)

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCounting()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editSeverance.setText(SeverancePrefs.getAnnualManwon(this).toString())

        selectedHireDateMillis = SeverancePrefs.getHireDateMillis(this)
        updateHireDateText()

        binding.editHireDate.setOnClickListener { showDatePicker() }

        binding.btnStart.setOnClickListener {
            val manwon = binding.editSeverance.text.toString().toIntOrNull() ?: 6000
            SeverancePrefs.setAnnualManwon(this, manwon)
            SeverancePrefs.setHireDateMillis(this, startOfDay(selectedHireDateMillis))
            ensureNotificationPermissionThenStart()
        }

        binding.btnStop.setOnClickListener {
            val intent = Intent(this, SeveranceForegroundService::class.java)
                .setAction(SeveranceForegroundService.ACTION_STOP)
            startService(intent)
            binding.statusText.text = "중지됨"
        }

        updateStatusText()
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedHireDateMillis }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance()
                picked.set(year, month, day, 0, 0, 0)
                selectedHireDateMillis = picked.timeInMillis
                updateHireDateText()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun updateHireDateText() {
        binding.editHireDate.setText(dateFormat.format(selectedHireDateMillis))
    }

    private fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun updateStatusText() {
        binding.statusText.text = if (SeverancePrefs.isRunning(this)) {
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
        val intent = Intent(this, SeveranceForegroundService::class.java)
            .setAction(SeveranceForegroundService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        binding.statusText.text = "카운팅 중 · 잠금화면 알림을 확인해보세요"
    }
}

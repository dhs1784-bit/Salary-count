package com.example.loancounter

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.loancounter.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedStartDateMillis: Long = 0L
    private val dateFormat = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA)

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCounting()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editPrincipal.setText(LoanPrefs.getPrincipalManwon(this).toString())
        binding.editRate.setText((LoanPrefs.getRateBp(this) / 100.0).toString())
        binding.editTerm.setText(LoanPrefs.getTermMonths(this).toString())

        selectedStartDateMillis = LoanPrefs.getLoanStartMillis(this)
        updateStartDateText()

        binding.editStartDate.setOnClickListener { showDatePicker() }

        binding.btnStart.setOnClickListener {
            val principal = binding.editPrincipal.text.toString().toLongOrNull() ?: 3000
            val ratePercent = binding.editRate.text.toString().toDoubleOrNull() ?: 4.5
            val term = binding.editTerm.text.toString().toIntOrNull() ?: 60

            LoanPrefs.setPrincipalManwon(this, principal)
            LoanPrefs.setRateBp(this, (ratePercent * 100).toInt())
            LoanPrefs.setTermMonths(this, term)
            LoanPrefs.setLoanStartMillis(this, startOfDay(selectedStartDateMillis))
            ensureNotificationPermissionThenStart()
        }

        binding.btnStop.setOnClickListener {
            val intent = Intent(this, LoanForegroundService::class.java)
                .setAction(LoanForegroundService.ACTION_STOP)
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
        val cal = Calendar.getInstance().apply { timeInMillis = selectedStartDateMillis }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance()
                picked.set(year, month, day, 0, 0, 0)
                selectedStartDateMillis = picked.timeInMillis
                updateStartDateText()
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateStartDateText() {
        binding.editStartDate.setText(dateFormat.format(selectedStartDateMillis))
    }

    private fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun updateStatusText() {
        binding.statusText.text = if (LoanPrefs.isRunning(this)) {
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
        val intent = Intent(this, LoanForegroundService::class.java)
            .setAction(LoanForegroundService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        binding.statusText.text = "카운팅 중 · 잠금화면 알림을 확인해보세요"
    }
}

package com.example.salarycounter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * 잠금화면 알림(상시표시 알림) 형태로 "이번 급여 주기에 번 돈"과 해금 진행 상황을 보여주는 서비스.
 * 안드로이드는 iOS와 달리 잠금화면 전용 위젯이 없어서, 알림이 잠금화면에 노출되는
 * 방식(설정 > 알림 > 잠금화면에 모든 알림 내용 표시)을 이용한다.
 */
class SalaryForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    companion object {
        const val CHANNEL_ONGOING = "salary_ongoing"
        const val CHANNEL_UNLOCK = "salary_unlock"
        const val NOTIF_ID_ONGOING = 1001
        const val NOTIF_ID_UNLOCK = 1002

        const val ACTION_START = "com.example.salarycounter.action.START"
        const val ACTION_STOP = "com.example.salarycounter.action.STOP"
        const val ACTION_TOGGLE_PRIVACY = "com.example.salarycounter.action.TOGGLE_PRIVACY"

        // 1초마다 갱신. 배터리가 걱정되면 이 값을 3000~5000으로 늘리면 됨.
        const val TICK_INTERVAL_MS = 250L
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTicking()
                SalaryPrefs.setRunning(this, false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PRIVACY -> {
                SalaryPrefs.setPrivateMode(this, !SalaryPrefs.isPrivateMode(this))
                tickOnce()
                return START_STICKY
            }
        }

        SalaryPrefs.setRunning(this, true)
        startForeground(NOTIF_ID_ONGOING, buildOngoingNotification())
        startTicking()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTicking()
        super.onDestroy()
    }

    private fun startTicking() {
        stopTicking()
        val runnable = object : Runnable {
            override fun run() {
                tickOnce()
                handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
        tickRunnable = runnable
        handler.post(runnable)
    }

    private fun stopTicking() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun tickOnce() {
        val earned = currentEarned()

        val idx = SalaryTiers.currentIndex(earned)
        val lastIdx = SalaryPrefs.getLastUnlockedIdx(this)
        if (idx > lastIdx) {
            SalaryPrefs.setLastUnlockedIdx(this, idx)
            if (idx >= 0) notifyUnlock(SalaryTiers.items[idx])
        }

        val notifManager = getSystemService(NotificationManager::class.java)
        notifManager.notify(NOTIF_ID_ONGOING, buildOngoingNotification())

        updateWidgets(earned)
    }

    private fun currentEarned(): Long {
        val annual = SalaryPrefs.getAnnualManwon(this)
        val periodStart = SalaryPrefs.periodStartMillis(this)
        val periodEnd = SalaryPrefs.nextPaydayMillis(this)
        return SalaryCalculator.earnedThisPeriod(annual, periodStart, periodEnd, System.currentTimeMillis())
    }

    private fun updateWidgets(earned: Long) {
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(
            android.content.ComponentName(this, SalaryWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) {
            SalaryWidgetProvider.updateAll(this, mgr, ids, earned)
        }
    }

    private fun buildOngoingNotification(): Notification {
        val annual = SalaryPrefs.getAnnualManwon(this)
        val periodStart = SalaryPrefs.periodStartMillis(this)
        val periodEnd = SalaryPrefs.nextPaydayMillis(this)
        val now = System.currentTimeMillis()
        val earned = SalaryCalculator.earnedThisPeriod(annual, periodStart, periodEnd, now)
        val perSec = SalaryCalculator.perSecondWon(annual, periodStart, periodEnd)
        val dday = SalaryCalculator.daysUntilNextPayday(periodEnd, now)
        val hidden = SalaryPrefs.isPrivateMode(this)

        val idx = SalaryTiers.currentIndex(earned)
        val (tierLine, nextLine) = tierText(idx, earned)
        val perSecLine = "초당 ${SalaryCalculator.formatWon(perSec)}원"
        val ddayLine = if (dday <= 0) "오늘 월급날! 🎊" else "월급날까지 D-$dday"

        val amountText = if (hidden) "•••••• 원" else "${SalaryCalculator.formatWon(earned)}원"

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SalaryForegroundService::class.java).setAction(ACTION_TOGGLE_PRIVACY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, SalaryForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bigText = if (hidden) {
            "탭해서 확인하기 🔒\n$ddayLine"
        } else {
            "$tierLine\n$nextLine\n$perSecLine · $ddayLine"
        }

        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("이번 급여 주기에 번 돈  $amountText")
            .setContentText(if (hidden) "탭해서 확인" else "$tierLine · $ddayLine")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openAppIntent)
            .addAction(0, if (hidden) "보기" else "가리기", toggleIntent)
            .addAction(0, "중지", stopIntent)
            .build()
    }

    private fun tierText(idx: Int, earned: Long): Pair<String, String> {
        return if (idx == -1) {
            val first = SalaryTiers.items.first()
            val remain = (first.price - earned).coerceAtLeast(0)
            "🔒 첫 해금까지 조금만" to "다음까지 ${SalaryCalculator.formatWon(remain)}원"
        } else {
            val cur = SalaryTiers.items[idx]
            val next = SalaryTiers.items.getOrNull(idx + 1)
            val line1 = "${cur.emoji} ${cur.name} 해금! (${SalaryCalculator.formatWon(cur.price)}원)"
            val line2 = if (next != null) {
                val remain = (next.price - earned).coerceAtLeast(0)
                "❔ 다음까지 ${SalaryCalculator.formatWon(remain)}원"
            } else {
                "🏁 모든 단계 해금 완료"
            }
            line1 to line2
        }
    }

    private fun notifyUnlock(tier: Tier) {
        val notif = NotificationCompat.Builder(this, CHANNEL_UNLOCK)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("🎉 해금!")
            .setContentText("${tier.emoji} ${tier.name} (${SalaryCalculator.formatWon(tier.price)}원)")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID_UNLOCK, notif)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)

        val ongoing = NotificationChannel(
            CHANNEL_ONGOING, getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }

        val unlock = NotificationChannel(
            CHANNEL_UNLOCK, "해금 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        mgr.createNotificationChannel(ongoing)
        mgr.createNotificationChannel(unlock)
    }
}

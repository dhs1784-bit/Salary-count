package com.example.severancecounter

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
 * 잠금화면 알림(상시표시 알림) 형태로 "추정 퇴직금"이 실시간으로 쌓이는 걸 보여주는 서비스.
 * 월급 버전과 달리 주기 리셋이 없다 — 입사일부터 계속 누적.
 */
class SeveranceForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    companion object {
        const val CHANNEL_ONGOING = "severance_ongoing"
        const val CHANNEL_UNLOCK = "severance_unlock"
        const val NOTIF_ID_ONGOING = 2001
        const val NOTIF_ID_UNLOCK = 2002

        const val ACTION_START = "com.example.severancecounter.action.START"
        const val ACTION_STOP = "com.example.severancecounter.action.STOP"
        const val ACTION_TOGGLE_PRIVACY = "com.example.severancecounter.action.TOGGLE_PRIVACY"

        const val TICK_INTERVAL_MS = 1000L
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTicking()
                SeverancePrefs.setRunning(this, false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PRIVACY -> {
                SeverancePrefs.setPrivateMode(this, !SeverancePrefs.isPrivateMode(this))
                tickOnce()
                return START_STICKY
            }
        }

        SeverancePrefs.setRunning(this, true)
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
        val now = System.currentTimeMillis()
        val annual = SeverancePrefs.getAnnualManwon(this)
        val hireDate = SeverancePrefs.getHireDateMillis(this)
        val earned = SeveranceCalculator.earnedSeverance(annual, hireDate, now)

        // 금액 해금 체크
        val idx = SeveranceTiers.currentIndex(earned)
        val lastIdx = SeverancePrefs.getLastUnlockedIdx(this)
        if (idx > lastIdx) {
            SeverancePrefs.setLastUnlockedIdx(this, idx)
            if (idx >= 0) notifyUnlock("🎉 ${SeveranceTiers.items[idx].name}", "${SeveranceTiers.items[idx].emoji} (${SeveranceCalculator.formatWon(SeveranceTiers.items[idx].price)}원)")
        }

        // 근속 연차 마일스톤 체크
        val years = SeveranceCalculator.tenureYears(hireDate, now)
        val lastYear = SeverancePrefs.getLastTenureYear(this)
        if (years > lastYear) {
            SeverancePrefs.setLastTenureYear(this, years)
            if (years > 0) notifyUnlock("🎊 근속 ${years}년차 도달!", "퇴직금 적립 기반이 한 단계 더 단단해졌어요")
        }

        val notifManager = getSystemService(NotificationManager::class.java)
        notifManager.notify(NOTIF_ID_ONGOING, buildOngoingNotification())
        updateWidgets(earned)
    }

    private fun updateWidgets(earned: Long) {
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(
            android.content.ComponentName(this, SeveranceWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) {
            SeveranceWidgetProvider.updateAll(this, mgr, ids, earned)
        }
    }

    private fun buildOngoingNotification(): Notification {
        val annual = SeverancePrefs.getAnnualManwon(this)
        val hireDate = SeverancePrefs.getHireDateMillis(this)
        val now = System.currentTimeMillis()
        val earned = SeveranceCalculator.earnedSeverance(annual, hireDate, now)
        val perSec = SeveranceCalculator.perSecondWon(annual)
        val tenureDays = SeveranceCalculator.tenureDays(hireDate, now)
        val years = SeveranceCalculator.tenureYears(hireDate, now)
        val dday = SeveranceCalculator.daysUntilNextAnniversary(hireDate, now)
        val hidden = SeverancePrefs.isPrivateMode(this)

        val idx = SeveranceTiers.currentIndex(earned)
        val (tierLine, nextLine) = tierText(idx, earned)
        val perSecLine = "초당 ${SeveranceCalculator.formatWon(perSec)}원"
        val tenureLine = if (tenureDays < 365) {
            "입사 ${tenureDays}일째 · 1년 채우면 퇴직금 발생 시작"
        } else {
            "근속 ${years}년차 · 다음 기념일까지 D-$dday"
        }

        val amountText = if (hidden) "•••••• 원" else "${SeveranceCalculator.formatWon(earned)}원"

        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SeveranceForegroundService::class.java).setAction(ACTION_TOGGLE_PRIVACY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, SeveranceForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bigText = if (hidden) {
            "탭해서 확인하기 🔒\n$tenureLine"
        } else {
            "$tierLine\n$nextLine\n$perSecLine\n$tenureLine"
        }

        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("추정 퇴직금  $amountText")
            .setContentText(if (hidden) "탭해서 확인" else tenureLine)
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
            val first = SeveranceTiers.items.first()
            val remain = (first.price - earned).coerceAtLeast(0)
            "🔒 첫 해금까지 조금만" to "다음까지 ${SeveranceCalculator.formatWon(remain)}원"
        } else {
            val cur = SeveranceTiers.items[idx]
            val next = SeveranceTiers.items.getOrNull(idx + 1)
            val line1 = "${cur.emoji} ${cur.name}"
            val line2 = if (next != null) {
                val remain = (next.price - earned).coerceAtLeast(0)
                "❔ 다음까지 ${SeveranceCalculator.formatWon(remain)}원"
            } else {
                "🏁 모든 단계 달성"
            }
            line1 to line2
        }
    }

    private fun notifyUnlock(title: String, content: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL_UNLOCK)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle(title)
            .setContentText(content)
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

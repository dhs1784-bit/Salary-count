package com.example.loancounter

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
 * 대출 잔액이 실시간으로 줄어드는 걸 잠금화면 알림으로 보여주는 서비스.
 * 월급/퇴직금 버전과 반대로, 숫자가 계속 "감소"한다.
 */
class LoanForegroundService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    companion object {
        const val CHANNEL_ONGOING = "loan_ongoing"
        const val CHANNEL_UNLOCK = "loan_unlock"
        const val NOTIF_ID_ONGOING = 3001
        const val NOTIF_ID_UNLOCK = 3002

        const val ACTION_START = "com.example.loancounter.action.START"
        const val ACTION_STOP = "com.example.loancounter.action.STOP"
        const val ACTION_TOGGLE_PRIVACY = "com.example.loancounter.action.TOGGLE_PRIVACY"

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
                LoanPrefs.setRunning(this, false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_PRIVACY -> {
                LoanPrefs.setPrivateMode(this, !LoanPrefs.isPrivateMode(this))
                tickOnce()
                return START_STICKY
            }
        }

        LoanPrefs.setRunning(this, true)
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

    private data class Snapshot(
        val principalManwon: Long, val rateBp: Int, val termMonths: Int,
        val paymentsElapsed: Int, val periodStart: Long, val periodEnd: Long,
        val remainingExact: Double, val repaidExact: Double, val percent: Double,
        val perSec: Double, val tiers: List<Tier>, val idx: Int
    )

    private fun snapshot(): Snapshot {
        val principal = LoanPrefs.getPrincipalManwon(this)
        val rateBp = LoanPrefs.getRateBp(this)
        val term = LoanPrefs.getTermMonths(this)
        val paymentsElapsed = LoanPrefs.paymentsElapsed(this)
        val periodStart = LoanPrefs.periodStartMillis(this)
        val periodEnd = LoanPrefs.nextPaymentMillis(this)
        val now = System.currentTimeMillis()

        val remaining = LoanCalculator.remainingBalanceExact(principal, rateBp, term, paymentsElapsed, periodStart, periodEnd, now)
        val repaid = LoanCalculator.repaidSoFarExact(principal, remaining)
        val percent = LoanCalculator.percentRepaid(principal, remaining)
        val perSec = LoanCalculator.perSecondWon(principal, rateBp, term, paymentsElapsed, periodStart, periodEnd)
        val tiers = LoanTiers.forPrincipal(principal)
        val idx = LoanTiers.currentIndex(tiers, repaid.toLong())

        return Snapshot(principal, rateBp, term, paymentsElapsed, periodStart, periodEnd, remaining, repaid, percent, perSec, tiers, idx)
    }

    private fun tickOnce() {
        val s = snapshot()

        val lastIdx = LoanPrefs.getLastUnlockedIdx(this)
        if (s.idx > lastIdx) {
            LoanPrefs.setLastUnlockedIdx(this, s.idx)
            if (s.idx >= 0) {
                val t = s.tiers[s.idx]
                notifyUnlock("${t.emoji} ${t.name}", "원금의 ${LoanCalculator.formatWon(t.price)}원 상환 달성")
            }
        }

        val notifManager = getSystemService(NotificationManager::class.java)
        notifManager.notify(NOTIF_ID_ONGOING, buildOngoingNotification())
        updateWidgets()
    }

    private fun updateWidgets() {
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(
            android.content.ComponentName(this, LoanWidgetProvider::class.java)
        )
        if (ids.isNotEmpty()) {
            LoanWidgetProvider.updateAll(this, mgr, ids)
        }
    }

    private fun buildOngoingNotification(): Notification {
        val s = snapshot()
        val hidden = LoanPrefs.isPrivateMode(this)
        val now = System.currentTimeMillis()
        val dday = LoanCalculator.daysUntilNextPayment(s.periodEnd, now)

        val (tierLine, nextLine) = tierText(s)
        val perSecLine = "초당 -${LoanCalculator.formatWon1(s.perSec)}원"
        val ddayLine = if (s.remainingExact <= 0.01) "🎉 완납했어요!" else "다음 상환일까지 D-$dday · 상환 ${"%.1f".format(s.percent)}%"

        val amountText = if (hidden) "•••••• 원" else "${LoanCalculator.formatWon1(s.remainingExact)}원"

        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LoanForegroundService::class.java).setAction(ACTION_TOGGLE_PRIVACY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, LoanForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bigText = if (hidden) {
            "탭해서 확인하기 🔒\n$ddayLine"
        } else {
            "$tierLine\n$nextLine\n$perSecLine\n$ddayLine"
        }

        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentTitle("대출 잔액  $amountText")
            .setContentText(if (hidden) "탭해서 확인" else ddayLine)
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

    private fun tierText(s: Snapshot): Pair<String, String> {
        if (s.remainingExact <= 0.01) return "🏁 완납!" to "축하해요, 이제 자유예요"
        return if (s.idx == -1) {
            val first = s.tiers.first()
            val remain = (first.price - s.repaidExact).coerceAtLeast(0.0)
            "🔒 첫 상환 진행 중" to "다음까지 ${LoanCalculator.formatWon(remain)}원"
        } else {
            val cur = s.tiers[s.idx]
            val next = s.tiers.getOrNull(s.idx + 1)
            val line1 = "${cur.emoji} ${cur.name}"
            val line2 = if (next != null) {
                val remain = (next.price - s.repaidExact).coerceAtLeast(0.0)
                "❔ 다음까지 ${LoanCalculator.formatWon(remain)}원"
            } else {
                "🏁 완납 직전"
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
            CHANNEL_UNLOCK, "상환 알림",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        mgr.createNotificationChannel(ongoing)
        mgr.createNotificationChannel(unlock)
    }
}

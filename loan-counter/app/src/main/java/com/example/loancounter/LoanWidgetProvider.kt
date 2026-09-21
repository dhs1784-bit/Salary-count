package com.example.loancounter

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class LoanWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAll(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            val principal = LoanPrefs.getPrincipalManwon(context)
            val rateBp = LoanPrefs.getRateBp(context)
            val term = LoanPrefs.getTermMonths(context)
            val paymentsElapsed = LoanPrefs.paymentsElapsed(context)
            val periodStart = LoanPrefs.periodStartMillis(context)
            val periodEnd = LoanPrefs.nextPaymentMillis(context)
            val now = System.currentTimeMillis()

            val remaining = LoanCalculator.remainingBalanceExact(principal, rateBp, term, paymentsElapsed, periodStart, periodEnd, now)
            val repaid = LoanCalculator.repaidSoFarExact(principal, remaining)
            val percent = LoanCalculator.percentRepaid(principal, remaining)
            val perSec = LoanCalculator.perSecondWon(principal, rateBp, term, paymentsElapsed, periodStart, periodEnd)
            val tiers = LoanTiers.forPrincipal(principal)
            val idx = LoanTiers.currentIndex(tiers, repaid.toLong())
            val dday = LoanCalculator.daysUntilNextPayment(periodEnd, now)

            val hidden = LoanPrefs.isPrivateMode(context)
            val paidOff = remaining <= 0.01

            val amountText = if (hidden) "•••••• 원" else "${LoanCalculator.formatWon1(remaining)}원"
            val perSecText = if (hidden) "· 초당 ••원" else "· 초당 -${LoanCalculator.formatWon1(perSec)}원"
            val ddayText = if (paidOff) "🏁 완납" else "D-$dday"
            val labelText = if (paidOff) "대출 잔액" else "상환 ${"%.0f".format(percent)}%"

            val (tierLine, nextLine) = if (hidden) {
                "🔒 탭해서 확인" to ""
            } else if (paidOff) {
                "🏁 완납!" to "축하해요, 이제 자유예요"
            } else if (idx == -1) {
                val first = tiers.first()
                val remain = (first.price - repaid).coerceAtLeast(0.0)
                "🔒 첫 상환 진행 중" to "다음까지 ${LoanCalculator.formatWon(remain)}원"
            } else {
                val cur = tiers[idx]
                val next = tiers.getOrNull(idx + 1)
                val line1 = "${cur.emoji} ${cur.name}"
                val line2 = if (next != null) {
                    val remain = (next.price - repaid).coerceAtLeast(0.0)
                    "❔ 다음까지 ${LoanCalculator.formatWon(remain)}원"
                } else {
                    "🏁 완납 직전"
                }
                line1 to line2
            }

            val openAppIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val toggleIntent = PendingIntent.getService(
                context, 3,
                Intent(context, LoanForegroundService::class.java)
                    .setAction(LoanForegroundService.ACTION_TOGGLE_PRIVACY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_loan)
                views.setTextViewText(R.id.widget_label, labelText)
                views.setTextViewText(R.id.widget_dday, ddayText)
                views.setTextViewText(R.id.widget_amount, amountText)
                views.setTextViewText(R.id.widget_persec, perSecText)
                views.setTextViewText(R.id.widget_tier, tierLine)
                views.setTextViewText(R.id.widget_next, nextLine)
                views.setOnClickPendingIntent(R.id.widget_root, toggleIntent)
                views.setOnClickPendingIntent(R.id.widget_amount, openAppIntent)
                mgr.updateAppWidget(id, views)
            }
        }
    }
}

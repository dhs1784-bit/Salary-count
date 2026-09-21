package com.example.salarycounter

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SalaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val earned = currentEarned(context)
        updateAll(context, appWidgetManager, appWidgetIds, earned)
    }

    companion object {
        private fun currentEarned(context: Context): Long {
            val annual = SalaryPrefs.getAnnualManwon(context)
            val periodStart = SalaryPrefs.periodStartMillis(context)
            val periodEnd = SalaryPrefs.nextPaydayMillis(context)
            return SalaryCalculator.earnedThisPeriod(annual, periodStart, periodEnd, System.currentTimeMillis())
        }

        fun updateAll(
            context: Context,
            mgr: AppWidgetManager,
            ids: IntArray,
            earned: Long
        ) {
            val annual = SalaryPrefs.getAnnualManwon(context)
            val periodStart = SalaryPrefs.periodStartMillis(context)
            val periodEnd = SalaryPrefs.nextPaydayMillis(context)
            val now = System.currentTimeMillis()
            val perSec = SalaryCalculator.perSecondWon(annual, periodStart, periodEnd)
            val dday = SalaryCalculator.daysUntilNextPayday(periodEnd, now)

            val hidden = SalaryPrefs.isPrivateMode(context)
            val idx = SalaryTiers.currentIndex(earned)

            val amountText = if (hidden) "•••••• 원" else "${SalaryCalculator.formatWon(earned)}원"
            val perSecText = if (hidden) "· 초당 ••원" else "· 초당 ${SalaryCalculator.formatWon(perSec)}원"
            val ddayText = if (dday <= 0) "D-day 🎊" else "D-$dday"

            val ytd = SalaryCalculator.earnedYearToDate(annual, now)
            val ytdPercent = SalaryCalculator.yearProgressPercent(annual, now)
            val ytdManwon = (ytd / 10_000).toLong()
            val labelText = if (hidden) "이번 급여 주기" else
                "올해 ${SalaryCalculator.formatWon(ytdManwon)}만원 · ${"%.0f".format(ytdPercent)}%"

            val (tierLine, nextLine) = if (hidden) {
                "🔒 탭해서 확인" to ""
            } else if (idx == -1) {
                val first = SalaryTiers.items.first()
                val remain = (first.price - earned).coerceAtLeast(0)
                "🔒 첫 해금까지 조금만" to "다음까지 ${SalaryCalculator.formatWon(remain)}원"
            } else {
                val cur = SalaryTiers.items[idx]
                val next = SalaryTiers.items.getOrNull(idx + 1)
                val line1 = "${cur.emoji} ${cur.name} 해금!"
                val line2 = if (next != null) {
                    val remain = (next.price - earned).coerceAtLeast(0)
                    "❔ 다음까지 ${SalaryCalculator.formatWon(remain)}원"
                } else {
                    "🏁 모든 단계 해금 완료"
                }
                line1 to line2
            }

            val openAppIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val toggleIntent = PendingIntent.getService(
                context, 3,
                Intent(context, SalaryForegroundService::class.java)
                    .setAction(SalaryForegroundService.ACTION_TOGGLE_PRIVACY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_salary)
                views.setTextViewText(R.id.widget_label, labelText)
                views.setTextViewText(R.id.widget_amount, amountText)
                views.setTextViewText(R.id.widget_persec, perSecText)
                views.setTextViewText(R.id.widget_dday, ddayText)
                views.setTextViewText(R.id.widget_tier, tierLine)
                views.setTextViewText(R.id.widget_next, nextLine)
                views.setOnClickPendingIntent(R.id.widget_root, toggleIntent)
                views.setOnClickPendingIntent(R.id.widget_amount, openAppIntent)
                mgr.updateAppWidget(id, views)
            }
        }
    }
}

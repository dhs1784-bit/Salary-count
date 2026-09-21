package com.example.severancecounter

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SeveranceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context, appWidgetManager, appWidgetIds, currentEarned(context))
    }

    companion object {
        private fun currentEarned(context: Context): Long {
            val annual = SeverancePrefs.getAnnualManwon(context)
            val hireDate = SeverancePrefs.getHireDateMillis(context)
            return SeveranceCalculator.earnedSeverance(annual, hireDate, System.currentTimeMillis())
        }

        fun updateAll(
            context: Context,
            mgr: AppWidgetManager,
            ids: IntArray,
            earned: Long
        ) {
            val annual = SeverancePrefs.getAnnualManwon(context)
            val hireDate = SeverancePrefs.getHireDateMillis(context)
            val now = System.currentTimeMillis()
            val perSec = SeveranceCalculator.perSecondWon(annual)
            val tenureDays = SeveranceCalculator.tenureDays(hireDate, now)
            val years = SeveranceCalculator.tenureYears(hireDate, now)
            val dday = SeveranceCalculator.daysUntilNextAnniversary(hireDate, now)

            val hidden = SeverancePrefs.isPrivateMode(context)
            val idx = SeveranceTiers.currentIndex(earned)

            val amountText = if (hidden) "•••••• 원" else "${SeveranceCalculator.formatWon(earned)}원"
            val perSecText = if (hidden) "· 초당 ••원" else "· 초당 ${SeveranceCalculator.formatWon(perSec)}원"
            val ddayText = if (tenureDays < 365) "1년까지 D-${365 - tenureDays}" else "D-$dday"
            val labelText = if (tenureDays < 365) "퇴직금 발생까지" else "근속 ${years}년차"

            val (tierLine, nextLine) = if (hidden) {
                "🔒 탭해서 확인" to ""
            } else if (idx == -1) {
                val first = SeveranceTiers.items.first()
                val remain = (first.price - earned).coerceAtLeast(0)
                if (tenureDays < 365) {
                    "🔒 아직 퇴직금 발생 전" to "1년 채우면 적립 시작"
                } else {
                    "🔒 첫 해금까지 조금만" to "다음까지 ${SeveranceCalculator.formatWon(remain)}원"
                }
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

            val openAppIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val toggleIntent = PendingIntent.getService(
                context, 3,
                Intent(context, SeveranceForegroundService::class.java)
                    .setAction(SeveranceForegroundService.ACTION_TOGGLE_PRIVACY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_severance)
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

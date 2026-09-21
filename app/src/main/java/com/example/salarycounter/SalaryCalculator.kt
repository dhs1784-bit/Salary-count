package com.example.salarycounter

import kotlin.math.ceil
import java.util.Calendar

object SalaryCalculator {

    /** 급여 주기(월급날~다음 월급날) 실제 길이를 기준으로 초당 금액 계산 */
    fun perSecondWon(annualManwon: Int, periodStartMillis: Long, periodEndMillis: Long): Double {
        val monthlyWon = annualManwon.toDouble() * 10_000 / 12.0
        val periodSeconds = ((periodEndMillis - periodStartMillis) / 1000L).coerceAtLeast(1)
        return monthlyWon / periodSeconds
    }

    /** 이번 급여 주기 시작(월급날)부터 지금까지 번 돈(원, 내림) */
    fun earnedThisPeriod(
        annualManwon: Int,
        periodStartMillis: Long,
        periodEndMillis: Long,
        nowMillis: Long
    ): Long {
        val elapsedSec = ((nowMillis - periodStartMillis) / 1000L).coerceAtLeast(0)
        val perSec = perSecondWon(annualManwon, periodStartMillis, periodEndMillis)
        return (perSec * elapsedSec).toLong()
    }

    /** 다음 월급날까지 남은 일수 (D-day, 오늘이 월급날이면 0) */
    fun daysUntilNextPayday(nextPaydayMillis: Long, nowMillis: Long): Int {
        val diff = nextPaydayMillis - nowMillis
        if (diff <= 0) return 0
        return ceil(diff / (24.0 * 60 * 60 * 1000)).toInt()
    }

    /** 1월 1일 00:00부터 지금까지, 연봉을 초 단위로 균등 배분했을 때의 누적액 (매달 리셋되는 "이번 달 번 돈"과 별개) */
    fun earnedYearToDate(annualManwon: Int, nowMillis: Long): Double {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val yearStart = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.MONTH, 0); set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val yearEnd = (yearStart.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
        val secondsInYear = (yearEnd.timeInMillis - yearStart.timeInMillis) / 1000.0
        val secondsElapsed = ((nowMillis - yearStart.timeInMillis) / 1000.0).coerceIn(0.0, secondsInYear)
        val annualWon = annualManwon.toDouble() * 10_000
        return annualWon * (secondsElapsed / secondsInYear)
    }

    fun yearProgressPercent(annualManwon: Int, nowMillis: Long): Double {
        val ytd = earnedYearToDate(annualManwon, nowMillis)
        val annualWon = annualManwon.toDouble() * 10_000
        if (annualWon <= 0) return 0.0
        return (ytd / annualWon * 100.0).coerceIn(0.0, 100.0)
    }

    fun formatWon(amount: Long): String = "%,d".format(amount)

    fun formatWon(amount: Double): String = "%,d".format(amount.toLong())
}

package com.example.salarycounter

import kotlin.math.ceil

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

    fun formatWon(amount: Long): String = "%,d".format(amount)

    fun formatWon(amount: Double): String = "%,d".format(amount.toLong())
}

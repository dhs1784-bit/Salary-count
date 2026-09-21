package com.example.severancecounter

import java.util.Calendar
import kotlin.math.floor

object SeveranceCalculator {

    /**
     * 법정 퇴직금 간이 계산: 퇴직금 = 1일평균임금 x 30일 x (재직일수/365)
     * 평균임금(월급/30) x 30일 = 월급 이므로 결국 "퇴직금 ≈ 월급 x 근속연수"로 정리됨
     * (실제로는 최근 3개월 실지급액 기준이라 상여금 등에 따라 달라질 수 있는 근사치).
     * => 초당 적립액 = 월급 / 365 / 86400
     */
    fun perSecondWon(annualManwon: Int): Double {
        val monthlyWon = annualManwon.toDouble() * 10_000 / 12.0
        return monthlyWon / 365.0 / 86_400.0
    }

    /** 입사일부터 지금까지 쌓인 추정 퇴직금(원, 내림). 1년 미만이면 법적으로 발생하지 않으므로 0. */
    fun earnedSeverance(annualManwon: Int, hireDateMillis: Long, nowMillis: Long): Long =
        floor(earnedSeveranceExact(annualManwon, hireDateMillis, nowMillis)).toLong()

    /** 소수점까지 포함한 정확한 누적액 — 위젯/알림에 소수점 표시용 */
    fun earnedSeveranceExact(annualManwon: Int, hireDateMillis: Long, nowMillis: Long): Double {
        val tenureDays = tenureDays(hireDateMillis, nowMillis)
        if (tenureDays < 365) return 0.0
        val elapsedSec = ((nowMillis - hireDateMillis) / 1000.0).coerceAtLeast(0.0)
        return perSecondWon(annualManwon) * elapsedSec
    }

    fun tenureDays(hireDateMillis: Long, nowMillis: Long): Long =
        ((nowMillis - hireDateMillis) / (1000L * 60 * 60 * 24)).coerceAtLeast(0)

    /** 만 근속 연차 (0년차, 1년차, 2년차...) */
    fun tenureYears(hireDateMillis: Long, nowMillis: Long): Int {
        val hire = Calendar.getInstance().apply { timeInMillis = hireDateMillis }
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        var years = now.get(Calendar.YEAR) - hire.get(Calendar.YEAR)
        val hireMonthDay = hire.get(Calendar.DAY_OF_YEAR)
        val nowMonthDay = now.get(Calendar.DAY_OF_YEAR)
        if (nowMonthDay < hireMonthDay) years -= 1
        return years.coerceAtLeast(0)
    }

    /** 다음 근속 연차 기념일까지 남은 일수 (D-day) */
    fun daysUntilNextAnniversary(hireDateMillis: Long, nowMillis: Long): Int {
        val hire = Calendar.getInstance().apply { timeInMillis = hireDateMillis }
        val nextAnniv = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.MONTH, hire.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, hire.get(Calendar.DAY_OF_MONTH))
        }
        if (nextAnniv.timeInMillis <= nowMillis) {
            nextAnniv.add(Calendar.YEAR, 1)
        }
        val diffMs = nextAnniv.timeInMillis - nowMillis
        return (diffMs / (1000L * 60 * 60 * 24)).toInt() + 1
    }

    fun formatWon(amount: Long): String = "%,d".format(amount)
    fun formatWon(amount: Double): String = "%,d".format(amount.toLong())
    fun formatWon1(amount: Double): String = "%,.1f".format(amount)
}

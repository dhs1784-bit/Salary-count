package com.example.salarycounter

object SalaryCalculator {

    private const val SECONDS_PER_MONTH = 30L * 24 * 60 * 60

    /** 연봉(만원) → 초당 원 (소수점 포함) */
    fun perSecondWon(annualManwon: Int): Double {
        val monthlyWon = annualManwon.toDouble() * 10_000 / 12.0
        return monthlyWon / SECONDS_PER_MONTH
    }

    /** 이번 달 1일 00:00부터 지금까지 번 돈(원, 정수 내림) */
    fun earnedThisMonth(annualManwon: Int, monthStartMillis: Long, nowMillis: Long): Long {
        val elapsedSec = ((nowMillis - monthStartMillis) / 1000L).coerceAtLeast(0)
        val perSec = perSecondWon(annualManwon)
        return (perSec * elapsedSec).toLong()
    }

    fun formatWon(amount: Long): String =
        "%,d".format(amount)
}

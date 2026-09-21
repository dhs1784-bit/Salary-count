package com.example.loancounter

import kotlin.math.ceil
import kotlin.math.pow

/** 원리금균등상환(등액상환) 기준 계산. */
object LoanCalculator {

    data class PeriodInfo(
        val balanceAtStart: Double,   // 이번 상환주기 시작 시점 잔액
        val principalThisPeriod: Double, // 이번 회차에 갚는 원금분
        val interestThisPeriod: Double,  // 이번 회차 이자분
        val isPaidOff: Boolean
    )

    fun monthlyRate(rateBp: Int): Double = (rateBp / 100.0) / 100.0 / 12.0

    /** 월 상환액(원리금균등) */
    fun monthlyPayment(principalWon: Double, rateBp: Int, termMonths: Int): Double {
        val r = monthlyRate(rateBp)
        if (termMonths <= 0) return principalWon
        if (r == 0.0) return principalWon / termMonths
        val factor = (1 + r).pow(termMonths)
        return principalWon * r * factor / (factor - 1)
    }

    /** paymentsElapsed 회차만큼 상환한 뒤의 상태를 계산 */
    fun periodInfo(principalManwon: Long, rateBp: Int, termMonths: Int, paymentsElapsed: Int): PeriodInfo {
        val principalWon = principalManwon * 10_000.0
        val pmt = monthlyPayment(principalWon, rateBp, termMonths)
        val r = monthlyRate(rateBp)

        var balance = principalWon
        var count = 0
        while (count < paymentsElapsed && count < termMonths && balance > 0.01) {
            val interest = balance * r
            val principalPortion = (pmt - interest).coerceIn(0.0, balance)
            balance -= principalPortion
            count++
        }

        if (paymentsElapsed >= termMonths || balance <= 0.01) {
            return PeriodInfo(balanceAtStart = 0.0, principalThisPeriod = 0.0, interestThisPeriod = 0.0, isPaidOff = true)
        }

        val interest = balance * r
        val principalPortion = (pmt - interest).coerceIn(0.0, balance)
        return PeriodInfo(balanceAtStart = balance, principalThisPeriod = principalPortion, interestThisPeriod = interest, isPaidOff = false)
    }

    /** 지금 이 순간의 정확한 잔액(소수점 포함) — 이번 회차 원금분을 주기 경과 비율만큼 선형 보간 */
    fun remainingBalanceExact(
        principalManwon: Long, rateBp: Int, termMonths: Int,
        paymentsElapsed: Int, periodStartMillis: Long, periodEndMillis: Long, nowMillis: Long
    ): Double {
        val info = periodInfo(principalManwon, rateBp, termMonths, paymentsElapsed)
        if (info.isPaidOff) return 0.0

        val periodLenSec = ((periodEndMillis - periodStartMillis) / 1000.0).coerceAtLeast(1.0)
        val elapsedSec = ((nowMillis - periodStartMillis) / 1000.0).coerceIn(0.0, periodLenSec)
        val fraction = elapsedSec / periodLenSec
        return (info.balanceAtStart - info.principalThisPeriod * fraction).coerceAtLeast(0.0)
    }

    fun perSecondWon(principalManwon: Long, rateBp: Int, termMonths: Int, paymentsElapsed: Int, periodStartMillis: Long, periodEndMillis: Long): Double {
        val info = periodInfo(principalManwon, rateBp, termMonths, paymentsElapsed)
        if (info.isPaidOff) return 0.0
        val periodLenSec = ((periodEndMillis - periodStartMillis) / 1000.0).coerceAtLeast(1.0)
        return info.principalThisPeriod / periodLenSec
    }

    fun repaidSoFarExact(principalManwon: Long, remainingBalanceExact: Double): Double =
        (principalManwon * 10_000.0 - remainingBalanceExact).coerceAtLeast(0.0)

    fun percentRepaid(principalManwon: Long, remainingBalanceExact: Double): Double {
        val principalWon = principalManwon * 10_000.0
        if (principalWon <= 0) return 100.0
        return (((principalWon - remainingBalanceExact) / principalWon) * 100.0).coerceIn(0.0, 100.0)
    }

    fun daysUntilNextPayment(nextPaymentMillis: Long, nowMillis: Long): Int {
        val diff = nextPaymentMillis - nowMillis
        if (diff <= 0) return 0
        return ceil(diff / (1000.0 * 60 * 60 * 24)).toInt()
    }

    fun formatWon(amount: Long): String = "%,d".format(amount)
    fun formatWon(amount: Double): String = "%,d".format(amount.toLong())
    fun formatWon1(amount: Double): String = "%,.1f".format(amount)
}

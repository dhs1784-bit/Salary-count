package com.example.loancounter

import android.content.Context
import java.util.Calendar

object LoanPrefs {
    private const val PREFS_NAME = "loan_prefs"
    private const val KEY_PRINCIPAL_MANWON = "principal_manwon"
    private const val KEY_RATE_BP = "rate_bp" // 연이자율 x 100 (예: 450 = 4.50%)
    private const val KEY_TERM_MONTHS = "term_months"
    private const val KEY_LOAN_START_MILLIS = "loan_start_millis"
    private const val KEY_RUNNING = "running"
    private const val KEY_LAST_UNLOCKED = "last_unlocked_idx"
    private const val KEY_PRIVATE_MODE = "private_mode"
    private const val KEY_ONBOARDED = "onboarded"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setPrincipalManwon(context: Context, value: Long) {
        prefs(context).edit().putLong(KEY_PRINCIPAL_MANWON, value).apply()
    }

    fun getPrincipalManwon(context: Context): Long =
        prefs(context).getLong(KEY_PRINCIPAL_MANWON, 3000)

    fun setRateBp(context: Context, bp: Int) {
        prefs(context).edit().putInt(KEY_RATE_BP, bp).apply()
    }

    fun getRateBp(context: Context): Int =
        prefs(context).getInt(KEY_RATE_BP, 450)

    fun setTermMonths(context: Context, months: Int) {
        prefs(context).edit().putInt(KEY_TERM_MONTHS, months).apply()
    }

    fun getTermMonths(context: Context): Int =
        prefs(context).getInt(KEY_TERM_MONTHS, 60)

    fun setLoanStartMillis(context: Context, millis: Long) {
        prefs(context).edit().putLong(KEY_LOAN_START_MILLIS, millis).apply()
    }

    fun getLoanStartMillis(context: Context): Long {
        val saved = prefs(context).getLong(KEY_LOAN_START_MILLIS, -1)
        if (saved != -1L) return saved
        return System.currentTimeMillis()
    }

    fun setRunning(context: Context, running: Boolean) {
        prefs(context).edit().putBoolean(KEY_RUNNING, running).apply()
    }

    fun isRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RUNNING, false)

    fun setPrivateMode(context: Context, hidden: Boolean) {
        prefs(context).edit().putBoolean(KEY_PRIVATE_MODE, hidden).apply()
    }

    fun isPrivateMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PRIVATE_MODE, true)

    fun setLastUnlockedIdx(context: Context, idx: Int) {
        prefs(context).edit().putInt(KEY_LAST_UNLOCKED, idx).apply()
    }

    fun getLastUnlockedIdx(context: Context): Int =
        prefs(context).getInt(KEY_LAST_UNLOCKED, -1)

    fun setOnboarded(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun hasOnboarded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDED, false)

    private fun clampDay(cal: Calendar, day: Int): Int =
        minOf(day, cal.getActualMaximum(Calendar.DAY_OF_MONTH))

    private fun paymentDayAt(base: Calendar, day: Int): Calendar {
        val c = base.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, clampDay(c, day))
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c
    }

    /** 이번 상환 주기의 시작 시각(가장 최근 상환일 00:00). 대출 시작일의 '일(day)'을 매달 상환일로 사용. */
    fun periodStartMillis(context: Context): Long {
        val loanStart = Calendar.getInstance().apply { timeInMillis = getLoanStartMillis(context) }
        val paymentDay = loanStart.get(Calendar.DAY_OF_MONTH)
        val now = Calendar.getInstance()

        val thisMonthPayment = paymentDayAt(now, paymentDay)
        val periodStart = if (now.timeInMillis >= thisMonthPayment.timeInMillis) {
            thisMonthPayment
        } else {
            val prev = now.clone() as Calendar
            prev.add(Calendar.MONTH, -1)
            paymentDayAt(prev, paymentDay)
        }
        // 대출 시작일 이전으로는 못 내려가게
        return maxOf(periodStart.timeInMillis, startOfDay(loanStart.timeInMillis))
    }

    fun nextPaymentMillis(context: Context): Long {
        val loanStart = Calendar.getInstance().apply { timeInMillis = getLoanStartMillis(context) }
        val paymentDay = loanStart.get(Calendar.DAY_OF_MONTH)
        val start = Calendar.getInstance().apply { timeInMillis = periodStartMillis(context) }
        start.add(Calendar.MONTH, 1)
        return paymentDayAt(start, paymentDay).timeInMillis
    }

    private fun startOfDay(millis: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    /** 대출 시작일부터 지금까지 완료된 상환 회차 수 */
    fun paymentsElapsed(context: Context): Int {
        val loanStart = Calendar.getInstance().apply { timeInMillis = getLoanStartMillis(context) }
        val periodStart = Calendar.getInstance().apply { timeInMillis = periodStartMillis(context) }
        val months = (periodStart.get(Calendar.YEAR) - loanStart.get(Calendar.YEAR)) * 12 +
                (periodStart.get(Calendar.MONTH) - loanStart.get(Calendar.MONTH))
        return months.coerceAtLeast(0)
    }
}

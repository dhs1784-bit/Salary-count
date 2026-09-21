package com.example.salarycounter

import android.content.Context
import java.util.Calendar

object SalaryPrefs {
    private const val PREFS_NAME = "salary_prefs"
    private const val KEY_ANNUAL_MANWON = "annual_manwon"
    private const val KEY_PAYDAY = "payday"
    private const val KEY_PERIOD_START = "period_start_millis"
    private const val KEY_RUNNING = "running"
    private const val KEY_LAST_UNLOCKED = "last_unlocked_idx"
    private const val KEY_PRIVATE_MODE = "private_mode"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setAnnualManwon(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_ANNUAL_MANWON, value).apply()
    }

    fun getAnnualManwon(context: Context): Int =
        prefs(context).getInt(KEY_ANNUAL_MANWON, 6000)

    /** 월급날(1~31). 31 설정 시 짧은 달은 말일로 자동 보정됨. */
    fun setPayday(context: Context, day: Int) {
        val clamped = day.coerceIn(1, 31)
        prefs(context).edit().putInt(KEY_PAYDAY, clamped).apply()
    }

    fun getPayday(context: Context): Int =
        prefs(context).getInt(KEY_PAYDAY, 25)

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

    private fun clampDay(cal: Calendar, day: Int): Int =
        minOf(day, cal.getActualMaximum(Calendar.DAY_OF_MONTH))

    private fun paydayAt(base: Calendar, day: Int): Calendar {
        val c = base.clone() as Calendar
        c.set(Calendar.DAY_OF_MONTH, clampDay(c, day))
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    /** 이번 급여 주기의 시작 시각(가장 최근 월급날 00:00). 새 주기로 넘어가면 해금 인덱스도 자동 리셋. */
    fun periodStartMillis(context: Context): Long {
        val payday = getPayday(context)
        val now = Calendar.getInstance()
        val thisMonthPayday = paydayAt(now, payday)

        val periodStart = if (now.timeInMillis >= thisMonthPayday.timeInMillis) {
            thisMonthPayday
        } else {
            val prev = now.clone() as Calendar
            prev.add(Calendar.MONTH, -1)
            paydayAt(prev, payday)
        }

        val saved = prefs(context).getLong(KEY_PERIOD_START, -1)
        if (saved != periodStart.timeInMillis) {
            prefs(context).edit()
                .putLong(KEY_PERIOD_START, periodStart.timeInMillis)
                .putInt(KEY_LAST_UNLOCKED, -1)
                .apply()
        }
        return periodStart.timeInMillis
    }

    /** 다음 월급날 00:00 (밀리초) */
    fun nextPaydayMillis(context: Context): Long {
        val payday = getPayday(context)
        val start = Calendar.getInstance().apply { timeInMillis = periodStartMillis(context) }
        start.add(Calendar.MONTH, 1)
        return paydayAt(start, payday).timeInMillis
    }
}

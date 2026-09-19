package com.example.salarycounter

import android.content.Context
import java.util.Calendar

object SalaryPrefs {
    private const val PREFS_NAME = "salary_prefs"
    private const val KEY_ANNUAL_MANWON = "annual_manwon"
    private const val KEY_MONTH_START = "month_start_millis"
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

    /** 이번 달 1일 00:00을 기준 시각으로 반환. 달이 바뀌면 자동으로 리셋되고 해금 인덱스도 초기화. */
    fun monthStartMillis(context: Context): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val thisMonthStart = cal.timeInMillis

        val saved = prefs(context).getLong(KEY_MONTH_START, -1)
        if (saved != thisMonthStart) {
            prefs(context).edit()
                .putLong(KEY_MONTH_START, thisMonthStart)
                .putInt(KEY_LAST_UNLOCKED, -1)
                .apply()
            return thisMonthStart
        }
        return saved
    }
}

package com.example.severancecounter

import android.content.Context

object SeverancePrefs {
    private const val PREFS_NAME = "severance_prefs"
    private const val KEY_ANNUAL_MANWON = "annual_manwon"
    private const val KEY_HIRE_DATE_MILLIS = "hire_date_millis"
    private const val KEY_RUNNING = "running"
    private const val KEY_LAST_UNLOCKED = "last_unlocked_idx"
    private const val KEY_LAST_TENURE_YEAR = "last_tenure_year"
    private const val KEY_PRIVATE_MODE = "private_mode"
    private const val KEY_ONBOARDED = "onboarded"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setAnnualManwon(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_ANNUAL_MANWON, value).apply()
    }

    fun getAnnualManwon(context: Context): Int =
        prefs(context).getInt(KEY_ANNUAL_MANWON, 6000)

    /** 입사일 (밀리초, 00:00 기준). 설정 안 됐으면 오늘. */
    fun setHireDateMillis(context: Context, millis: Long) {
        prefs(context).edit().putLong(KEY_HIRE_DATE_MILLIS, millis).apply()
    }

    fun getHireDateMillis(context: Context): Long {
        val saved = prefs(context).getLong(KEY_HIRE_DATE_MILLIS, -1)
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

    // 퇴직금은 계속 쌓이기만 하고 리셋되지 않으므로, 해금 인덱스도 절대 초기화하지 않음
    fun setLastUnlockedIdx(context: Context, idx: Int) {
        prefs(context).edit().putInt(KEY_LAST_UNLOCKED, idx).apply()
    }

    fun getLastUnlockedIdx(context: Context): Int =
        prefs(context).getInt(KEY_LAST_UNLOCKED, -1)

    fun setLastTenureYear(context: Context, year: Int) {
        prefs(context).edit().putInt(KEY_LAST_TENURE_YEAR, year).apply()
    }

    fun getLastTenureYear(context: Context): Int =
        prefs(context).getInt(KEY_LAST_TENURE_YEAR, 0)

    fun setOnboarded(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun hasOnboarded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDED, false)
}

package com.peykich.sofapanel.domain

object TimeRules {
    /** minuteOfDay が [start, end) の時間帯に入っているか。日付をまたぐ帯（例 23:00〜6:00）にも対応 */
    fun inWindow(minuteOfDay: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minuteOfDay in start until end
        else -> minuteOfDay >= start || minuteOfDay < end
    }

    /** 焼き付き対策：数分ごとに時計の位置を数ピクセルずらす量（dp） */
    fun burnInShift(nowMillis: Long, periodMinutes: Int = 3): Pair<Int, Int> {
        val pattern = listOf(0 to 0, 6 to -4, -6 to 4, 4 to 6, -4 to -6, 8 to 2, -8 to -2)
        val i = ((nowMillis / (periodMinutes * 60_000L)) % pattern.size).toInt()
        return pattern[i]
    }
}

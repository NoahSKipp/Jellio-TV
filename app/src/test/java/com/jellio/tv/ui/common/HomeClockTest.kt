package com.jellio.tv.ui.common

import com.jellio.tv.data.prefs.ClockStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeClockTest {
    @Test
    fun twentyFourHourPadsHoursAndMinutes() {
        assertEquals(ClockText("09:05", null), formatClock(9, 5, ClockStyle.HOURS_24))
        assertEquals(ClockText("00:00", null), formatClock(0, 0, ClockStyle.HOURS_24))
        assertEquals(ClockText("23:59", null), formatClock(23, 59, ClockStyle.HOURS_24))
    }

    @Test
    fun twelveHourUsesNoonAndMidnightAsTwelve() {
        assertEquals(ClockText("12:00", "AM"), formatClock(0, 0, ClockStyle.HOURS_12))
        assertEquals(ClockText("9:05", "AM"), formatClock(9, 5, ClockStyle.HOURS_12))
        assertEquals(ClockText("12:30", "PM"), formatClock(12, 30, ClockStyle.HOURS_12))
        assertEquals(ClockText("11:59", "PM"), formatClock(23, 59, ClockStyle.HOURS_12))
    }
}

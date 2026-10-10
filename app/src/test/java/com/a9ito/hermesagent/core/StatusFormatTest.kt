package com.a9ito.hermesagent.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure formatter contract for the agent status strip. These run on the JVM and
 * pin the number/duration-to-string matrix the composable renders, including the
 * boundary cases (sub-minute, hour rollover, k/M thresholds, negative clamp).
 */
class StatusFormatTest {

    @Test fun elapsedTimerUnderOneHourIsMinuteSecond() {
        assertEquals("0:00", StatusFormat.elapsedTimer(0))
        assertEquals("0:05", StatusFormat.elapsedTimer(5_000))
        assertEquals("0:59", StatusFormat.elapsedTimer(59_000))
        assertEquals("1:00", StatusFormat.elapsedTimer(60_000))
        assertEquals("12:34", StatusFormat.elapsedTimer((12 * 60 + 34) * 1000L))
    }

    @Test fun elapsedTimerPastOneHourIsHourMinuteSecond() {
        assertEquals("1:00:00", StatusFormat.elapsedTimer(3_600_000))
        assertEquals("2:05:09", StatusFormat.elapsedTimer((2 * 3600 + 5 * 60 + 9) * 1000L))
    }

    @Test fun elapsedTimerClampsNegativeToZero() {
        assertEquals("0:00", StatusFormat.elapsedTimer(-5_000))
    }

    @Test fun ageShowsTwoMostSignificantUnits() {
        assertEquals("45s", StatusFormat.age(45_000))
        assertEquals("12m", StatusFormat.age(12 * 60_000L))
        assertEquals("2h 05m", StatusFormat.age((2 * 3600 + 5 * 60) * 1000L))
        assertEquals("3d 4h", StatusFormat.age((3 * 86_400 + 4 * 3600) * 1000L))
    }

    @Test fun ageClampsNegativeToZeroSeconds() {
        assertEquals("0s", StatusFormat.age(-1))
    }

    @Test fun tokensRawUnderThousand() {
        assertEquals("0", StatusFormat.tokens(0))
        assertEquals("999", StatusFormat.tokens(999))
    }

    @Test fun tokensThousandsAndMillionsTrimTrailingZero() {
        assertEquals("1k", StatusFormat.tokens(1_000))
        assertEquals("12.3k", StatusFormat.tokens(12_345))
        assertEquals("5k", StatusFormat.tokens(5_000))
        assertEquals("1M", StatusFormat.tokens(1_000_000))
        assertEquals("2.5M", StatusFormat.tokens(2_500_000))
    }

    @Test fun tokensClampsNegativeToZero() {
        assertEquals("0", StatusFormat.tokens(-100))
    }
}

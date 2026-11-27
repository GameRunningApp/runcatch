package com.cookandroid.runcatch.format;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RunFormatTest {

    @Test
    public void pace_roundsSecondsWithoutShowingSixty() {
        // 359.6초 → 6'00" (예전 결과 화면은 5:60으로 표시했음)
        assertEquals("6'00\"/km", RunFormat.pace(359.6));
        assertEquals("5'30\"/km", RunFormat.pace(330));
    }

    @Test
    public void pace_zeroIsEmpty() {
        assertEquals(RunFormat.EMPTY_PACE, RunFormat.pace(0));
    }

    @Test
    public void clock_formatsHoursMinutesSeconds() {
        assertEquals("01:02:03", RunFormat.clock(3723));
        assertEquals("00 : 31 : 20", RunFormat.clock(1880, " : "));
    }
}

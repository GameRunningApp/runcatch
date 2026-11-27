package com.cookandroid.runcatch.core.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;

public class RunStreakTest {

    private static final TimeZone SEOUL = TimeZone.getTimeZone("Asia/Seoul");
    private static final long HOUR = 60L * 60 * 1000;
    private static final long DAY = 24 * HOUR;
    // 2026-11-04 10:00 KST
    private static final long NOW = 1793754000000L;

    @Test
    public void noRuns_isZero() {
        assertEquals(0, RunStreak.countDays(Collections.emptyList(), NOW, SEOUL));
    }

    @Test
    public void ranToday_countsFromToday() {
        assertEquals(3, RunStreak.countDays(
                Arrays.asList(NOW, NOW - DAY, NOW - 2 * DAY), NOW, SEOUL));
    }

    @Test
    public void notYetToday_countsFromYesterday() {
        // 오늘 아직 안 뛰었어도 어제까지의 연속 기록은 유지
        assertEquals(2, RunStreak.countDays(
                Arrays.asList(NOW - DAY, NOW - 2 * DAY), NOW, SEOUL));
    }

    @Test
    public void gapBreaksStreak() {
        assertEquals(1, RunStreak.countDays(
                Arrays.asList(NOW, NOW - 2 * DAY, NOW - 3 * DAY), NOW, SEOUL));
    }

    @Test
    public void severalRunsSameDay_countAsOneDay() {
        assertEquals(2, RunStreak.countDays(
                Arrays.asList(NOW, NOW - HOUR, NOW - 2 * HOUR, NOW - DAY), NOW, SEOUL));
    }

    @Test
    public void splitsDaysAtKoreanMidnight() {
        long todayHalfPastMidnight = NOW - 9 * HOUR - 30 * 60 * 1000;   // 11/04 00:30 KST
        long yesterdayLateNight = todayHalfPastMidnight - HOUR;          // 11/03 23:30 KST
        assertEquals(2, RunStreak.countDays(
                Arrays.asList(todayHalfPastMidnight, yesterdayLateNight), NOW, SEOUL));
    }

    @Test
    public void weekStart_isMondayMidnight() {
        // NOW는 수요일 10시 → 월요일(11-02) 0시
        assertEquals(NOW - 2 * DAY - 10 * HOUR, RunStreak.weekStartMillis(NOW, SEOUL));
        // 월요일 0시는 그 주의 시작 그대로
        long monday = NOW - 2 * DAY - 10 * HOUR;
        assertEquals(monday, RunStreak.weekStartMillis(monday, SEOUL));
    }
}

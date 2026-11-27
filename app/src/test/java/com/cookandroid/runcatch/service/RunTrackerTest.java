package com.cookandroid.runcatch.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** RunTracker의 좌표 거르기 · 페이스 규칙 (안드로이드 클래스를 쓰지 않는 static 함수만) */
public class RunTrackerTest {

    private static final long SEC = 1_000_000_000L;
    private static final long NOW = 1000 * SEC;

    private static boolean usable(long timestamp, long lastFix, long resumedAt,
                                  boolean hasAccuracy, float accuracy) {
        return RunTracker.isUsableFix(timestamp, NOW, lastFix, resumedAt, hasAccuracy, accuracy);
    }

    @Test
    public void usableFix_normal() {
        assertTrue(usable(NOW - SEC, 0, 0, true, 10));
    }

    @Test
    public void usableFix_rejectsDuplicateAndBeforeResume() {
        assertFalse(usable(NOW - SEC, NOW - SEC, 0, true, 10));  // 같은 시각 좌표 다시 옴
        assertFalse(usable(NOW - 5 * SEC, 0, NOW - SEC, true, 10)); // 재개 전(일시정지 중) 좌표
    }

    @Test
    public void usableFix_rejectsInaccurateAndTooLate() {
        assertFalse(usable(NOW - SEC, 0, 0, false, 10));
        assertTrue(usable(NOW - SEC, 0, 0, true, RunTracker.MAX_ACCURACY_M));
        assertFalse(usable(NOW - SEC, 0, 0, true, RunTracker.MAX_ACCURACY_M + 1));
        assertTrue(usable(NOW - 120 * SEC, 0, 0, true, 10));   // 2분까지는 사용
        assertFalse(usable(NOW - 121 * SEC, 0, 0, true, 10));
    }

    @Test
    public void plausibleStep_rejectsJumpAndZeroTime() {
        assertTrue(RunTracker.isPlausibleStep(24, 2));    // 초속 12m
        assertFalse(RunTracker.isPlausibleStep(25, 2));
        assertFalse(RunTracker.isPlausibleStep(5, 0));
    }

    @Test
    public void pace_isSecondsPerKm() {
        assertEquals(300, RunTracker.paceSecondsPerKm(2000, 600), 1e-9);  // 2km 10분 → 5'00"
        assertEquals(0, RunTracker.paceSecondsPerKm(0, 600), 1e-9);
    }
}

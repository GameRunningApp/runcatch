package com.cookandroid.runcatch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;

public class ExpRulesTest {

    private static final TimeZone SEOUL = TimeZone.getTimeZone("Asia/Seoul");
    private static final long DAY = 24L * 60 * 60 * 1000;
    // 2026-11-04 10:00 KST
    private static final long TODAY = 1793754000000L;

    @Test
    public void distanceExp_per100m() {
        assertEquals(0, ExpRules.distanceExp(0f));
        assertEquals(0, ExpRules.distanceExp(99f));
        assertEquals(10, ExpRules.distanceExp(1000f));
        assertEquals(12, ExpRules.distanceExp(1250f));
    }

    @Test
    public void streakExp_cappedAt30() {
        assertEquals(5, ExpRules.streakExp(1));
        assertEquals(25, ExpRules.streakExp(5));
        assertEquals(30, ExpRules.streakExp(6));
        assertEquals(30, ExpRules.streakExp(20));
    }

    @Test
    public void levelTable() {
        assertEquals(50, ExpRules.expToNextLevel(1));
        assertEquals(70, ExpRules.expToNextLevel(2));
        assertEquals(90, ExpRules.expToNextLevel(3));

        assertEquals(1, ExpRules.levelOf(0));
        assertEquals(1, ExpRules.levelOf(49));
        assertEquals(2, ExpRules.levelOf(50));
        assertEquals(2, ExpRules.levelOf(119));
        assertEquals(3, ExpRules.levelOf(120));   // 50 + 70
        assertEquals(15, ExpRules.expInLevel(65));
    }

    @Test
    public void beginnerLevelsUpAfterTwo1kmRuns() {
        // 기획안 예시: 1km씩 두 번 뛰면 첫 레벨업 (연속 1일·2일 보너스 포함)
        int first = ExpRules.RUN_COMPLETE_EXP + ExpRules.distanceExp(1000f) + ExpRules.streakExp(1);
        int second = ExpRules.RUN_COMPLETE_EXP + ExpRules.distanceExp(1000f) + ExpRules.streakExp(2);
        assertEquals(1, ExpRules.levelOf(first));
        assertEquals(2, ExpRules.levelOf(first + second));
    }

    @Test
    public void streakDays_countsBackFromToday() {
        assertEquals(1, ExpRules.streakDays(Collections.emptyList(), TODAY, SEOUL));
        assertEquals(1, ExpRules.streakDays(Arrays.asList(TODAY, TODAY - 2 * DAY), TODAY, SEOUL));
        assertEquals(3, ExpRules.streakDays(
                Arrays.asList(TODAY, TODAY - DAY, TODAY - 2 * DAY, TODAY - 4 * DAY), TODAY, SEOUL));
        // 같은 날 여러 번 뛰어도 1일
        assertEquals(2, ExpRules.streakDays(
                Arrays.asList(TODAY, TODAY - 3600_000L, TODAY - DAY), TODAY, SEOUL));
    }
}

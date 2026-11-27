package com.cookandroid.runcatch.core.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExpRulesTest {

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
}

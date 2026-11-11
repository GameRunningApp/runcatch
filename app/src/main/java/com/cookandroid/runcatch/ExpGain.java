package com.cookandroid.runcatch;

import android.content.Intent;

/**
 * 러닝 1회로 얻은 경험치와, 그 경험치를 받은 대표 캐릭터의 변화.
 * RunningActivity에서 계산하고 Intent에 담아 (캐릭터 획득 화면을 거쳐) ResultActivity로 넘긴다.
 */
public class ExpGain {

    private static final String EXTRA_RUN = "expRun";
    private static final String EXTRA_DISTANCE = "expDistance";
    private static final String EXTRA_GOAL = "expGoal";
    private static final String EXTRA_STREAK_DAYS = "expStreakDays";
    private static final String EXTRA_STREAK = "expStreak";
    private static final String EXTRA_CHARACTER_ID = "expCharacterId";
    private static final String EXTRA_BEFORE = "expBefore";
    private static final String EXTRA_AFTER = "expAfter";

    public final int runExp;        // 러닝 완료
    public final int distanceExp;   // 100m당 1
    public final int goalExp;       // 개인 목표 달성
    public final int streakDays;    // 연속 러닝 일수 (오늘 포함)
    public final int streakExp;     // 연속 일수 × 5 (최대 30)

    /** 경험치를 받은 대표 캐릭터 id. 대표 캐릭터가 없으면 -1 */
    public final int characterId;
    /** 대표 캐릭터의 누적 경험치 (이번 러닝 전 / 후) */
    public final int expBefore;
    public final int expAfter;

    private ExpGain(int runExp, int distanceExp, int goalExp, int streakDays, int streakExp,
                    int characterId, int expBefore, int expAfter) {
        this.runExp = runExp;
        this.distanceExp = distanceExp;
        this.goalExp = goalExp;
        this.streakDays = streakDays;
        this.streakExp = streakExp;
        this.characterId = characterId;
        this.expBefore = expBefore;
        this.expAfter = expAfter;
    }

    /** 이번 러닝 경험치 계산 (아직 캐릭터에 적용 전) */
    public static ExpGain calculate(float distanceMeters, int streakDays, boolean goalAchieved) {
        return new ExpGain(
                ExpRules.RUN_COMPLETE_EXP,
                ExpRules.distanceExp(distanceMeters),
                goalAchieved ? ExpRules.GOAL_EXP : 0,
                streakDays,
                ExpRules.streakExp(streakDays),
                -1, 0, 0);
    }

    /** 대표 캐릭터에 적용한 결과를 붙인 새 객체 */
    public ExpGain appliedTo(int characterId, int expBefore, int expAfter) {
        return new ExpGain(runExp, distanceExp, goalExp, streakDays, streakExp,
                characterId, expBefore, expAfter);
    }

    public int total() {
        return runExp + distanceExp + goalExp + streakExp;
    }

    public boolean hasCharacter() {
        return characterId != -1;
    }

    public int levelBefore() {
        return ExpRules.levelOf(expBefore);
    }

    public int levelAfter() {
        return ExpRules.levelOf(expAfter);
    }

    public boolean leveledUp() {
        return hasCharacter() && levelAfter() > levelBefore();
    }

    // ===== Intent 주고받기 =====

    public void putInto(Intent intent) {
        intent.putExtra(EXTRA_RUN, runExp);
        intent.putExtra(EXTRA_DISTANCE, distanceExp);
        intent.putExtra(EXTRA_GOAL, goalExp);
        intent.putExtra(EXTRA_STREAK_DAYS, streakDays);
        intent.putExtra(EXTRA_STREAK, streakExp);
        intent.putExtra(EXTRA_CHARACTER_ID, characterId);
        intent.putExtra(EXTRA_BEFORE, expBefore);
        intent.putExtra(EXTRA_AFTER, expAfter);
    }

    /** Intent에 경험치 정보가 없으면 null */
    public static ExpGain from(Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_RUN)) return null;
        return new ExpGain(
                intent.getIntExtra(EXTRA_RUN, 0),
                intent.getIntExtra(EXTRA_DISTANCE, 0),
                intent.getIntExtra(EXTRA_GOAL, 0),
                intent.getIntExtra(EXTRA_STREAK_DAYS, 0),
                intent.getIntExtra(EXTRA_STREAK, 0),
                intent.getIntExtra(EXTRA_CHARACTER_ID, -1),
                intent.getIntExtra(EXTRA_BEFORE, 0),
                intent.getIntExtra(EXTRA_AFTER, 0));
    }

    /** 캐릭터 획득 화면처럼 중간에 거쳐 가는 화면에서 그대로 다음 화면으로 넘길 때 */
    public static void copy(Intent from, Intent to) {
        ExpGain gain = from(from);
        if (gain != null) gain.putInto(to);
    }
}

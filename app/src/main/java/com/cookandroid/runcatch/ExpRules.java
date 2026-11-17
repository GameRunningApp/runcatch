package com.cookandroid.runcatch;

/**
 * 캐릭터 성장(경험치) 규칙. 기획안 '핵심 기능 ① 성장 규칙'과 같은 값.
 * 수치를 바꿀 때는 이 파일의 상수만 바꾸면 된다. (세부 수치는 테스트 후 조정)
 *
 * 러닝 1회 경험치 = 러닝 완료 20 + 100m당 1 + 개인 목표 달성 10 + 연속 러닝 일수 × 5 (최대 30)
 * 레벨업 = Lv1→2 에 50, 이후 레벨마다 필요한 경험치가 20씩 늘어남 (50, 70, 90, ...)
 *
 * 연속 러닝 일수를 세는 코드는 RunStreak.java에 있다. 여기서는 일수를 경험치로 바꾸기만 한다.
 * 안드로이드 클래스를 쓰지 않아서 단위 테스트로 바로 확인할 수 있다.
 */
public final class ExpRules {

    public static final int RUN_COMPLETE_EXP = 20;
    public static final int METERS_PER_EXP = 100;
    public static final int GOAL_EXP = 10;
    public static final int STREAK_EXP_PER_DAY = 5;
    public static final int STREAK_EXP_MAX = 30;

    public static final int FIRST_LEVEL_EXP = 50;
    public static final int LEVEL_EXP_STEP = 20;

    private ExpRules() {
    }

    /** 거리 경험치: 100m당 1 (100m 미만은 버림) */
    public static int distanceExp(float distanceMeters) {
        return distanceMeters <= 0 ? 0 : (int) (distanceMeters / METERS_PER_EXP);
    }

    /** 연속 러닝 경험치: 연속 일수 × 5, 최대 30 */
    public static int streakExp(int streakDays) {
        return Math.min(Math.max(streakDays, 0) * STREAK_EXP_PER_DAY, STREAK_EXP_MAX);
    }

    /** level → level+1 로 올라가는 데 필요한 경험치 (Lv1→2 = 50, Lv2→3 = 70, ...) */
    public static int expToNextLevel(int level) {
        return FIRST_LEVEL_EXP + (Math.max(level, 1) - 1) * LEVEL_EXP_STEP;
    }

    /** 누적 경험치로 계산한 레벨 (1부터 시작) */
    public static int levelOf(int totalExp) {
        int level = 1;
        int remain = Math.max(totalExp, 0);
        while (remain >= expToNextLevel(level)) {
            remain -= expToNextLevel(level);
            level++;
        }
        return level;
    }

    /** 현재 레벨 안에서 모은 경험치 (진행바 왼쪽 숫자) */
    public static int expInLevel(int totalExp) {
        int level = 1;
        int remain = Math.max(totalExp, 0);
        while (remain >= expToNextLevel(level)) {
            remain -= expToNextLevel(level);
            level++;
        }
        return remain;
    }
}

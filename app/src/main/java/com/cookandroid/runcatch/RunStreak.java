package com.cookandroid.runcatch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * 연속 러닝 일수 계산.
 * 홈 화면("🔥 연속 러닝 N일째")과 경험치 계산(연속 일수 × 5)이 같은 코드를 쓴다.
 *
 * 규칙
 * - 같은 날 여러 번 뛰어도 하루로 센다.
 * - 오늘 뛰었으면 오늘부터, 아직 안 뛰었으면 어제부터 하루씩 거슬러 올라가며 센다.
 *   (오늘 아직 안 뛰었다고 연속 기록이 0으로 보이지 않게 하기 위함)
 * - 러닝 기록이 없는 날이 나오면 멈춘다.
 *
 * 안드로이드 클래스를 쓰지 않아서 단위 테스트로 바로 확인할 수 있다.
 */
public final class RunStreak {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private RunStreak() {
    }

    /**
     * 연속 러닝 일수.
     *
     * @param runDates  러닝 시작 시각 목록 (epoch ms, 순서 상관없음)
     * @param nowMillis 기준 시각 (보통 System.currentTimeMillis(), 러닝 종료 직후면 그 러닝의 시작 시각)
     * @param zone      날짜를 나누는 시간대 (보통 TimeZone.getDefault())
     */
    public static int countDays(List<Long> runDates, long nowMillis, TimeZone zone) {
        Set<Long> runDays = new HashSet<>();
        if (runDates != null) {
            for (Long date : runDates) {
                if (date != null) runDays.add(dayNumber(date, zone));
            }
        }

        long day = dayNumber(nowMillis, zone);
        if (!runDays.contains(day)) {
            day--;   // 오늘 아직 안 뛰었으면 어제부터
        }

        int streak = 0;
        while (runDays.contains(day - streak)) {
            streak++;
        }
        return streak;
    }

    /** 1970-01-01 기준 몇 번째 날인지 (해당 시간대 기준) */
    static long dayNumber(long millis, TimeZone zone) {
        return Math.floorDiv(millis + zone.getOffset(millis), DAY_MS);
    }
}

package com.cookandroid.runcatch.core.domain;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * 연속 러닝 일수와 주 시작 계산.
 * 홈 화면("🔥 연속 러닝 N일째")과 경험치 계산(연속 일수 × 5)이 같은 코드를 쓴다.
 *
 *  MainActivity · RecordActivity에 따로 있던 주 시작 계산을 weekStartMillis()로 모았음
 */
public final class RunStreak {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private RunStreak() {
    }

    /**
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
    public static long weekStartMillis(long nowMillis, TimeZone zone) {
        Calendar cal = Calendar.getInstance(zone);
        cal.setTimeInMillis(nowMillis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        // Calendar.DAY_OF_WEEK는 일요일 1 ~ 토요일 7 → 월요일 0 ~ 일요일 6
        cal.add(Calendar.DAY_OF_MONTH, -((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        return cal.getTimeInMillis();
    }
    static long dayNumber(long millis, TimeZone zone) {
        return Math.floorDiv(millis + zone.getOffset(millis), DAY_MS);
    }
}

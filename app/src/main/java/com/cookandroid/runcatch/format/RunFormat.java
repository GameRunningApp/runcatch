package com.cookandroid.runcatch.format;

import java.util.Locale;

/**
 * 시간과 페이스를 화면에 표시
 *
 * RunningService.formatTime, CommunityDummyData.formatPace/formatDuration,
 *   CommunityActivity.formatPace, 각 화면의 페이스 계산을 하나로 모았음
 */
public final class RunFormat {

    public static final String EMPTY_PACE = "--'--\"/km";

    private RunFormat() {
    }

    // 초/km → 6'01"/km. 0 이하면 EMPTY_PACE
    public static String pace(double secondsPerKm) {
        long total = Math.round(secondsPerKm);
        if (total <= 0) return EMPTY_PACE;
        return String.format(Locale.US, "%d'%02d\"/km", total / 60, total % 60);
    }

    // 초 → 00:31:20 (separator가 " : "면 00 : 31 : 20)
    public static String clock(long seconds, String separator) {
        return String.format(Locale.US, "%02d%s%02d%s%02d",
                seconds / 3600, separator, seconds / 60 % 60, separator, seconds % 60);
    }

    public static String clock(long seconds) {
        return clock(seconds, ":");
    }
}

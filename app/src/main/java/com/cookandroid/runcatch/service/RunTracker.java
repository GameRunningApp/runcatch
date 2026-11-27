package com.cookandroid.runcatch.service;

import com.cookandroid.runcatch.core.domain.RunRecord;
import com.cookandroid.runcatch.environment.EnvironmentFeature;
import com.cookandroid.runcatch.environment.EnvironmentMatcher;
import com.cookandroid.runcatch.environment.EnvironmentTally;

import android.location.Location;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 러닝 1회의 측정 상태: 좌표 거르기, 거리 · 최고 속도 누적, 시간(일시정지 포함), 지도 경로 구간, 환경 집계.
 * 러닝을 시작할 때마다 새로 만든다 → 이전 러닝 값이 다음 러닝에 남지 않는다 (필드를 하나씩 0으로 되돌리지 않음).
 *
 * RunningService의 위치 콜백, 시간 계산, 경로 저장, 기록 계산을 옮겨 왔음
 */
final class RunTracker {

    // ===== 좌표 거르기 기준 =====
    /** 정확도가 이보다 나쁜 좌표는 버림 (m) */
    static final float MAX_ACCURACY_M = 100;
    /** 백그라운드에서 늦게 전달된 좌표도 이 시간까지는 사용 (2분) */
    static final long MAX_DELAY_NANOS = 120_000_000_000L;
    /** 이전 좌표와의 속도가 이보다 빠르면 튄 좌표로 보고 버림 (초속 12m = 시속 43km) */
    static final float MAX_SPEED_MPS = 12;
    /** 좌표 간격이 이 이상이면 지도 선만 새 구간으로 나눔. 거리는 계속 누적 (초) */
    static final double NEW_SEGMENT_GAP_SEC = 30;

    // ===== 측정 중 페이스 표시 =====
    /** 이 거리 미만이면 페이스를 표시하지 않음 (m) */
    static final float MIN_PACE_DISTANCE_M = 10;
    /** 숫자가 너무 자주 바뀌지 않게 이 단위로 반올림 (초) */
    static final int LIVE_PACE_STEP_SEC = 5;

    private final List<EnvironmentFeature> environmentFeatures;
    private final EnvironmentTally environmentTally = new EnvironmentTally();
    /** 지도 경로. 일시정지 · 긴 끊김마다 새 구간 */
    private final List<List<Location>> segments = new ArrayList<>();
    /** DB 기록용 러닝 시작 시각 (epoch ms) */
    private final long runStartTime;

    private boolean paused;
    /** 일시정지 전까지 쌓인 시간 (ms) */
    private long accumulated;
    /** 마지막 시작 · 재개 시각 (elapsedRealtime ms) */
    private long startedAt;
    /** 이 시각 이전 좌표는 버림 (일시정지 동안의 좌표) */
    private long resumedAtNanos;
    private long lastFixNanos;
    private Location previous;
    private float meters;
    /** km/h */
    private float maxSpeed;
    /** 경로가 바뀔 때마다 증가. 화면이 다시 그릴지 판단할 때 씀 */
    private int routeVersion = 1;

    RunTracker(List<EnvironmentFeature> environmentFeatures) {
        this.environmentFeatures = environmentFeatures;
        runStartTime = System.currentTimeMillis();
        startedAt = SystemClock.elapsedRealtime();
        resumedAtNanos = SystemClock.elapsedRealtimeNanos();
        segments.add(new ArrayList<>());
    }

    // ===== 좌표 =====

    /** 좌표 1개 반영. 거른 좌표면 false */
    boolean add(Location location) {
        if (paused) return false;
        long timestamp = location.getElapsedRealtimeNanos();
        if (!isUsableFix(timestamp, SystemClock.elapsedRealtimeNanos(), lastFixNanos,
                resumedAtNanos, location.hasAccuracy(), location.getAccuracy())) {
            return false;
        }

        if (previous != null) {
            float step = previous.distanceTo(location);
            double seconds = (timestamp - previous.getElapsedRealtimeNanos()) / 1e9;
            if (!isPlausibleStep(step, seconds)) return false;
            if (seconds >= NEW_SEGMENT_GAP_SEC) segments.add(new ArrayList<>());
            meters += step;
        }
        if (location.hasSpeed()) {
            maxSpeed = Math.max(maxSpeed, location.getSpeed() * 3.6f);
        }

        environmentTally.add(EnvironmentMatcher.findMatchedCategories(
                location.getLatitude(), location.getLongitude(), environmentFeatures));
        Log.d("ENV_COUNT", environmentTally.summary());

        previous = new Location(location);
        lastFixNanos = timestamp;
        segments.get(segments.size() - 1).add(new Location(location));
        routeVersion++;
        return true;
    }

    /** 중복 · 재개 전 · 부정확 · 너무 늦은 좌표가 아니면 true (안드로이드 클래스 없이 테스트 가능) */
    static boolean isUsableFix(long timestampNanos, long nowNanos, long lastFixNanos,
                               long resumedAtNanos, boolean hasAccuracy, float accuracyMeters) {
        return timestampNanos > lastFixNanos
                && timestampNanos >= resumedAtNanos
                && hasAccuracy
                && accuracyMeters <= MAX_ACCURACY_M
                && nowNanos - timestampNanos <= MAX_DELAY_NANOS;
    }

    /** 이전 좌표에서 이 거리(m)를 이 시간(초)에 움직인 게 사람 속도면 true */
    static boolean isPlausibleStep(float stepMeters, double seconds) {
        return seconds > 0 && stepMeters / seconds <= MAX_SPEED_MPS;
    }

    // ===== 시간 =====

    void pause() {
        if (paused) return;
        accumulated = elapsedMillis();
        paused = true;
        previous = null;
    }

    void resume() {
        if (!paused) return;
        previous = null;
        // 일시정지 동안 이동한 위치를 이전 경로와 연결하지 않음
        segments.add(new ArrayList<>());
        routeVersion++;
        startedAt = SystemClock.elapsedRealtime();
        resumedAtNanos = SystemClock.elapsedRealtimeNanos();
        paused = false;
    }

    /** 종료. 이후 시간은 멈춘 값 그대로 */
    void finish() {
        accumulated = elapsedMillis();
        paused = true;
    }

    boolean isPaused() {
        return paused;
    }

    long elapsedMillis() {
        return accumulated + (paused ? 0 : SystemClock.elapsedRealtime() - startedAt);
    }

    // ===== 기록 값 =====

    float meters() {
        return meters;
    }

    /** 평균 페이스 (초/km). 거리가 0이면 0. 측정 중 표시와 DB 기록이 같은 식을 쓴다 */
    static double paceSecondsPerKm(double meters, double seconds) {
        return meters > 0 ? seconds / (meters / 1000.0) : 0;
    }

    /** 측정 중 표시용 페이스 (초/km). 10m 미만이면 0, 5초 단위 반올림 */
    long livePaceSeconds() {
        if (meters < MIN_PACE_DISTANCE_M) return 0;
        double pace = paceSecondsPerKm(meters, elapsedMillis() / 1000.0);
        return Math.round(pace / LIVE_PACE_STEP_SEC) * LIVE_PACE_STEP_SEC;
    }

    String finalEnvironment() {
        return environmentTally.finalEnvironment();
    }

    /** running_record에 저장할 값 (거리 m, 시간 초, 페이스 초/km, 속도 km/h) */
    RunRecord createRunRecord(String userId) {
        float durationSec = elapsedMillis() / 1000f;
        float averageSpeedKmh = durationSec > 0 ? (meters / 1000f) / (durationSec / 3600f) : 0;
        return new RunRecord(0, userId, runStartTime, meters, durationSec,
                (float) paceSecondsPerKm(meters, durationSec), averageSpeedKmh, maxSpeed);
    }

    // ===== 지도 경로 =====

    int routeVersion() {
        return routeVersion;
    }

    /** 전체 경로 복사본 */
    List<List<Location>> route() {
        return routeFrom(new int[0]);
    }

    /** 화면이 이미 그린 좌표(구간별 개수) 뒤에 추가된 점만 복사해서 돌려줌 */
    List<List<Location>> routeFrom(int[] pointCounts) {
        List<List<Location>> added = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            List<Location> segment = segments.get(i);
            int from = i < pointCounts.length ? Math.min(pointCounts[i], segment.size()) : 0;
            List<Location> points = new ArrayList<>();
            for (int j = from; j < segment.size(); j++) points.add(new Location(segment.get(j)));
            added.add(points);
        }
        return added;
    }
}

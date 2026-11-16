package com.cookandroid.runcatch;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.*;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.*;

import com.cookandroid.runcatch.backend.RunRecord;

import java.util.*;

public class RunningService extends Service {
    public static final String START = "com.cookandroid.runcatch.START";
    public static final String PAUSE = "com.cookandroid.runcatch.PAUSE";
    public static final String RESUME = "com.cookandroid.runcatch.RESUME";

    public static final String CHANNEL = "running_tracking";
    public static final int NOTIFICATION = 1001;

    private final LocalBinder binder = new LocalBinder();
    private FusedLocationProviderClient client;

    private boolean active;
    private boolean paused;

    private long accumulated;
    private long startedAt;
    private long lastFixNanos;
    private long resumedAtNanos;

    private float meters;

    // 러닝 시작 시각과 최고 속도는 서비스가 관리합니다.
    private long runStartTime;
    private float maxSpeed;

    // [추가] 러닝 중 좌표별 환경을 서비스에서 집계합니다.
    private ArrayList<EnvironmentFeature> environmentFeatures;
    private int parkCount;
    private int forestCount;
    private int riverCount;
    private int lakeCount;

    private Location previous;
    private String error;
    private int routeVersion;

    private final List<List<Location>> segments = new ArrayList<>();

    public class LocalBinder extends Binder {
        public RunningService getService() {
            return RunningService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        client = LocationServices.getFusedLocationProviderClient(this);

        // [추가] 기존 사전 저장 환경 데이터를 서비스에서 한 번 불러옵니다.
        environmentFeatures = EnvironmentDataLoader.load(this);
        if (environmentFeatures == null) environmentFeatures = new ArrayList<>();


        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL,
                    "러닝 기록",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription(
                    "러닝 상태 확인 및 일시정지 / 다시 시작"
            );

            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }
    }

    private final LocationCallback callback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            if (!active || paused) return;

            for (Location location : result.getLocations()) {
                long timestamp = location.getElapsedRealtimeNanos();

                // 중복 좌표, 재개 전 좌표, 지나치게 부정확한 위치를 제외합니다.
                if (timestamp <= lastFixNanos
                        || timestamp < resumedAtNanos
                        || !location.hasAccuracy()
                        || location.getAccuracy() > 100) {
                    continue;
                }

                // 백그라운드에서 늦게 전달된 위치도 최대 2분까지 사용합니다.
                if (SystemClock.elapsedRealtimeNanos() - timestamp
                        > 120_000_000_000L) {
                    continue;
                }

                if (previous != null) {
                    float step = previous.distanceTo(location);

                    double seconds =
                            (timestamp - previous.getElapsedRealtimeNanos())
                                    / 1e9;

                    // [수정] 늦게 온 좌표도 속도가 비정상적이지 않으면 거리 계산에 포함합니다.
                    if (seconds <= 0 || step / seconds > 12) {
                        continue;
                    }

                    // 업데이트 간격이 길면 지도 선만 새 구간으로 나누고 거리는 계속 누적합니다.
                    if (seconds >= 30) {
                        segments.add(new ArrayList<>());
                    }
                    meters += step;
                }
                // 최고 속도는 km/h로 저장합니다.
                if (location.hasSpeed()) {
                    maxSpeed = Math.max(
                            maxSpeed,
                            location.getSpeed() * 3.6f
                    );
                }

                // [추가] 유효한 러닝 좌표를 환경 집계에 사용합니다.
                countEnvironmentAtLocation(location.getLatitude(), location.getLongitude());

                error = null;
                previous = new Location(location);
                lastFixNanos = timestamp;

                segments.get(segments.size() - 1)
                        .add(new Location(location));

                routeVersion++;
            }

            updateNotification();
        }

        @Override
        public void onLocationAvailability(
                LocationAvailability availability
        ) {
            if (!active) return;

            error = availability.isLocationAvailable()
                    ? null
                    : "GPS 신호를 기다리는 중";

            updateNotification();
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        if (START.equals(action)) {
            // 화면으로 다시 돌아와도 이미 진행 중이면 초기화하지 않음.
            if (!active) {
                begin();
            }
        } else if (PAUSE.equals(action)) {
            pauseRun();
        } else if (RESUME.equals(action)) {
            resumeRun();
        }

        if (!active) {
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private boolean hasLocation() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void begin() {
        if (!hasLocation()) {
            error = "위치 권한이 필요합니다";
            stopSelf();
            return;
        }

        meters = 0;
        // [추가] 새 러닝을 시작할 때 이전 환경 집계값을 초기화합니다.
        parkCount = 0;
        forestCount = 0;
        riverCount = 0;
        lakeCount = 0;
        accumulated = 0;
        lastFixNanos = 0;
        previous = null;

        segments.clear();
        segments.add(new ArrayList<>());
        routeVersion++;

        active = true;
        paused = false;
        error = null;


        startedAt = SystemClock.elapsedRealtime();


        resumedAtNanos = SystemClock.elapsedRealtimeNanos();

        // DB 기록에 사용할 러닝 시작 시각과 최고 속도를 초기화합니다.
        runStartTime = System.currentTimeMillis();
        maxSpeed = 0;

        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                        NOTIFICATION,
                        notification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                );
            } else {
                startForeground(
                        NOTIFICATION,
                        notification()
                );
            }

            LocationRequest request = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    2000
            )
                    .setMinUpdateIntervalMillis(1000)
                    .setMaxUpdateDelayMillis(0)
                    .build();

            client.requestLocationUpdates(
                    request,
                    callback,
                    Looper.getMainLooper()
            ).addOnFailureListener(failure -> {
                if (active) {
                    error = "위치 수신을 시작하지 못했습니다";
                    finishRun();
                }
            });
        } catch (RuntimeException failure) {
            error =
                    "러닝 서비스를 시작하지 못했습니다. "
                            + "위치 권한과 위치 설정을 확인해 주세요";
            finishRun();
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPaused() {
        return paused;
    }

    public String getError() {
        return error;
    }

    // 서비스가 기록한 누적 거리(m)를 반환합니다.
    public float getMeters() {
        return meters;
    }


    // [추가] Activity 표시와 결과 화면 전달에 사용할 거리(km)를 반환합니다.
    public float getKilometers() {
        return meters / 1000f;
    }

    public long getRunStartTime() {
        return runStartTime;
    }

    // 페이스 계산은 서비스가 담당하고 Activity에는 표시 문자열만 전달합니다.
    // [추가] GPS 좌표가 속한 환경 종류별 카운트를 서비스에서 누적합니다.
    private void countEnvironmentAtLocation(double latitude, double longitude) {
        Set<String> matchedCategories = EnvironmentMatcher.findMatchedCategories(
                latitude,
                longitude,
                environmentFeatures
        );
        if (matchedCategories.contains("PARK")) parkCount++;
        if (matchedCategories.contains("FOREST")) forestCount++;
        if (matchedCategories.contains("RIVER")) riverCount++;
        if (matchedCategories.contains("LAKE")) lakeCount++;

        Log.d("ENV_COUNT", "공원: " + parkCount
                + ", 숲: " + forestCount
                + ", 하천: " + riverCount
                + ", 호수: " + lakeCount);
    }

    // [추가] 최다 집계 환경을 반환하며 동률은 PARK, FOREST, RIVER, LAKE 순으로 선택합니다.
    public String getFinalEnvironment() {
        if (parkCount == 0 && forestCount == 0 && riverCount == 0 && lakeCount == 0) {
            return "CITY";
        }
        int maxCount = Math.max(Math.max(parkCount, forestCount), Math.max(riverCount, lakeCount));
        if (parkCount == maxCount) return "PARK";
        if (forestCount == maxCount) return "FOREST";
        if (riverCount == maxCount) return "RIVER";
        return "LAKE";
    }

    // [추가] Activity에서 환경별 집계 결과를 로그로 확인할 때 사용합니다.
    public String getEnvironmentCountSummary() {
        return "공원: " + parkCount + ", 숲: " + forestCount
                + ", 하천: " + riverCount + ", 호수: " + lakeCount;
    }
    // [추가] 러닝 기록에 저장할 시간·거리·페이스·평균 속도를 서비스 기준으로 계산합니다.
    public RunRecord createRunRecord(String userId) {
        float durationSec = getElapsedMillis() / 1000f;
        float distanceKm = meters / 1000f;
        float paceSecondsPerKm = distanceKm > 0 ? durationSec / distanceKm : 0;
        float averageSpeedKmh = durationSec > 0
                ? distanceKm / (durationSec / 3600f)
                : 0;
        return new RunRecord(
                0,
                userId,
                runStartTime,
                meters,
                durationSec,
                paceSecondsPerKm,
                averageSpeedKmh,
                maxSpeed
        );
    }
    public String getPaceText() {
        if (meters < 10) return "--'--\"/km";
        long elapsed = getElapsedMillis();
        if (elapsed <= 0) return "--'--\"/km";
        double paceSeconds = elapsed / (meters / 1000.0) / 1000.0;
        int rounded = (int) (Math.round(paceSeconds / 5.0) * 5);
        return String.format(Locale.getDefault(), "%d'%02d\"/km", rounded / 60, rounded % 60);
    }
    public float getMaxSpeed() {
        return maxSpeed;
    }

    public long getElapsedMillis() {
        return accumulated
                + (active && !paused
                ? SystemClock.elapsedRealtime() - startedAt
                : 0);
    }

    public int getRouteVersion() {
        return routeVersion;
    }

    // Activity가 이미 그린 좌표 뒤에 추가된 점만 반환해 전체 경로 복사를 줄입니다.
    public List<List<Location>> getRouteFrom(int[] pointCounts) {
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
    public List<List<Location>> getRoute() {
        List<List<Location>> copy = new ArrayList<>();

        for (List<Location> segment : segments) {
            List<Location> points = new ArrayList<>();

            for (Location point : segment) {
                points.add(new Location(point));
            }

            copy.add(points);
        }

        return copy;
    }

    public void pauseRun() {
        if (!active || paused) return;

        accumulated = getElapsedMillis();
        paused = true;
        previous = null;

        updateNotification();
    }

    public void resumeRun() {
        if (!active || !paused) return;

        if (!hasLocation()) {
            error = "위치 권한이 해제되었습니다.";
            finishRun();
            return;
        }

        previous = null;

        // 일시정지 동안 이동한 위치를 이전 경로와 연결하지 않음.
        segments.add(new ArrayList<>());
        routeVersion++;

        startedAt = SystemClock.elapsedRealtime();
        resumedAtNanos = SystemClock.elapsedRealtimeNanos();
        paused = false;

        updateNotification();
    }

    public void finishRun() {
        accumulated = getElapsedMillis();
        active = false;
        paused = true;

        client.removeLocationUpdates(callback);

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private PendingIntent command(String action, int requestCode) {
        return PendingIntent.getService(
                this,
                requestCode,
                new Intent(this, RunningService.class)
                        .setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private Notification notification() {
        Intent open = new Intent(this, RunningActivity.class)
                .putExtra("from_notification", true)
                .addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                );

        PendingIntent content = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        String distance = String.format(
                Locale.KOREA,
                "%.2f km",
                meters / 1000f
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL)
                        .setSmallIcon(R.drawable.ic_running_notification)
                        .setContentTitle(
                                paused
                                        ? "Runcatch · 일시정지"
                                        : "Runcatch · 러닝 중"
                        )
                        .setContentText(
                                distance + " · "
                                        + (error != null
                                        ? error
                                        : paused
                                          ? "다시 시작을 눌러 이어 달리세요"
                                          : "러닝 시간을 기록하고 있어요")
                        )
                        .setContentIntent(content)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .setForegroundServiceBehavior(
                                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                        );

        if (!paused) {
            builder
                    .setWhen(
                            System.currentTimeMillis() - getElapsedMillis()
                    )
                    .setUsesChronometer(true)
                    .setShowWhen(true);
        } else {
            builder
                    .setSubText(
                            "기록 시간 " + formatTime(getElapsedMillis())
                    )
                    .setShowWhen(false);
        }

        builder.addAction(
                paused
                        ? android.R.drawable.ic_media_play
                        : android.R.drawable.ic_media_pause,
                paused ? "다시 시작" : "일시정지",
                command(
                        paused ? RESUME : PAUSE,
                        paused ? 2 : 1
                )
        );

        return builder.build();
    }

    private void updateNotification() {
        if (active) {
            getSystemService(NotificationManager.class).notify(
                    NOTIFICATION,
                    notification()
            );
        }
    }

    public static String formatTime(long millis) {
        long seconds = millis / 1000;

        return String.format(
                Locale.KOREA,
                "%02d:%02d:%02d",
                seconds / 3600,
                seconds / 60 % 60,
                seconds % 60
        );
    }

    @Override
    public void onDestroy() {
        client.removeLocationUpdates(callback);
        super.onDestroy();
    }
}
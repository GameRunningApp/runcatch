package com.cookandroid.runcatch;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.*;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.*;

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

    // [추가] Activity가 재생성되어도 시작 시각과 최고 속도를 유지.
    private long runStartTime;
    private float maxSpeed;

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

                // 중복 좌표, 재개 전 좌표, 정확도가 낮은 좌표 제외.
                if (timestamp <= lastFixNanos
                        || timestamp < resumedAtNanos
                        || !location.hasAccuracy()
                        || location.getAccuracy() > 50) {
                    continue;
                }

                // 오래된 위치 정보 제외.
                if (SystemClock.elapsedRealtimeNanos() - timestamp
                        > 30_000_000_000L) {
                    continue;
                }

                if (previous != null) {
                    float step = previous.distanceTo(location);

                    double seconds =
                            (timestamp - previous.getElapsedRealtimeNanos())
                                    / 1e9;

                    // 시간이 역전되거나 비정상적으로 크게 튄 좌표 제외.
                    if (seconds <= 0
                            || (seconds < 30 && step / seconds > 12)) {
                        continue;
                    }

                    // GPS가 오래 끊겼다면 새 구간으로 분리.
                    if (seconds >= 30) {
                        segments.add(new ArrayList<>());
                    } else {
                        meters += step;
                    }
                }

                // [추가] 기존 Activity와 동일한 단위(km/h)로 보관.
                if (location.hasSpeed()) {
                    maxSpeed = Math.max(
                            maxSpeed,
                            location.getSpeed() * 3.6f
                    );
                }

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
        accumulated = 0;
        lastFixNanos = 0;
        previous = null;

        segments.clear();
        segments.add(new ArrayList<>());
        routeVersion++;

        active = true;
        paused = false;
        error = null;

        // [수정] 경과 시간 계산은 밀리초.
        startedAt = SystemClock.elapsedRealtime();

        // [수정] GPS 좌표의 시간 비교는 나노초.
        resumedAtNanos = SystemClock.elapsedRealtimeNanos();

        // [추가] DB 저장용 시작 시각과 최고 속도.
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

    // [추가] 서비스가 기록한 누적 거리(m).
    public float getMeters() {
        return meters;
    }

    // [추가] 기존 Activity의 DB 저장용 변수에 전달.
    public long getRunStartTime() {
        return runStartTime;
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
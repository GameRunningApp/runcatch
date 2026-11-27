package com.cookandroid.runcatch.service;

import com.cookandroid.runcatch.core.domain.RunRecord;
import com.cookandroid.runcatch.environment.EnvironmentDataLoader;
import com.cookandroid.runcatch.environment.EnvironmentFeature;
import com.cookandroid.runcatch.format.RunFormat;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.ArrayList;
import java.util.List;

/**
 * 러닝 측정 포그라운드 서비스. 화면이 꺼지거나 다른 앱으로 가도 GPS 기록을 계속한다.
 * 이 클래스는 서비스 실행, 종료, 위치 요청, 화면(RunningActivity)과의 연결만 맡는다.
 *   측정 값(거리, 시간, 경로, 환경) -> RunTracker (러닝마다 새로 만듦)
 *   알림 -> RunningNotification
 *
 *   환경 집계는 EnvironmentTally로 시간, 페이스 문자열은 RunFormat으로 옮김
 *   측정 상태와 좌표 거르기는 RunTracker로, 알림은 RunningNotification으로 분리
 *   쓰이지 않던 getMeters(), getRunStartTime(), getMaxSpeed() 삭제
 */
public class RunningService extends Service {
    public static final String START = "com.cookandroid.runcatch.START";
    public static final String PAUSE = "com.cookandroid.runcatch.PAUSE";
    public static final String RESUME = "com.cookandroid.runcatch.RESUME";

    /** 알림을 눌러 RunningActivity가 열렸는지 (서비스가 만드는 값이라 서비스에 둠) */
    public static final String EXTRA_FROM_NOTIFICATION = "from_notification";

    private static final long LOCATION_INTERVAL_MS = 2000;
    private static final long LOCATION_MIN_INTERVAL_MS = 1000;

    private final LocalBinder binder = new LocalBinder();
    private FusedLocationProviderClient client;
    private RunningNotification notification;
    private List<EnvironmentFeature> environmentFeatures;

    /** 진행 중이거나 마지막으로 끝난 러닝. 한 번도 시작하지 않았으면 null */
    private RunTracker tracker;
    private boolean active;
    private String error;

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
        notification = new RunningNotification(this);

        // 미리 저장해 둔 환경 데이터를 서비스에서 한 번 불러옵니다.
        environmentFeatures = EnvironmentDataLoader.load(this);
        if (environmentFeatures == null) environmentFeatures = new ArrayList<>();
    }

    private final LocationCallback callback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            if (!active || tracker.isPaused()) return;

            for (Location location : result.getLocations()) {
                if (tracker.add(location)) error = null;
            }

            updateNotification();
        }

        @Override
        public void onLocationAvailability(LocationAvailability availability) {
            if (!active) return;

            error = availability.isLocationAvailable() ? null : "GPS 신호를 기다리는 중";

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
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void begin() {
        // 새 러닝은 새 RunTracker로 시작 (이전 러닝 값이 남지 않음)
        tracker = new RunTracker(environmentFeatures);
        active = true;
        error = null;

        try {
            // startForegroundService()로 시작됐으므로 권한이 없어도 startForeground()를 먼저 불러야 한다.
            // 부르지 않고 stopSelf()하면 시스템이 앱을 강제 종료한다.
            // (Android 14 이상은 위치 권한 없이 부르면 SecurityException → 아래 catch.
            //  화면이 시작 전에 권한을 확인하고, 권한을 끄면 앱 프로세스가 종료되므로 실제로는 거의 오지 않음)
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(RunningNotification.ID, notification.build(tracker, error),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(RunningNotification.ID, notification.build(tracker, error));
            }

            if (!hasLocation()) {
                error = "위치 권한이 필요합니다";
                finishRun();
                return;
            }

            LocationRequest request = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
                    .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MS)
                    .setMaxUpdateDelayMillis(0)
                    .build();

            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    .addOnFailureListener(failure -> {
                        if (active) {
                            error = "위치 수신을 시작하지 못했습니다";
                            finishRun();
                        }
                    });
        } catch (RuntimeException failure) {
            error = "러닝 서비스를 시작하지 못했습니다. 위치 권한과 위치 설정을 확인해 주세요";
            finishRun();
        }
    }

    // ===== 화면(RunningActivity)이 읽는 값 =====

    public boolean isActive() {
        return active;
    }

    public boolean isPaused() {
        return tracker != null && tracker.isPaused();
    }

    public String getError() {
        return error;
    }

    /** 누적 거리 (km). 화면 표시와 결과 화면 전달용 */
    public float getKilometers() {
        return tracker == null ? 0 : tracker.meters() / 1000f;
    }

    public long getElapsedMillis() {
        return tracker == null ? 0 : tracker.elapsedMillis();
    }

    /** 측정 중 페이스 문구 (10m 미만이면 --'--"/km) */
    public String getPaceText() {
        return RunFormat.pace(tracker == null ? 0 : tracker.livePaceSeconds());
    }

    /** 가장 많이 지나간 환경 (규칙은 EnvironmentTally) */
    public String getFinalEnvironment() {
        return tracker == null ? EnvironmentFeature.CITY : tracker.finalEnvironment();
    }

    /** 러닝 기록에 저장할 시간 · 거리 · 페이스 · 속도. 러닝을 시작한 뒤에만 호출 */
    public RunRecord createRunRecord(String userId) {
        return tracker.createRunRecord(userId);
    }

    public int getRouteVersion() {
        return tracker == null ? 0 : tracker.routeVersion();
    }

    /** Activity가 이미 그린 좌표 뒤에 추가된 점만 반환해 전체 경로 복사를 줄입니다. */
    public List<List<Location>> getRouteFrom(int[] pointCounts) {
        return tracker == null ? new ArrayList<>() : tracker.routeFrom(pointCounts);
    }

    public List<List<Location>> getRoute() {
        return tracker == null ? new ArrayList<>() : tracker.route();
    }

    // ===== 화면 · 알림 버튼이 보내는 명령 =====

    public void pauseRun() {
        if (!active || tracker.isPaused()) return;

        tracker.pause();
        updateNotification();
    }

    public void resumeRun() {
        if (!active || !tracker.isPaused()) return;

        if (!hasLocation()) {
            error = "위치 권한이 해제되었습니다.";
            finishRun();
            return;
        }

        tracker.resume();
        updateNotification();
    }

    public void finishRun() {
        if (tracker != null) tracker.finish();
        active = false;

        client.removeLocationUpdates(callback);

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void updateNotification() {
        if (active) notification.update(tracker, error);
    }

    @Override
    public void onDestroy() {
        client.removeLocationUpdates(callback);
        super.onDestroy();
    }
}

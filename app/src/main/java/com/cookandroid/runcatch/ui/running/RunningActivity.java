package com.cookandroid.runcatch.ui.running;

import com.cookandroid.runcatch.R;
import com.cookandroid.runcatch.core.Session;
import com.cookandroid.runcatch.core.domain.GpsPoint;
import com.cookandroid.runcatch.core.domain.RunRecord;
import com.cookandroid.runcatch.run.RunFinisher;
import com.cookandroid.runcatch.service.RunningService;
import com.cookandroid.runcatch.format.RunFormat;

import android.os.Bundle;
import android.widget.Toast;
import android.widget.Button;

import android.widget.Chronometer;
import android.os.SystemClock;

import android.widget.TextView;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;


import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.camera.CameraUpdateFactory;

import android.graphics.Color;
import org.maplibre.android.annotations.Polyline;
import org.maplibre.android.annotations.PolylineOptions;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// [추가] 서비스 연결, 화면 갱신, 알림 권한 확인에 필요한 import.
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.view.View;
import java.util.Locale;

/**
 *   종료 시 저장, 보상, 경험치는 RunFinisher로 옮김
 *   Intent 키는 RunExtras, 지도 주소는 MAP_STYLE_URL 상수로 통일
 */
public class RunningActivity extends AppCompatActivity {
    static final String MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";

    private Button btnPause_Resume;
    private Button btnStop;
    private boolean isPaused = false;

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    // 지도 스타일 로딩 완료 여부
    private boolean isMapStyleLoaded = false;
    //마커 표시
    private org.maplibre.android.annotations.Marker currentLocationMarker;
    // 러닝 경로에 사용할 GPS 좌표 목록
    private List<LatLng> routePoints = new ArrayList<>();
    // 지도에 표시할 러닝 경로 선
    private Polyline runningRoute;
    private boolean isFirstLocation = true;

    //Timer
    private Chronometer chronometer;
    private long pauseOffset = 0; // 일시정지된 시점까지 누적된 시간

    //GPS
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private TextView runningDistanceText;
    private TextView runningPaceText;

    //DB 저장용
    // 지도 경로(routePoints)는 일시정지마다 새로 만들어지므로, 저장할 좌표는 따로 모음 (러닝 중인 좌표만)
    private final List<LatLng> recordedPoints = new ArrayList<>();

    // [추가] 화면은 서비스의 값을 표시하고, 실제 기록은 서비스가 관리합니다.
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private TextView titleText;

    // [추가] 중복 종료 방지, 서비스 연결 여부, 화면 활성 여부,
    // 위치 권한 확인 여부, 서비스 시작 요청 여부.
    private boolean finishingRun;
    private boolean bound;
    private boolean resumed;
    private boolean permissionsReady;
    private boolean startRequested;

    // [추가] 같은 오류 안내 반복 및 불필요한 경로 재그리기 방지.
    private String lastError;
    private int drawnVersion = -1;
    private final List<Polyline> serviceRouteLines = new ArrayList<>();
    // [추가] 구간별 지도 Polyline에 반영한 좌표 수를 저장합니다.
    private final List<Integer> drawnSegmentPointCounts = new ArrayList<>();

    private final Handler serviceHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable serviceRefresh = new Runnable() {
        @Override
        public void run() {
            render();

            // [추가] 화면이 보이는 동안만 1초마다 화면 갱신.
            if (resumed) {
                serviceHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {


        // MapLibre 초기화
        org.maplibre.android.MapLibre.getInstance(this);

        super.onCreate(savedInstanceState);
        // [추가] 러닝 화면이 잠금화면 위에 측정 화면으로 계속 보이게 합니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
        } else {
            getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            );
        }
        setContentView(R.layout.activity_running);

        //뷰 설정
        btnPause_Resume = findViewById(R.id.btnPause);
        btnStop = findViewById(R.id.btnStop);
        chronometer = findViewById(R.id.chronometer);
        runningDistanceText = findViewById(R.id.tvDistanceText);
        runningPaceText = findViewById(R.id.tvPace);
        runningPaceText.setText(RunFormat.EMPTY_PACE);

        // [추가] 서비스 준비 전에는 버튼 조작 방지.
        titleText = findViewById(R.id.tvTitle);
        btnPause_Resume.setVisibility(View.VISIBLE);
        btnPause_Resume.setEnabled(false);
        btnStop.setEnabled(false);

        // [추가] 화면 재생성 시 중복 시작/종료 방지.
        if (savedInstanceState != null) {
            startRequested =
                    savedInstanceState.getBoolean("serviceStartRequested");
            finishingRun =
                    savedInstanceState.getBoolean("finishingRun");
        }

        // 지도 화면 연결
        mapView = findViewById(R.id.mapPlaceholder);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(map -> {
            mapLibreMap = map;

            map.setStyle(
                    MAP_STYLE_URL,
                    style -> {
                        isMapStyleLoaded = true;
                        // [추가] 지도 로딩 전에 수신한 경로도 복원합니다.
                        // [추가] 새 지도 스타일에는 경로가 비어 있어 좌표 렌더링 기준을 초기화합니다.
                        serviceRouteLines.clear();
                        drawnSegmentPointCounts.clear();
                        drawnVersion = -1;
                        render();

                        android.util.Log.d(
                                "RUNCATCH_MAP",
                                "지도 스타일 로드 성공"
                        );
                    }
            );
        });

        // [수정] 위치 수신과 좌표 집계는 RunningService가 담당합니다.
        btnPause_Resume.setOnClickListener(v -> Pause_Resume());
        btnStop.setOnClickListener(v -> StopRunning());

        // 종료 처리(서버 응답 대기) 중에 화면이 다시 만들어진 경우: 기록은 이미 저장됐고
        // 결과 화면은 이전 화면의 콜백이 연다. 이 화면이 남으면 버튼이 막힌 채로 갇히므로 닫는다
        // (mapView.onCreate 뒤라서 onDestroy의 mapView.onDestroy()가 안전함)
        if (finishingRun) {
            finish();
            return;
        }

        // [수정] 타이머의 기준은 서비스가 관리합니다.
        startTimer();

        // GPS 권한 확인
        checkPermission_Start();
    }

    private void startTimer() {
        // [수정] 화면 생성/갱신 때 러닝 시작 시각을 초기화하지 않습니다.
        chronometer.setOnChronometerTickListener(chronometer -> updatePace());
        if (service == null) {
            chronometer.stop();
            chronometer.setBase(SystemClock.elapsedRealtime());
            return;
        }
        pauseOffset = service.getElapsedMillis();
        chronometer.setBase(SystemClock.elapsedRealtime() - pauseOffset);
        if (service.isActive() && !service.isPaused()) chronometer.start();
        else chronometer.stop();
    }
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }
    // [수정] 페이스는 서비스가 계산하고 Activity는 화면 표시만 담당합니다.
    // [수정] 페이스는 서비스 계산값을 그대로 화면에 표시합니다.
    private void updatePace() {
        if (service == null) {
            runningPaceText.setText(RunFormat.EMPTY_PACE);
            return;
        }
        runningPaceText.setText(service.getPaceText());
    }
    private void checkPermission_Start() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );
            return;
        }
        startLocationUpdates();
    }
    private void startLocationUpdates() {
        if (!hasLocationPermission()) return;
        // [수정] Activity와 서비스가 GPS를 중복 수신하지 않도록 서비스만 시작합니다.
        permissionsReady = true;
        maybeStart();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[]grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (hasLocationPermission()) {
                if (!isPaused) startLocationUpdates();
            } else {
                Toast.makeText(this, "거리 측정을 위해 위치 권한이 필요합니다", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void Pause_Resume() {
        // [수정] 알림 버튼과 화면 버튼이 동일한 서비스 상태를 변경합니다.
        if (service == null || !service.isActive() || finishingRun) return;
        if (service.isPaused()) service.resumeRun();
        else service.pauseRun();
        render();
    }
    private void StopRunning() {
        // [추가] 기록을 먼저 일시정지하고 마지막 좌표까지
        // 기존 저장용 변수에 옮깁니다.
        if (service == null || !service.isActive() || finishingRun) return;

        service.pauseRun();
        render();

        recordedPoints.clear();

        for (List<Location> segment : service.getRoute()) {
            for (Location point : segment) {
                recordedPoints.add(
                        new LatLng(
                                point.getLatitude(),
                                point.getLongitude()
                        )
                );
            }
        }
        finishingRun = true;
        btnPause_Resume.setEnabled(false);
        // [수정] 종료 경과 시간은 서비스의 누적값을 사용합니다.
        long elapsedTime = service.getElapsedMillis();
        chronometer.stop();
        //중복 저장 방지
        btnStop.setEnabled(false);

        //러닝 기록 DB 저장
        // core 모듈은 MapLibre에 의존하지 않으므로 좌표 타입만 변환합니다.
        List<GpsPoint> points = new ArrayList<>();
        for (LatLng point : recordedPoints) {
            points.add(new GpsPoint(point.getLatitude(), point.getLongitude()));
        }
        // [추가] 서비스에서 집계한 최종 환경을 기록 저장과 보상 요청에 전달합니다.
        final String finalEnvironment = service.getFinalEnvironment();
        RunFinisher finisher = new RunFinisher(this);
        RunRecord record = finisher.save(
                service.createRunRecord(Session.userId(this)), finalEnvironment, points);
        // [추가] 저장 실패 시 일시정지 상태를 유지해 다시 저장할 수 있게 합니다.
        if (record == null) {
            Toast.makeText(this, "러닝 기록 저장에 실패했습니다", Toast.LENGTH_SHORT).show();
            finishingRun = false;
            render();
            return;
        }
        // 보상 응답을 기다리는 동안 화면이 꺼지면 service가 null이 되므로 값을 미리 복사
        final float distanceKm = service.getKilometers();
        service.finishRun();

        // 보상 판정 → 획득 캐릭터 저장 → 경험치 적용 (RunFinisher)
        // 획득한 캐릭터가 있으면 캐릭터 획득 화면, 없으면 바로 결과 화면으로
        finisher.finish(record, finalEnvironment, result -> {
            if (result.rewardCheckFailed) {
                Toast.makeText(getApplicationContext(),
                        "서버에 연결하지 못해 캐릭터 획득을 확인하지 못했습니다", Toast.LENGTH_LONG).show();
            }
            Intent intent;

            if (result.newRewards.isEmpty()) {
                intent = new Intent(RunningActivity.this, ResultActivity.class);
            } else {
                intent = new Intent(RunningActivity.this, CharacterRewardActivity.class);
                CharacterRewardActivity.putRewards(intent, result.newRewards);
            }

            intent.putExtra(RunExtras.DISTANCE, distanceKm);
            intent.putExtra(RunExtras.ELAPSED_TIME, elapsedTime);
            intent.putExtra(RunExtras.FINAL_ENVIRONMENT, finalEnvironment);

            // GPS 경로 전달
            double[] routeLatitude = new double[recordedPoints.size()];
            double[] routeLongitude = new double[recordedPoints.size()];

            for (int i = 0; i < recordedPoints.size(); i++) {
                routeLatitude[i] = recordedPoints.get(i).getLatitude();
                routeLongitude[i] = recordedPoints.get(i).getLongitude();
            }

            intent.putExtra(RunExtras.ROUTE_LATITUDE, routeLatitude);
            intent.putExtra(RunExtras.ROUTE_LONGITUDE, routeLongitude);
            //경험치 전달
            result.expGain.putInto(intent);

            startActivity(intent);

            // 현재 화면 종료
            finish();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
        // [추가] 화면과 서비스 연결.
        // 시작된 서비스는 화면 연결이 끊겨도 GPS 기록을 계속합니다.
        if (!bound) {
            bound = bindService(
                    new Intent(this, RunningService.class),
                    connection,
                    BIND_AUTO_CREATE
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        // [추가] 최초 서비스 시작은 화면이 보이고 위치 권한이 있을 때만 실행합니다.
        resumed = true;
        permissionsReady = hasLocationPermission();
        maybeStart();
        serviceHandler.removeCallbacks(serviceRefresh);
        serviceHandler.post(serviceRefresh);
    }

    @Override
    protected void onPause() {
        // [추가] 화면 갱신만 멈추며 서비스의 위치 수신은 유지합니다.
        resumed = false;
        serviceHandler.removeCallbacks(serviceRefresh);
        chronometer.stop();
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        // [추가] 화면과 서비스의 연결만 해제하여 백그라운드 기록을 유지합니다.
        if (bound) { unbindService(connection); bound = false; }
        service = null; // 화면 연결만 해제하고 서비스는 계속 실행합니다.
        mapView.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mapView.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
        // [추가] 화면 재생성 시 동일 러닝을 새로 시작하지 않습니다.
        outState.putBoolean("serviceStartRequested", startRequested);
        outState.putBoolean("finishingRun", finishingRun);
    }

    private RunningService service;
    // [추가] 알림을 눌러 기존 러닝 화면으로 돌아왔을 때 최신 상태를 표시합니다.
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        render();
    }

    // [추가] 권한, 화면 상태, 중복 시작을 확인한 뒤 서비스 시작.
    private void maybeStart() {
        if (!resumed
                || !permissionsReady
                || service == null
                || finishingRun) {
            return;
        }

        if (service.isActive()) {
            startRequested = true;
            return;
        }

        if (startRequested
                || getIntent().getBooleanExtra(RunningService.EXTRA_FROM_NOTIFICATION, false)) {
            titleText.setText(
                    "진행 중인 러닝이 없습니다. 홈에서 다시 시작해 주세요"
            );
            return;
        }

        try {
            ContextCompat.startForegroundService(
                    this,
                    new Intent(this, RunningService.class)
                            .setAction(RunningService.START)
            );

            startRequested = true;

            // [추가] 알림 허용 여부와 관계없이 GPS 기록은 시작.
            if (Build.VERSION.SDK_INT >= 33
                    && ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.POST_NOTIFICATIONS
                        },
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                );
            }
        } catch (RuntimeException error) {
            titleText.setText("러닝 서비스를 시작하지 못했습니다");

            Toast.makeText(
                    this,
                    "위치 권한과 위치 설정을 확인하고 다시 시작해 주세요",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // [추가] 서비스가 백그라운드에서 모은 경로를 기존 지도 변수로 표시.
    // [수정] 전체 경로를 매번 제거하지 않고 서비스에서 새로 모은 점만 지도 선에 덧붙입니다.
    private void drawRoute() {
        if (service == null || !isMapStyleLoaded || mapLibreMap == null) return;
        int[] renderedCounts = new int[drawnSegmentPointCounts.size()];
        for (int i = 0; i < renderedCounts.length; i++) renderedCounts[i] = drawnSegmentPointCounts.get(i);
        List<List<Location>> addedSegments = service.getRouteFrom(renderedCounts);
        LatLng lastPosition = null;
        for (int i = 0; i < addedSegments.size(); i++) {
            List<LatLng> newPoints = new ArrayList<>();
            for (Location point : addedSegments.get(i)) {
                lastPosition = new LatLng(point.getLatitude(), point.getLongitude());
                newPoints.add(lastPosition);
            }
            if (i >= serviceRouteLines.size()) {
                // [추가] 재개 직후 빈 구간도 인덱스만 보관하고 실제 선은 좌표가 있을 때 만듭니다.
                if (newPoints.isEmpty()) {
                    serviceRouteLines.add(null);
                    drawnSegmentPointCounts.add(0);
                } else {
                    Polyline line = mapLibreMap.addPolyline(new PolylineOptions()
                            .addAll(newPoints).color(Color.BLUE).width(5f));
                    serviceRouteLines.add(line);
                    drawnSegmentPointCounts.add(newPoints.size());
                }
            } else if (!newPoints.isEmpty()) {
                Polyline line = serviceRouteLines.get(i);
                if (line == null) {
                    // [추가] 처음 GPS 점이 들어온 빈 구간의 경로선을 생성합니다.
                    line = mapLibreMap.addPolyline(new PolylineOptions()
                            .addAll(newPoints).color(Color.BLUE).width(5f));
                    serviceRouteLines.set(i, line);
                    drawnSegmentPointCounts.set(i, newPoints.size());
                } else {
                    List<LatLng> allPoints = new ArrayList<>(line.getPoints());
                    allPoints.addAll(newPoints);
                    line.setPoints(allPoints);
                    drawnSegmentPointCounts.set(i, allPoints.size());
                }
            }
        }
        // [추가] 새 GPS 점이 없을 때도 마지막 위치를 서비스 경로에서 복원합니다.
        if (lastPosition == null) {
            List<List<Location>> route = service.getRoute();
            for (int i = route.size() - 1; i >= 0; i--) {
                List<Location> segment = route.get(i);
                if (!segment.isEmpty()) {
                    Location point = segment.get(segment.size() - 1);
                    lastPosition = new LatLng(point.getLatitude(), point.getLongitude());
                    break;
                }
            }
        }
        if (lastPosition != null) {
            if (currentLocationMarker == null) {
                currentLocationMarker = mapLibreMap.addMarker(new org.maplibre.android.annotations.MarkerOptions()
                        .position(lastPosition).title("현재 위치"));
            } else currentLocationMarker.setPosition(lastPosition);
            if (isFirstLocation) {
                mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastPosition, 16));
                isFirstLocation = false;
            } else {
                mapLibreMap.moveCamera(CameraUpdateFactory.newLatLng(lastPosition));
            }
        }
        drawnVersion = service.getRouteVersion();
    }
    // [추가] onStart()의 bindService()와 연결.
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service =
                    ((RunningService.LocalBinder) binder).getService();

            drawnVersion = -1;
            maybeStart();
            render();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            btnPause_Resume.setEnabled(false);
            btnStop.setEnabled(false);
            titleText.setText("러닝 서비스 연결이 끊어졌습니다");
        }
    };
    // [수정] 기존 render()의 오타를 고치고 서비스 시간 동기화를 연결했습니다.
    // [수정] 서비스의 거리, 시간, 일시정지 상태를 화면에 반영.
    private void render() {
        if (service == null || finishingRun) return;
        boolean active = service.isActive();
        isPaused = service.isPaused();
        // [수정] 집계 값은 RunningService에서 받아 화면과 기록에 사용합니다.
        long elapsed = service.getElapsedMillis();
        if (isPaused) pauseOffset = elapsed;
        btnPause_Resume.setEnabled(active);
        btnStop.setEnabled(active);
        btnPause_Resume.setText(isPaused ? "다시시작" : "일시정지");
        startTimer();
        updatePace();
        runningDistanceText.setText(String.format(Locale.KOREA, "%.2f km", service.getKilometers()));
        String error = service.getError();
        if (error != null) {
            titleText.setText(error);
            if (!error.equals(lastError)) Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        } else if (active) {
            titleText.setText(isPaused ? "러닝 일시정지" : "러닝 측정 중");
        }
        lastError = error;
        if (isMapStyleLoaded && drawnVersion != service.getRouteVersion()) drawRoute();
    }
}

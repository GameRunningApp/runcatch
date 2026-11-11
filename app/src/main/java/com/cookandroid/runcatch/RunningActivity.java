package com.cookandroid.runcatch;

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

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

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
import com.cookandroid.runcatch.backend.GpsPoint;
import com.cookandroid.runcatch.backend.RunCatchDbHelper;
import com.cookandroid.runcatch.backend.RunRecord;

// [추가] 서비스 연결, 화면 갱신, 알림 권한 확인에 필요한 import.
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.view.View;
import java.util.Locale;

public class RunningActivity extends AppCompatActivity {
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
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private Location previousLocation;
    private float totalDistance = 0;

    //DB 저장용
    // 지도 경로(routePoints)는 일시정지마다 새로 만들어지므로, 저장할 좌표는 따로 모음 (러닝 중인 좌표만)
    private final List<LatLng> recordedPoints = new ArrayList<>();
    private float maxSpeed = 0; // km/h
    private long runStartTime;  // 러닝 시작 시각 (epoch ms)

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
        setContentView(R.layout.activity_running);

        //뷰 설정
        btnPause_Resume = findViewById(R.id.btnPause);
        btnStop = findViewById(R.id.btnStop);
        chronometer = findViewById(R.id.chronometer);
        runningDistanceText = findViewById(R.id.tvDistanceText);
        runningPaceText = findViewById(R.id.tvPace);
        runningPaceText.setText("--'--\"/km");

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
                    "https://tiles.openfreemap.org/styles/liberty",
                    style -> {
                        isMapStyleLoaded = true;
                        // [추가] 지도 로딩 전에 수신한 경로도 복원합니다.
                        drawnVersion = -1;
                        render();

                        android.util.Log.d(
                                "RUNCATCH_MAP",
                                "지도 스타일 로드 성공"
                        );
                    }
            );
        });

        //GPS
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 2000
        ).setMinUpdateIntervalMillis(1000).build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    LatLng currentPosition = new LatLng(
                            location.getLatitude(),
                            location.getLongitude()
                    );
                    // 지도와 스타일이 준비된 경우에만 표시 기능 실행
                    if (mapLibreMap != null && isMapStyleLoaded) {
                        // 1. 현재 위치 마커 생성 또는 이동
                        if (currentLocationMarker == null) {
                            currentLocationMarker = mapLibreMap.addMarker(
                                    new org.maplibre.android.annotations.MarkerOptions()
                                            .position(currentPosition)
                                            .title("현재 위치")
                            );
                        } else {
                            currentLocationMarker.setPosition(currentPosition);
                        }
                        // 상태와 관계없이 카메라가 현재 위치를 따라감
                        mapLibreMap.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                        currentPosition, 16
                                )
                        );
                        // 현재 상태의 경로에 좌표 추가
                        routePoints.add(currentPosition);
                        // 러닝 중 파란색, 일시정지 중 빨간색
                        int routeColor = isPaused ? Color.RED : Color.BLUE;
                        if (runningRoute == null) {
                            runningRoute = mapLibreMap.addPolyline(
                                    new PolylineOptions()
                                            .addAll(routePoints)
                                            .color(routeColor)
                                            .width(5f)
                            );
                        } else {
                            runningRoute.setPoints(routePoints);
                            runningRoute.setColor(routeColor);
                        }
                    }
                    // 거리 계산은 러닝 중에만 수행
                    if (!isPaused) {
                        // 저장용 좌표와 최고 속도 기록
                        recordedPoints.add(currentPosition);
                        if (location.hasSpeed()) {
                            maxSpeed = Math.max(maxSpeed, location.getSpeed() * 3.6f);
                        }
                        if (previousLocation != null) {
                            float distance =
                                    previousLocation.distanceTo(location);

                            totalDistance += distance;
                            runningDistanceText.setText(
                                    String.format(
                                            "%.2f Km",
                                            totalDistance / 1000
                                    )
                            );
                            updatePace();
                        }
                        previousLocation = location;
                    } else {
                        // 일시정지 중에는 거리 계산 기준 위치를 초기화
                        previousLocation = null;
                    }
                }
            }
        };
        btnPause_Resume.setOnClickListener(v -> Pause_Resume());
        btnStop.setOnClickListener(v -> StopRunning());

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
        runStartTime = service.getRunStartTime();
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
    private void updatePace() {
        if (totalDistance < 10) {
            runningPaceText.setText("--'--\"/km");
            return;
        }
        // 일시정지 시간을 제외한 러닝 경과 시간(ms)
        long elapsedMillis = isPaused
                ? pauseOffset
                : SystemClock.elapsedRealtime() - chronometer.getBase();
        if (elapsedMillis <= 0) {
            runningPaceText.setText("--'--\"/km");
            return;
        }
        // 전체 평균 페이스(초/km)
        double paceSeconds =
                elapsedMillis / (totalDistance / 1000.0) / 1000.0;
        // 화면 표시용 페이스를 5초 단위로 반올림
        int roundedPaceSeconds =
                (int) (Math.round(paceSeconds / 5.0) * 5);

        int minutes = roundedPaceSeconds / 60;
        int seconds = roundedPaceSeconds % 60;
        runningPaceText.setText(
                String.format(
                        java.util.Locale.getDefault(),
                        "%d'%02d\"/km",
                        minutes,
                        seconds
                )
        );
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
        maxSpeed = service.getMaxSpeed();
        finishingRun = true;
        btnPause_Resume.setEnabled(false);
        long elapsedTime = isPaused ?
                pauseOffset : SystemClock.elapsedRealtime() - chronometer.getBase();
        chronometer.stop();
        //Gps 위치 측정 종료
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        //중복 저장 방지
        btnStop.setEnabled(false);

        //러닝 기록 DB 저장
        RunRecord record = saveRunRecord(elapsedTime);
        // [추가] 저장 실패 시 일시정지 상태를 유지해 다시 저장할 수 있게 합니다.
        if (record.runningNumber == -1) {
            finishingRun = false;
            render();
            return;
        }
        service.finishRun();

        //서버에 러닝 기록을 넘겨서 캐릭터 획득 여부를 받음
        //획득한 캐릭터가 있으면 캐릭터 획득 화면, 없으면 바로 결과 화면으로
        RewardServer.requestRewards(record, rewards -> {
            //이미 가진 캐릭터는 빼고 새로 획득한 캐릭터만 저장/표시
            List<RewardServer.Reward> newRewards = saveRewards(rewards);
            //대표 캐릭터에 이번 러닝 경험치 적용 (캐릭터 저장 뒤에 해야 첫 캐릭터도 경험치를 받음)
            ExpGain expGain = applyExp(record);

            Intent intent;
            if (newRewards.isEmpty()) {
                intent = new Intent(RunningActivity.this, ResultActivity.class);
            } else {
                intent = new Intent(RunningActivity.this, CharacterRewardActivity.class);
                CharacterRewardActivity.putRewards(intent, newRewards);
            }

            //이동한 거리 전달
            intent.putExtra("distance", totalDistance / 1000);
            //걸린 시간 전달
            intent.putExtra("elapsedTime", elapsedTime);
            // GPS 경로 전달
            double[] routeLatitude = new double[recordedPoints.size()];
            double[] routeLongitude = new double[recordedPoints.size()];

            for (int i = 0; i < recordedPoints.size(); i++) {
                routeLatitude[i] = recordedPoints.get(i).getLatitude();
                routeLongitude[i] = recordedPoints.get(i).getLongitude();
            }
            intent.putExtra("routeLatitude", routeLatitude);
            intent.putExtra("routeLongitude", routeLongitude);
            //경험치 전달
            expGain.putInto(intent);

            startActivity(intent);

            //현재 화면 종료
            finish();
        });
    }

    /**
     * 이번 러닝 경험치를 계산해서 대표 캐릭터에 더한다.
     * 대표 캐릭터가 없거나 기록 저장에 실패했으면 계산만 하고 저장하지 않는다.
     */
    private ExpGain applyExp(RunRecord record) {
        RunCatchDbHelper db = RunCatchDbHelper.getInstance(this);
        String userId = RunCatchDbHelper.CURRENT_USER_ID;

        int streakDays = ExpRules.streakDays(
                db.getRunDates(userId), record.runDate, java.util.TimeZone.getDefault());
        // TODO(개인 목표): 목표 설정 기능이 생기면 달성 여부를 넘긴다 (+10)
        boolean goalAchieved = false;
        ExpGain gain = ExpGain.calculate(record.distance, streakDays, goalAchieved);

        int characterId = db.getCharacterId(userId);
        if (characterId == -1 || record.runningNumber == -1) {
            return gain;
        }
        int before = db.getCharacterExp(userId, characterId);
        if (before == -1) {
            return gain;
        }
        int after = db.addCharacterExp(userId, characterId, gain.total());
        return gain.appliedTo(characterId, before, after);
    }

    /** 획득한 캐릭터를 user_character에 저장. 새로 저장된 것만 돌려줌 */
    private List<RewardServer.Reward> saveRewards(List<RewardServer.Reward> rewards) {
        RunCatchDbHelper db = RunCatchDbHelper.getInstance(this);
        long now = System.currentTimeMillis();

        List<RewardServer.Reward> newRewards = new ArrayList<>();
        for (RewardServer.Reward reward : rewards) {
            if (db.addUserCharacter(RunCatchDbHelper.CURRENT_USER_ID, reward.character.id, now)) {
                newRewards.add(reward);
            }
        }
        return newRewards;
    }

    /** running_record + gps_point 저장. 저장된 running_number가 담긴 기록을 돌려줌 */
    private RunRecord saveRunRecord(long elapsedTime) {
        float durationSec = elapsedTime / 1000f;
        float distanceKm = totalDistance / 1000f;
        float pace = distanceKm > 0 ? durationSec / distanceKm : 0;                   // 초/km
        float avgSpeed = durationSec > 0 ? distanceKm / (durationSec / 3600f) : 0;    // km/h

        RunRecord record = new RunRecord(0, RunCatchDbHelper.CURRENT_USER_ID, runStartTime,
                totalDistance, durationSec, pace, avgSpeed, maxSpeed);

        // backend 모듈은 MapLibre에 의존하지 않으므로 LatLng를 GpsPoint로 변환해서 넘긴다
        List<GpsPoint> points = new ArrayList<>();
        for (LatLng p : recordedPoints) {
            points.add(new GpsPoint(p.getLatitude(), p.getLongitude()));
        }
        long runningNumber = RunCatchDbHelper.getInstance(this).insertRun(record, points);
        if (runningNumber == -1) {
            Toast.makeText(this, "러닝 기록 저장에 실패했습니다", Toast.LENGTH_SHORT).show();
        }

        return new RunRecord(runningNumber, record.userId, record.runDate, record.distance,
                record.duration, record.pace, record.avgSpeed, record.maxSpeed);
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
                || getIntent().getBooleanExtra("from_notification", false)) {
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
    private void drawRoute() {
        if (service == null
                || !isMapStyleLoaded
                || mapLibreMap == null) {
            return;
        }

        for (Polyline line : serviceRouteLines) {
            mapLibreMap.removePolyline(line);
        }

        serviceRouteLines.clear();
        routePoints = new ArrayList<>();
        runningRoute = null;
        previousLocation = null;

        LatLng lastPosition = null;
        // 일시정지/재개 구간을 각각 그려
        // 일시정지 중 이동한 구간을 연결하지 않습니다.
        for (List<Location> segment : service.getRoute()) {
            routePoints = new ArrayList<>();
            runningRoute = null;

            for (Location point : segment) {
                lastPosition = new LatLng(
                        point.getLatitude(),
                        point.getLongitude()
                );

                routePoints.add(lastPosition);
                previousLocation = new Location(point);
            }

            if (routePoints.size() >= 2) {
                runningRoute = mapLibreMap.addPolyline(
                        new PolylineOptions()
                                .addAll(routePoints)
                                .color(Color.BLUE)
                                .width(5f)
                );

                serviceRouteLines.add(runningRoute);
            }
        }

        if (lastPosition != null) {
            if (currentLocationMarker == null) {
                currentLocationMarker = mapLibreMap.addMarker(
                        new org.maplibre.android.annotations.MarkerOptions()
                                .position(lastPosition)
                                .title("현재 위치")
                );
            } else {
                currentLocationMarker.setPosition(lastPosition);
            }

            if (isFirstLocation) {
                mapLibreMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(lastPosition, 16)
                );

                isFirstLocation = false;
            } else {
                mapLibreMap.moveCamera(
                        CameraUpdateFactory.newLatLng(lastPosition)
                );
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
        totalDistance = service.getMeters();

        long elapsed = service.getElapsedMillis();

        if (isPaused) {
            pauseOffset = elapsed;
        }

        btnPause_Resume.setEnabled(active);
        btnStop.setEnabled(active);
        btnPause_Resume.setText(isPaused ? "다시시작" : "일시정지");

        startTimer();
        updatePace();

        runningDistanceText.setText(
                String.format(Locale.KOREA,
                        "%.2f km",
                        totalDistance / 1000f
                )
        );

        String error = service.getError();

        if (error != null) {
            titleText.setText(error);

            if (!error.equals(lastError)) {
                Toast.makeText(
                        this,
                        error,
                        Toast.LENGTH_LONG
                ).show();
            }
        } else if (active) {
            titleText.setText(
                    isPaused ? "러닝 일시정지" : "러닝 측정 중"
            );
        }

        lastError = error;
        if (isMapStyleLoaded
                && drawnVersion != service.getRouteVersion()) {
            drawRoute();
        }
    }
}

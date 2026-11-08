package com.cookandroid.runcatch;

import android.os.Bundle;
import android.util.Log;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
import java.util.Map;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.cookandroid.runcatch.backend.GpsPoint;
import com.cookandroid.runcatch.backend.RunCatchDbHelper;
import com.cookandroid.runcatch.backend.RunRecord;

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
    private List<LatLng> recordedPoints = new ArrayList<>();
    // 각 GPS 좌표가 기록된 시간 (밀리초)
    private List<Long> recordedTimestamps = new ArrayList<>();
    // 일시정지 후 다시 시작한 좌표의 인덱스
    private List<Integer> breakIndices = new ArrayList<>();
    private float maxSpeed = 0; // km/h
    private long runStartTime;  // 러닝 시작 시각 (epoch ms)

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

        // 지도 화면 연결
        mapView = findViewById(R.id.mapPlaceholder);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(map -> {
            mapLibreMap = map;

            map.setStyle(
                    "https://tiles.openfreemap.org/styles/liberty",
                    style -> {
                        isMapStyleLoaded = true;
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
                        recordedPoints.add(currentPosition);
                        // GPS 좌표가 실제로 측정된 시각 저장
                        long timestamp = location.getElapsedRealtimeNanos() / 1_000_000L;
                        recordedTimestamps.add(timestamp);
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

        startTimer(); // 화면 들어오자마자 바로 타이머 시작

        // GPS 권한 확인
        checkPermission_Start();
    }

    private void startTimer() {
        runStartTime = System.currentTimeMillis();
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.setOnChronometerTickListener(
                chronometer -> updatePace()
        );
        chronometer.start();
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
        fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                getMainLooper()
        );
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
        if (!isPaused) {
            // 러닝 일시정지
            pauseOffset =
                    SystemClock.elapsedRealtime()
                            - chronometer.getBase();
            chronometer.stop();
            isPaused = true;
            btnPause_Resume.setText("계속하기");
        } else {
            // 러닝 재개
            chronometer.setBase(
                    SystemClock.elapsedRealtime() - pauseOffset
            );
            chronometer.start();
            isPaused = false;

            // 재개 후 처음 기록될 GPS 좌표의 인덱스 저장
            if (!recordedPoints.isEmpty()) {
                breakIndices.add(recordedPoints.size());
            }

            btnPause_Resume.setText("일시정지");
        }
        // 상태가 바뀌면 새로운 경로 구간 시작
        LatLng lastPosition = null;
        if (previousLocation != null) {
            lastPosition = new LatLng(
                    previousLocation.getLatitude(),
                    previousLocation.getLongitude()
            );
        }
        routePoints = new ArrayList<>();
        // 이전 구간의 마지막 위치를 새 구간의 시작점으로 사용
        if (lastPosition != null) {
            routePoints.add(lastPosition);
        }
        runningRoute = null;
        previousLocation = null;
    }

    // 환경 분석이 완료되면 환경별 체류 시간을 전달하는 콜백
    private interface EnvironmentCallback {
        void onComplete(Map<String, Long> environmentTimes);
    }

    // 환경 분석에 실패했을 때 사용할 기본값
    private Map<String, Long> createEmptyEnvironmentTimes() {
        Map<String, Long> times = new HashMap<>();

        times.put("공원", 0L);
        times.put("숲", 0L);
        times.put("하천", 0L);

        return times;
    }

    // 러닝 경로를 기준으로 환경 데이터 요청 및 체류 시간 계산
    private void searchEnvironmentForRoute(EnvironmentCallback callback) {

        // GPS 좌표가 없는 경우
        if (recordedPoints == null || recordedPoints.size() < 2) {
            Log.d("OVERPASS_TEST", "환경 분석에 필요한 GPS 좌표가 부족합니다.");
            callback.onComplete(createEmptyEnvironmentTimes());
            return;
        }

        // 첫 번째 좌표를 기준으로 경계값 초기화
        double south = recordedPoints.get(0).getLatitude();
        double north = recordedPoints.get(0).getLatitude();

        double west = recordedPoints.get(0).getLongitude();
        double east = recordedPoints.get(0).getLongitude();

        // 모든 GPS 좌표를 확인하며 경계값 계산
        for (LatLng point : recordedPoints) {
            double latitude = point.getLatitude();
            double longitude = point.getLongitude();

            south = Math.min(south, latitude);
            north = Math.max(north, latitude);

            west = Math.min(west, longitude);
            east = Math.max(east, longitude);
        }

        // 러닝 경로 주변 환경도 포함하도록 여유 추가
        double margin = 0.001;

        south -= margin;
        north += margin;
        west -= margin;
        east += margin;

        Log.d("OVERPASS_TEST", "러닝 경로 환경 검색 시작");
        Log.d("OVERPASS_TEST", "저장된 GPS 좌표 수: " + recordedPoints.size());
        Log.d("OVERPASS_TEST", "저장된 시간 수: " + recordedTimestamps.size());
        Log.d("OVERPASS_TEST", "일시정지 구간 수: " + breakIndices.size());

        // Overpass API 요청
        OverpassApi.searchEnvironment(
                south,
                west,
                north,
                east,
                new OverpassApi.Callback() {

                    @Override
                    public void onSuccess(String result) {
                        try {
                            // 1. Overpass JSON을 환경 객체로 변환
                            ArrayList<OsmEnvironment> environments =
                                    EnvironmentParser.parse(result);

                            Log.d("OVERPASS_TEST",
                                    "찾은 환경 객체 수: " + environments.size());

                            // 2. GPS 경로를 OsmCoordinate 형식으로 변환
                            ArrayList<OsmCoordinate> routeCoordinates =
                                    new ArrayList<>();

                            for (LatLng point : recordedPoints) {
                                routeCoordinates.add(
                                        new OsmCoordinate(
                                                point.getLatitude(),
                                                point.getLongitude()
                                        )
                                );
                            }

                            // 3. 환경별 체류 시간 계산
                            Map<String, Long> environmentTimes =
                                    EnvironmentAnalyzer.calculateEnvironmentTimes(
                                            routeCoordinates,
                                            recordedTimestamps,
                                            breakIndices,
                                            environments
                                    );

                            // 4. 환경별 체류 시간 로그
                            for (Map.Entry<String, Long> entry
                                    : environmentTimes.entrySet()) {

                                Log.d(
                                        "ENVIRONMENT_TIME",
                                        entry.getKey() + " 체류 시간: "
                                                + entry.getValue() / 1000.0
                                                + "초"
                                );
                            }

                            // 5. 계산 완료 후 결과 전달
                            callback.onComplete(environmentTimes);

                        } catch (Exception e) {
                            Log.e("OVERPASS_TEST", "환경 분석 실패", e);

                            // 분석 실패 시 기본값 전달
                            callback.onComplete(createEmptyEnvironmentTimes());
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e("OVERPASS_TEST", "환경 데이터 요청 실패", e);

                        // API 요청 실패 시 기본값 전달
                        callback.onComplete(createEmptyEnvironmentTimes());
                    }
                }
        );
    }


    private void StopRunning() {
        long elapsedTime = isPaused
                ? pauseOffset
                : SystemClock.elapsedRealtime() - chronometer.getBase();

        chronometer.stop();

        // GPS 위치 측정 종료
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);

        // 중복 저장 방지
        btnStop.setEnabled(false);
        btnPause_Resume.setEnabled(false);

        // 러닝 기록 DB 저장
        RunRecord record = saveRunRecord(elapsedTime);

        // 환경 분석이 완료된 뒤 보상 판정 진행
        searchEnvironmentForRoute(environmentTimes -> {

            // Overpass 콜백은 백그라운드 스레드일 수 있으므로
            // 화면 이동 및 UI 관련 작업은 메인 스레드에서 실행
            runOnUiThread(() -> {

                Log.d("REWARD_SERVER", "환경 분석 완료. 보상 판정 시작");

                RewardServer.requestRewards(
                        record,
                        environmentTimes,
                        rewards -> {

                            // 이미 가진 캐릭터는 제외하고 새 캐릭터만 저장
                            List<RewardServer.Reward> newRewards =
                                    saveRewards(rewards);

                            Intent intent;

                            if (newRewards.isEmpty()) {
                                intent = new Intent(
                                        RunningActivity.this,
                                        ResultActivity.class
                                );
                            } else {
                                intent = new Intent(
                                        RunningActivity.this,
                                        CharacterRewardActivity.class
                                );

                                CharacterRewardActivity.putRewards(
                                        intent,
                                        newRewards
                                );
                            }

                            // 이동한 거리 전달
                            intent.putExtra(
                                    "distance",
                                    totalDistance / 1000
                            );

                            // 걸린 시간 전달
                            intent.putExtra("elapsedTime", elapsedTime);

                            // GPS 경로 전달
                            double[] routeLatitude =
                                    new double[recordedPoints.size()];

                            double[] routeLongitude =
                                    new double[recordedPoints.size()];

                            for (int i = 0; i < recordedPoints.size(); i++) {
                                routeLatitude[i] =
                                        recordedPoints.get(i).getLatitude();

                                routeLongitude[i] =
                                        recordedPoints.get(i).getLongitude();
                            }

                            intent.putExtra("routeLatitude", routeLatitude);
                            intent.putExtra("routeLongitude", routeLongitude);

                            startActivity(intent);

                            // 현재 화면 종료
                            finish();
                        }
                );
            });
        });
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
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
    }
}

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

import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
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

    // 사전 저장 환경 데이터
    private ArrayList<EnvironmentFeature> environmentFeatures;
    // 환경별 GPS 좌표 카운트
    private int parkCount = 0;
    private int forestCount = 0;
    private int riverCount = 0;
    private int lakeCount = 0;

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
    private float maxSpeed = 0; // km/h
    private long runStartTime;  // 러닝 시작 시각 (epoch ms)

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // MapLibre 초기화
        org.maplibre.android.MapLibre.getInstance(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running);

        // 사전 저장 환경 데이터 불러오기
        environmentFeatures = EnvironmentDataLoader.load(this);

        Log.d(
                "ENV_TEST",
                "불러온 환경 데이터 개수: " + environmentFeatures.size()
        );

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
                        // 현재 GPS 좌표가 포함되는 환경 카운트
                        countEnvironmentAtLocation(
                                location.getLatitude(),
                                location.getLongitude()
                        );
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
                                           @NonNull int[] grantResults) {
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

    // GPS 좌표가 포함된 환경의 카운트를 증가시킨다.
    private void countEnvironmentAtLocation(double latitude, double longitude) {

        // 해당 GPS 좌표가 포함되는 환경 종류 확인
        java.util.Set<String> matchedCategories =
                EnvironmentMatcher.findMatchedCategories(
                        latitude,
                        longitude,
                        environmentFeatures
                );

        // 환경별 카운트 증가
        if (matchedCategories.contains("PARK")) {
            parkCount++;
        }

        if (matchedCategories.contains("FOREST")) {
            forestCount++;
        }

        if (matchedCategories.contains("RIVER")) {
            riverCount++;
        }

        if (matchedCategories.contains("LAKE")) {
            lakeCount++;
        }

        Log.d(
                "ENV_COUNT",
                "공원: " + parkCount
                        + ", 숲: " + forestCount
                        + ", 하천: " + riverCount
                        + ", 호수: " + lakeCount
        );
    }
    // 러닝 종료 시 가장 많이 기록된 환경 판정
    private String getFinalEnvironment() {

        // 모든 환경 카운트가 0이면 도심
        if (parkCount == 0
                && forestCount == 0
                && riverCount == 0
                && lakeCount == 0) {

            return "CITY";
        }

        // 가장 높은 카운트 찾기
        int maxCount = Math.max(
                Math.max(parkCount, forestCount),
                Math.max(riverCount, lakeCount)
        );

        // 가장 많이 기록된 환경 반환
        if (parkCount == maxCount) {
            return "PARK";
        }

        if (forestCount == maxCount) {
            return "FOREST";
        }

        if (riverCount == maxCount) {
            return "RIVER";
        }

        return "LAKE";
    }

    private void StopRunning() {
        long elapsedTime = isPaused ?
                pauseOffset : SystemClock.elapsedRealtime() - chronometer.getBase();

        chronometer.stop();

        // GPS 위치 측정 종료
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);

        // 중복 저장 방지
        btnStop.setEnabled(false);

        // 이번 러닝에서 가장 많이 측정된 환경 확인
        String finalEnvironment = getFinalEnvironment();

        // 러닝 기록 DB 저장
        RunRecord record = saveRunRecord(elapsedTime);

        Log.d("ENV_COUNT",
                "공원: " + parkCount +
                        ", 숲: " + forestCount +
                        ", 하천: " + riverCount +
                        ", 호수: " + lakeCount);

        Log.d("ENV_COUNT", "최종 환경: " + finalEnvironment);

        // 서버에 러닝 기록을 넘겨서 캐릭터 획득 여부를 받음
        // 획득한 캐릭터가 있으면 캐릭터 획득 화면, 없으면 바로 결과 화면으로
        RewardServer.requestRewards(record,finalEnvironment, rewards -> {

            // 이미 가진 캐릭터는 빼고 새로 획득한 캐릭터만 저장/표시
            List<RewardServer.Reward> newRewards = saveRewards(rewards);

            Intent intent;

            if (newRewards.isEmpty()) {
                intent = new Intent(RunningActivity.this, ResultActivity.class);
            } else {
                intent = new Intent(RunningActivity.this, CharacterRewardActivity.class);
                CharacterRewardActivity.putRewards(intent, newRewards);
            }

            // 이동한 거리 전달
            intent.putExtra("distance", totalDistance / 1000);

            // 걸린 시간 전달
            intent.putExtra("elapsedTime", elapsedTime);

            // 최종 환경 전달
            intent.putExtra("finalEnvironment", finalEnvironment);

            // GPS 경로 전달
            double[] routeLatitude = new double[recordedPoints.size()];
            double[] routeLongitude = new double[recordedPoints.size()];

            for (int i = 0; i < recordedPoints.size(); i++) {
                routeLatitude[i] = recordedPoints.get(i).getLatitude();
                routeLongitude[i] = recordedPoints.get(i).getLongitude();
            }

            intent.putExtra("routeLatitude", routeLatitude);
            intent.putExtra("routeLongitude", routeLongitude);

            startActivity(intent);

            // 현재 화면 종료
            finish();
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

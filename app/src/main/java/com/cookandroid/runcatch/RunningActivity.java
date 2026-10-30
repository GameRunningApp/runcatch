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
    private void StopRunning() {
        long elapsedTime = isPaused ?
                pauseOffset : SystemClock.elapsedRealtime() - chronometer.getBase();
        chronometer.stop();
        //Gps 위치 측정 종료
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        //결과 화면으로 이동
        //2km 등 조건을 달성하면 캐릭터 획득 화면, 아니면 바로 결과 화면으로
        Class<?> next = CharacterRewardActivity.isRewardEligible(totalDistance / 1000)
                ? CharacterRewardActivity.class : ResultActivity.class;
        Intent intent = new Intent(RunningActivity.this, next);

        //이동한 거리 전달
        intent.putExtra("distance", totalDistance / 1000);
        //걸린 시간 전달
        intent.putExtra("elapsedTime", elapsedTime);

        startActivity(intent);

        //현재 화면 종료
        finish();
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
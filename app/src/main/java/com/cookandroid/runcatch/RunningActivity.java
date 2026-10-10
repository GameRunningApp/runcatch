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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class RunningActivity extends AppCompatActivity {
    private Button btnPause_Resume;
    private Button btnStop;
    private boolean isPaused = false;

    //Timer
    private Chronometer chronometer;
    private long pauseOffset = 0; // 일시정지된 시점까지 누적된 시간

    //GPS
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private TextView runningDistanceText;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private Location previousLocation;
    private float totalDistance = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running);

        //뷰 설정
        btnPause_Resume = findViewById(R.id.btnPause);
        btnStop = findViewById(R.id.btnStop);
        chronometer = findViewById(R.id.chronometer);
        runningDistanceText = findViewById(R.id.tvDistanceText);

        //GPS
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 2000
        ).setMinUpdateIntervalMillis(1000).build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (previousLocation != null) {
                        float distance = previousLocation.distanceTo(location);

                        totalDistance += distance;

                        runningDistanceText.setText(
                                String.format("%.2f Km", totalDistance / 1000)
                        );
                    }

                    previousLocation = location;
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
        chronometer.start();
    }
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
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
            pauseOffset = SystemClock.elapsedRealtime() - chronometer.getBase();
            chronometer.stop();
            isPaused = true;

            //Gps 중지
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);

            btnPause_Resume.setText("계속하기");
        } else {
            chronometer.setBase(SystemClock.elapsedRealtime() - pauseOffset);
            chronometer.start();
            isPaused = false;
            previousLocation = null;

            //Gps 재개
            checkPermission_Start();

            btnPause_Resume.setText("일시정지");
        }
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
}
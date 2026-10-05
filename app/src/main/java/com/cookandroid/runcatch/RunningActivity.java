package com.cookandroid.runcatch;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import androidx.appcompat.app.AppCompatActivity;

public class RunningActivity extends AppCompatActivity {

    private Chronometer chronometer;
    private Button btnPause, btnResume, btnStop;

    private long pauseOffset = 0; // 일시정지된 시점까지 누적된 시간
    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running);

        chronometer = findViewById(R.id.chronometer);
        btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnStop = findViewById(R.id.btnStop);

        startTimer(); // 화면 들어오자마자 바로 타이머 시작

        btnPause.setOnClickListener(v -> pauseTimer());
        btnResume.setOnClickListener(v -> resumeTimer());
        btnStop.setOnClickListener(v -> resetTimer());
    }

    private void startTimer() {
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.start();
        isRunning = true;
    }

    private void pauseTimer() {
        if (!isRunning) return;
        pauseOffset = SystemClock.elapsedRealtime() - chronometer.getBase();
        chronometer.stop();
        isRunning = false;

        btnPause.setVisibility(View.GONE);
        btnResume.setVisibility(View.VISIBLE);
    }

    private void resumeTimer() {
        if (isRunning) return;
        chronometer.setBase(SystemClock.elapsedRealtime() - pauseOffset);
        chronometer.start();
        isRunning = true;

        btnResume.setVisibility(View.GONE);
        btnPause.setVisibility(View.VISIBLE);
    }

    private void resetTimer() {
        chronometer.stop();
        chronometer.setBase(SystemClock.elapsedRealtime());
        pauseOffset = 0;
        isRunning = false;

        btnResume.setVisibility(View.GONE);
        btnPause.setVisibility(View.VISIBLE);
    }
}
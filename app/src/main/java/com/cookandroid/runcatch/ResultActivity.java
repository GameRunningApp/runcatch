package com.cookandroid.runcatch;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ResultActivity extends AppCompatActivity {

    private TextView resultDistanceText;
    private TextView resultTimeText;
    private TextView resultPaceText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        resultDistanceText = findViewById(R.id.resultDistanceText);
        resultTimeText = findViewById(R.id.resultTimeText);
        resultPaceText = findViewById(R.id.resultPaceText);

        float distance = getIntent().getFloatExtra("distance", 0);
        long elapsedTime = getIntent().getLongExtra("elapsedTime", 0);
        //1km당 걸린 거리
        float pace = distance > 0 ? elapsedTime / 60000f / distance : 0;
        int paceMinutes = (int) pace;
        int paceSeconds = Math.round((pace - paceMinutes) * 60);

        long minutes = elapsedTime / 60000;
        long seconds = (elapsedTime % 60000) / 1000;

        //거리 표시
        resultTimeText.setText(String.format("%02d:%02d", minutes, seconds));
        //시간 표시
        resultDistanceText.setText(
                String.format(java.util.Locale.getDefault(),
                        "%.2f km", distance)
        );
        //페이스 표시
        resultPaceText.setText(String.format(java.util.Locale.getDefault(),
                "%d:%02d /Km", paceMinutes, paceSeconds));
    }
}
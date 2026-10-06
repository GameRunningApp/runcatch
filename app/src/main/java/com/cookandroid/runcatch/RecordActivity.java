package com.cookandroid.runcatch;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class RecordActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_record);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.record), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 홈
        Button homeNavButton = findViewById(R.id.homeNavButton);

        homeNavButton.setOnClickListener(v -> {
            Intent intent = new Intent(RecordActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        // 기록
        Button recordNavButton = findViewById(R.id.recordNavButton);

        recordNavButton.setOnClickListener(v -> {
            // 현재 이미 기록 화면이므로 아무것도 하지 않음
        });

        // 도감
        Button collectionNavButton = findViewById(R.id.collectionNavButton);

        collectionNavButton.setOnClickListener(v -> {
            Intent intent = new Intent(RecordActivity.this, CollectionActivity.class);
            startActivity(intent);
            finish();
        });

        // My
        Button myNavButton = findViewById(R.id.myNavButton);

        myNavButton.setOnClickListener(v -> {
            Intent intent = new Intent(RecordActivity.this, MyActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
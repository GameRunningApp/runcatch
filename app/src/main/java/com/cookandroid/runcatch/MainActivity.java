package com.cookandroid.runcatch;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-Edge + 레이아웃 + 인셋 처리
        setContentViewEdgeToEdge(R.layout.activity_main, R.id.main);
        // 하단 내비게이션 (현재 화면 = 도감)
        setupBottomNav(R.id.homeNavButton);
    }
}
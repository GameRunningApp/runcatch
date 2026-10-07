package com.cookandroid.runcatch;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class CollectionActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-Edge + 레이아웃 + 인셋 처리
        setContentViewEdgeToEdge(R.layout.activity_collection, R.id.collection);
        // 하단 내비게이션 (현재 화면 = 도감)
        setupBottomNav(R.id.collectionNavButton);
    }
}
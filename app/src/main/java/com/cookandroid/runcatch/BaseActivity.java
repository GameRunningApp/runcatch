package com.cookandroid.runcatch;

import android.content.Intent;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 여러 화면에서 공통으로 쓰는 기능을 모아 둔 부모 액티비티.
 * MainActivity, RecordActivity, CollectionActivity, MyActivity가 이 클래스를 상속받는다.
 */
public abstract class BaseActivity extends AppCompatActivity {

    /**
     * Edge-to-Edge 적용 + 레이아웃 설정 + 시스템 바 인셋 처리를 한 번에 수행.
     * 기존의 EdgeToEdge.enable / setContentView / setOnApplyWindowInsetsListener 세 부분을 대신한다.
     *
     * @param layoutResId 화면 레이아웃 (예: R.layout.activity_collection)
     * @param rootViewId  레이아웃 최상위 뷰의 ID (예: R.id.collection)
     */
    protected void setContentViewEdgeToEdge(@LayoutRes int layoutResId, @IdRes int rootViewId) {
        EdgeToEdge.enable(this);          // setContentView 전에 호출해야 함
        setContentView(layoutResId);

        View root = findViewById(rootViewId);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    /**
     * 하단 내비게이션 버튼 4개에 클릭 동작을 연결.
     *
     * @param currentNavButtonId 현재 화면에 해당하는 버튼 ID (눌러도 아무 동작 안 함)
     */
    protected void setupBottomNav(@IdRes int currentNavButtonId) {
        bindNavButton(R.id.homeNavButton, MainActivity.class, currentNavButtonId);
        bindNavButton(R.id.recordNavButton, RecordActivity.class, currentNavButtonId);
        bindNavButton(R.id.collectionNavButton, CollectionActivity.class, currentNavButtonId);
        bindNavButton(R.id.myNavButton, MyActivity.class, currentNavButtonId);
    }

    private void bindNavButton(@IdRes int buttonId, Class<?> targetActivity, @IdRes int currentNavButtonId) {
        View button = findViewById(buttonId);
        if (button == null) return;   // 해당 버튼이 없는 레이아웃이면 건너뜀

        // 현재 화면 버튼은 selected 상태로 표시 (selector 배경을 쓰면 강조 효과를 줄 수 있음)
        button.setSelected(buttonId == currentNavButtonId);

        button.setOnClickListener(v -> {
            if (buttonId == currentNavButtonId) return;   // 이미 이 화면이면 아무것도 안 함

            Intent intent = new Intent(this, targetActivity);
            startActivity(intent);
            finish();
        });
    }
}
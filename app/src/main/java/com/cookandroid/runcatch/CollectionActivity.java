package com.cookandroid.runcatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class CollectionActivity extends BaseActivity {

    private static final int COLUMN_COUNT = 3;
    private static final int TOTAL_SLOTS = 12;
    private static final String LOCKED_EMOJI = "🔒";

    private static final String[][] OWNED_CHARACTERS = {
            {"🐣", "알병아리"},
            {"🐥", "삐약이"},
            {"🐰", "토끼"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_collection);

        applySystemBarInsets();
        setupBottomNav(R.id.collectionNavButton);

        buildCharacterGrid();
    }

    private void applySystemBarInsets() {
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);

        View header = findViewById(R.id.headerLayout);
        View bottomNav = findViewById(R.id.bottomNavLayout);
        int headerHeight = header.getLayoutParams().height;

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.collection), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.LayoutParams params = header.getLayoutParams();
            params.height = headerHeight + bars.top;
            header.setLayoutParams(params);
            bottomNav.setPadding(0, 0, 0, bars.bottom);
            v.setPadding(bars.left, 0, bars.right, 0);
            return insets;
        });
    }

    private void buildCharacterGrid() {
        LinearLayout grid = findViewById(R.id.characterGrid);
        LayoutInflater inflater = LayoutInflater.from(this);
        int columnGap = dp(12);
        int rowGap = dp(43);

        LinearLayout row = null;
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            if (i % COLUMN_COUNT == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) rowParams.topMargin = rowGap;
                grid.addView(row, rowParams);
            } else {
                row.addView(new Space(this), new LinearLayout.LayoutParams(columnGap, 1));
            }

            View cell = inflater.inflate(R.layout.item_collection_character, row, false);
            TextView emojiText = cell.findViewById(R.id.characterEmojiText);

            if (i < OWNED_CHARACTERS.length) {
                String emoji = OWNED_CHARACTERS[i][0];
                String name = OWNED_CHARACTERS[i][1];
                emojiText.setText(emoji);
                cell.setContentDescription(name);
                cell.setOnClickListener(v -> showToast(name));
            } else {
                emojiText.setText(LOCKED_EMOJI);
                emojiText.setTextSize(44);
                cell.setContentDescription("잠긴 캐릭터");
                cell.setOnClickListener(v -> showToast("아직 획득하지 못한 캐릭터입니다"));
            }

            row.addView(cell);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

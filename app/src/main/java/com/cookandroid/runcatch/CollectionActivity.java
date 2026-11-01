package com.cookandroid.runcatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.cookandroid.runcatch.backend.RunCatchDbHelper;

public class CollectionActivity extends BaseActivity {

    private static final int COLUMN_COUNT = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_collection);

        applySystemBarInsets();
        setupBottomNav(R.id.collectionNavButton);

        RunCatchDbHelper db = RunCatchDbHelper.getInstance(this);
        String userId = RunCatchDbHelper.CURRENT_USER_ID;

        double totalDistanceKm = db.getTotalDistance(userId) / 1000.0;
        // 잠금 해제 여부는 누적 거리가 아니라 획득한 캐릭터(user_character) 기준
        Set<Integer> ownedIds = db.getOwnedCharacterIds(userId);
        List<RunCharacter> characters = RunCharacter.ALL;

        bindSummary(characters, ownedIds, totalDistanceKm);
        bindNextCharacter(characters, ownedIds, totalDistanceKm);
        bindCharacterGrid(characters, ownedIds);
    }

    private void applySystemBarInsets() {
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);

        View header = findViewById(R.id.headerLayout);
        View bottomNav = findViewById(R.id.bottomNavLayout);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.collection), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(0, bars.top, 0, 0);
            bottomNav.setPadding(0, 0, 0, bars.bottom);
            v.setPadding(bars.left, 0, bars.right, 0);
            return insets;
        });
    }

    private int countUnlocked(List<RunCharacter> characters, Set<Integer> ownedIds) {
        int count = 0;
        for (RunCharacter character : characters) {
            if (ownedIds.contains(character.id)) {
                count++;
            }
        }
        return count;
    }

    private void bindSummary(List<RunCharacter> characters, Set<Integer> ownedIds,
                             double totalDistanceKm) {
        TextView collectedCountText = findViewById(R.id.collectedCountText);
        ProgressBar collectionProgress = findViewById(R.id.collectionProgress);
        TextView totalDistanceText = findViewById(R.id.totalDistanceText);

        int unlocked = countUnlocked(characters, ownedIds);
        int total = characters.size();

        collectedCountText.setText(unlocked + " / " + total);
        collectionProgress.setProgress(total > 0 ? Math.round(unlocked * 100f / total) : 0);
        totalDistanceText.setText(String.format(Locale.US, "누적 거리 %.1f Km", totalDistanceKm));
    }

    private void bindNextCharacter(List<RunCharacter> characters, Set<Integer> ownedIds,
                                   double totalDistanceKm) {
        TextView nextCharacterEmoji = findViewById(R.id.nextCharacterEmoji);
        TextView nextCharacterName = findViewById(R.id.nextCharacterName);
        TextView nextCharacterText = findViewById(R.id.nextCharacterText);
        View nextCharacterCard = findViewById(R.id.nextCharacterCard);

        RunCharacter next = null;
        for (RunCharacter character : characters) {
            if (!ownedIds.contains(character.id)) {
                next = character;
                break;
            }
        }

        if (next == null) {
            nextCharacterCard.setVisibility(View.GONE);
            return;
        }

        nextCharacterEmoji.setText("🔒");
        nextCharacterName.setText("다음 캐릭터");
        // 획득은 서버가 판단하므로, 누적 거리가 기준을 넘었는데 아직 못 받은 경우도 있음
        double remainingKm = next.requiredKm - totalDistanceKm;
        if (remainingKm > 0) {
            nextCharacterText.setText(String.format(Locale.US, "%.1f Km 더 달리면 만날 수 있어요",
                    remainingKm));
        } else {
            nextCharacterText.setText("곧 만날 수 있어요");
        }
    }

    private void bindCharacterGrid(List<RunCharacter> characters, Set<Integer> ownedIds) {
        GridLayout characterGrid = findViewById(R.id.characterGrid);
        characterGrid.removeAllViews();
        characterGrid.setColumnCount(COLUMN_COUNT);

        LayoutInflater inflater = LayoutInflater.from(this);
        int margin = Math.round(4 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < characters.size(); i++) {
            RunCharacter character = characters.get(i);
            boolean unlocked = ownedIds.contains(character.id);

            View item = inflater.inflate(R.layout.item_collection_character, characterGrid, false);
            bindCharacterItem(item, character, unlocked);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / COLUMN_COUNT),
                    GridLayout.spec(i % COLUMN_COUNT, 1f));
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setMargins(margin, margin, margin, margin);
            characterGrid.addView(item, params);

            item.setOnClickListener(v -> showCharacterDialog(character, unlocked));
        }
    }

    private void bindCharacterItem(View item, RunCharacter character, boolean unlocked) {
        TextView characterEmojiText = item.findViewById(R.id.characterEmojiText);
        TextView characterNameText = item.findViewById(R.id.characterNameText);
        TextView characterConditionText = item.findViewById(R.id.characterConditionText);

        characterConditionText.setText(formatKm(character.requiredKm));

        if (unlocked) {
            item.setBackgroundResource(R.drawable.bg_collection_unlocked);
            characterEmojiText.setText(character.emoji);
            characterNameText.setText(character.name);
            characterNameText.setTextColor(getColor(R.color.text_primary));
        } else {
            item.setBackgroundResource(R.drawable.bg_collection_locked);
            characterEmojiText.setText("🔒");
            characterNameText.setText("???");
            characterNameText.setTextColor(getColor(R.color.text_hint));
        }
    }

    private void showCharacterDialog(RunCharacter character, boolean unlocked) {
        String condition = "획득 조건 : 누적 " + formatKm(character.requiredKm) + " 러닝";

        if (unlocked) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(character.emoji + "  " + character.name)
                    .setMessage(character.description + "\n\n" + condition)
                    .setPositiveButton("확인", null)
                    .show();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("🔒  아직 만나지 못한 캐릭터")
                    .setMessage(condition)
                    .setPositiveButton("확인", null)
                    .show();
        }
    }

    private String formatKm(double km) {
        if (km == Math.floor(km)) {
            return String.format(Locale.US, "%d Km", (long) km);
        }
        return String.format(Locale.US, "%.1f Km", km);
    }
}

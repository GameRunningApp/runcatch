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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CollectionActivity extends BaseActivity {

    private static final int COLUMN_COUNT = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_collection);

        applySystemBarInsets();
        setupBottomNav(R.id.collectionNavButton);

        double totalDistanceKm = 15.1;
        List<RunCharacter> characters = createCharacters();

        bindSummary(characters, totalDistanceKm);
        bindNextCharacter(characters, totalDistanceKm);
        bindCharacterGrid(characters, totalDistanceKm);
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

    private List<RunCharacter> createCharacters() {
        List<RunCharacter> characters = new ArrayList<>();
        characters.add(new RunCharacter("🐥", "삐약이", 2, "처음 만나는 러닝 동료"));
        characters.add(new RunCharacter("🐰", "토끼", 5, "가볍게 뛰는 걸 좋아하는 친구"));
        characters.add(new RunCharacter("🐿️", "다람쥐", 10, "작지만 꾸준한 러너"));
        characters.add(new RunCharacter("🦊", "여우", 15, "페이스 조절의 달인"));
        characters.add(new RunCharacter("🐶", "강아지", 20, "언제나 함께 달려주는 친구"));
        characters.add(new RunCharacter("🐱", "고양이", 30, "밤 러닝을 즐기는 러너"));
        characters.add(new RunCharacter("🐼", "판다", 42.195, "풀코스 거리를 달성한 러너"));
        characters.add(new RunCharacter("🐧", "펭귄", 50, "추위에도 멈추지 않는 러너"));
        characters.add(new RunCharacter("🐨", "코알라", 70, "느긋하지만 끝까지 달리는 친구"));
        characters.add(new RunCharacter("🦁", "사자", 100, "100Km를 정복한 러닝의 왕"));
        characters.add(new RunCharacter("🐯", "호랑이", 150, "누구보다 빠른 질주 본능"));
        characters.add(new RunCharacter("🦄", "유니콘", 200, "전설로 남은 러너"));
        return characters;
    }

    private int countUnlocked(List<RunCharacter> characters, double totalDistanceKm) {
        int count = 0;
        for (RunCharacter character : characters) {
            if (character.isUnlocked(totalDistanceKm)) {
                count++;
            }
        }
        return count;
    }

    private void bindSummary(List<RunCharacter> characters, double totalDistanceKm) {
        TextView collectedCountText = findViewById(R.id.collectedCountText);
        TextView collectedCountBadge = findViewById(R.id.collectedCountBadge);
        ProgressBar collectionProgress = findViewById(R.id.collectionProgress);
        TextView totalDistanceText = findViewById(R.id.totalDistanceText);

        int unlocked = countUnlocked(characters, totalDistanceKm);
        int total = characters.size();

        collectedCountText.setText(unlocked + " / " + total);
        collectedCountBadge.setText(unlocked + " / " + total);
        collectionProgress.setProgress(total > 0 ? Math.round(unlocked * 100f / total) : 0);
        totalDistanceText.setText(String.format(Locale.US, "누적 거리 %.1f Km", totalDistanceKm));
    }

    private void bindNextCharacter(List<RunCharacter> characters, double totalDistanceKm) {
        TextView nextCharacterEmoji = findViewById(R.id.nextCharacterEmoji);
        TextView nextCharacterName = findViewById(R.id.nextCharacterName);
        TextView nextCharacterText = findViewById(R.id.nextCharacterText);
        View nextCharacterCard = findViewById(R.id.nextCharacterCard);

        RunCharacter next = null;
        for (RunCharacter character : characters) {
            if (!character.isUnlocked(totalDistanceKm)) {
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
        nextCharacterText.setText(String.format(Locale.US, "%.1f Km 더 달리면 만날 수 있어요",
                next.requiredKm - totalDistanceKm));
    }

    private void bindCharacterGrid(List<RunCharacter> characters, double totalDistanceKm) {
        GridLayout characterGrid = findViewById(R.id.characterGrid);
        characterGrid.removeAllViews();
        characterGrid.setColumnCount(COLUMN_COUNT);

        LayoutInflater inflater = LayoutInflater.from(this);
        int margin = Math.round(4 * getResources().getDisplayMetrics().density);

        for (int i = 0; i < characters.size(); i++) {
            RunCharacter character = characters.get(i);
            boolean unlocked = character.isUnlocked(totalDistanceKm);

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

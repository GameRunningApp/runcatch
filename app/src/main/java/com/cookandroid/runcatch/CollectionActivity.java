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
    private static final double DISTANCE_STEP_KM = 10;

    private List<RunCharacter> characters;
    private CharacterCategory.CharacterGroup selectedGroup = null;
    private TextView[] filterChips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_collection);

        applySystemBarInsets();
        setupBottomNav(R.id.collectionNavButton);

        double totalDistanceKm = 15.1;
        characters = createCharacters();

        bindSummary();
        bindNextCharacter(totalDistanceKm);
        setupFilters();
        bindCharacterGrid();
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
        List<RunCharacter> list = new ArrayList<>();
        list.add(new RunCharacter("🦦", "수달", CharacterCategory.WATER,
                "강·하천 근처 1Km 이상 러닝", "물가를 따라 달려보세요",
                "강바람을 좋아하는 러닝 메이트", false));
        list.add(new RunCharacter("🦆", "오리", CharacterCategory.WATER,
                "호수 근처 러닝 3회", "잔잔한 호숫가를 자주 찾아보세요",
                "호숫가 산책로의 단골 손님", false));
        list.add(new RunCharacter("🐿️", "다람쥐", CharacterCategory.FOREST,
                "공원·산책로 1Km 이상 러닝", "나무가 많은 곳을 달려보세요",
                "작지만 꾸준한 러너", true));
        list.add(new RunCharacter("🦔", "고슴도치", CharacterCategory.FOREST,
                "숲길 누적 5Km 러닝", "숲길을 여러 번 달려보세요",
                "천천히 그러나 끝까지 달리는 친구", false));
        list.add(new RunCharacter("🐈", "고양이", CharacterCategory.CITY,
                "도심 거리 1Km 이상 러닝", "도시 한복판을 달려보세요",
                "밤거리 러닝을 즐기는 러너", true));
        list.add(new RunCharacter("🕊️", "비둘기", CharacterCategory.CITY,
                "번화가 러닝 3회", "사람이 많은 거리를 자주 달려보세요",
                "도시 어디서나 만나는 친구", false));
        list.add(new RunCharacter("🐐", "산양", CharacterCategory.MOUNTAIN,
                "오르막길 1Km 이상 러닝", "언덕길에 도전해보세요",
                "오르막을 두려워하지 않는 러너", false));
        list.add(new RunCharacter("🦅", "독수리", CharacterCategory.MOUNTAIN,
                "산길 누적 5Km 러닝", "높은 곳을 여러 번 달려보세요",
                "정상에서 내려다보는 걸 좋아하는 친구", false));
        list.add(new RunCharacter("🐰", "토끼", CharacterCategory.DISTANCE,
                "누적 거리 10Km 달성", "조금만 더 달려보세요",
                "가볍게 뛰는 걸 좋아하는 친구", true));
        list.add(new RunCharacter("🦊", "여우", CharacterCategory.DISTANCE,
                "누적 거리 20Km 달성", "꾸준히 거리를 쌓아보세요",
                "페이스 조절의 달인", false));
        list.add(new RunCharacter("🐻", "곰", CharacterCategory.DISTANCE,
                "누적 거리 30Km 달성", "멀리 보고 달려보세요",
                "든든한 장거리 러너", false));
        list.add(new RunCharacter("🐥", "삐약이", CharacterCategory.STREAK,
                "3일 연속 러닝", "며칠 연속으로 달려보세요",
                "처음 만나는 러닝 동료", true));
        list.add(new RunCharacter("🐶", "강아지", CharacterCategory.STREAK,
                "7일 연속 러닝", "일주일 동안 하루도 빠짐없이 달려보세요",
                "언제나 함께 달려주는 친구", false));
        list.add(new RunCharacter("🦄", "유니콘", CharacterCategory.LEAGUE,
                "월간 리그 1위", "이번 달 리그 정상에 도전해보세요",
                "리그 1위에게만 나타나는 전설의 러너", false));
        return list;
    }

    private void bindSummary() {
        TextView collectedCountText = findViewById(R.id.collectedCountText);
        TextView collectedCountBadge = findViewById(R.id.collectedCountBadge);
        ProgressBar collectionProgress = findViewById(R.id.collectionProgress);

        int unlocked = countUnlocked(null);
        int total = characters.size();

        collectedCountText.setText(unlocked + " / " + total);
        collectedCountBadge.setText(unlocked + " / " + total);
        collectionProgress.setProgress(total > 0 ? Math.round(unlocked * 100f / total) : 0);

        bindGroupCount(R.id.placeCountText, CharacterCategory.CharacterGroup.PLACE);
        bindGroupCount(R.id.recordCountText, CharacterCategory.CharacterGroup.RECORD);
        bindGroupCount(R.id.leagueCountText, CharacterCategory.CharacterGroup.LEAGUE);
    }

    private void bindGroupCount(int textId, CharacterCategory.CharacterGroup group) {
        TextView countText = findViewById(textId);
        countText.setText(countUnlocked(group) + " / " + countTotal(group));
    }

    private int countUnlocked(CharacterCategory.CharacterGroup group) {
        int count = 0;
        for (RunCharacter character : characters) {
            if (character.unlocked && (group == null || character.category.group == group)) {
                count++;
            }
        }
        return count;
    }

    private int countTotal(CharacterCategory.CharacterGroup group) {
        int count = 0;
        for (RunCharacter character : characters) {
            if (character.category.group == group) {
                count++;
            }
        }
        return count;
    }

    private void bindNextCharacter(double totalDistanceKm) {
        TextView nextCharacterEmoji = findViewById(R.id.nextCharacterEmoji);
        TextView nextCharacterName = findViewById(R.id.nextCharacterName);
        TextView nextCharacterText = findViewById(R.id.nextCharacterText);
        View nextCharacterCard = findViewById(R.id.nextCharacterCard);

        double nextGoalKm = (Math.floor(totalDistanceKm / DISTANCE_STEP_KM) + 1) * DISTANCE_STEP_KM;
        RunCharacter next = null;
        for (RunCharacter character : characters) {
            if (character.category == CharacterCategory.DISTANCE && !character.unlocked) {
                next = character;
                break;
            }
        }

        if (next == null) {
            nextCharacterCard.setVisibility(View.GONE);
            return;
        }

        nextCharacterEmoji.setText("🔒");
        nextCharacterName.setText("다음 거리 캐릭터");
        nextCharacterText.setText(String.format(Locale.US, "%.1f Km 더 달리면 만날 수 있어요",
                nextGoalKm - totalDistanceKm));
    }

    private void setupFilters() {
        filterChips = new TextView[]{
                findViewById(R.id.filterAll),
                findViewById(R.id.filterPlace),
                findViewById(R.id.filterRecord),
                findViewById(R.id.filterLeague)
        };
        CharacterCategory.CharacterGroup[] groups = {
                null,
                CharacterCategory.CharacterGroup.PLACE,
                CharacterCategory.CharacterGroup.RECORD,
                CharacterCategory.CharacterGroup.LEAGUE
        };

        for (int i = 0; i < filterChips.length; i++) {
            CharacterCategory.CharacterGroup group = groups[i];
            filterChips[i].setOnClickListener(v -> {
                selectedGroup = group;
                updateFilterSelection(v);
                bindCharacterGrid();
            });
        }
        updateFilterSelection(filterChips[0]);
    }

    private void updateFilterSelection(View selected) {
        for (TextView chip : filterChips) {
            chip.setSelected(chip == selected);
        }
    }

    private void bindCharacterGrid() {
        GridLayout characterGrid = findViewById(R.id.characterGrid);
        characterGrid.removeAllViews();
        characterGrid.setColumnCount(COLUMN_COUNT);

        LayoutInflater inflater = LayoutInflater.from(this);
        int margin = Math.round(4 * getResources().getDisplayMetrics().density);

        int index = 0;
        for (RunCharacter character : characters) {
            if (selectedGroup != null && character.category.group != selectedGroup) {
                continue;
            }

            View item = inflater.inflate(R.layout.item_collection_character, characterGrid, false);
            bindCharacterItem(item, character);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(index / COLUMN_COUNT),
                    GridLayout.spec(index % COLUMN_COUNT, 1f));
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setMargins(margin, margin, margin, margin);
            characterGrid.addView(item, params);

            item.setOnClickListener(v -> showCharacterDialog(character));
            index++;
        }
    }

    private void bindCharacterItem(View item, RunCharacter character) {
        TextView characterCategoryText = item.findViewById(R.id.characterCategoryText);
        TextView characterEmojiText = item.findViewById(R.id.characterEmojiText);
        TextView characterNameText = item.findViewById(R.id.characterNameText);
        TextView characterConditionText = item.findViewById(R.id.characterConditionText);

        characterCategoryText.setText(character.category.label);

        if (character.unlocked) {
            item.setBackgroundResource(R.drawable.bg_collection_unlocked);
            characterEmojiText.setText(character.emoji);
            characterNameText.setText(character.name);
            characterNameText.setTextColor(getColor(R.color.text_primary));
            characterConditionText.setText(character.condition);
        } else {
            item.setBackgroundResource(R.drawable.bg_collection_locked);
            characterEmojiText.setText("🔒");
            characterNameText.setText("???");
            characterNameText.setTextColor(getColor(R.color.text_hint));
            characterConditionText.setText(character.hint);
        }
    }

    private void showCharacterDialog(RunCharacter character) {
        String categoryLine = character.category.group.label + " · " + character.category.label + " 캐릭터";

        if (character.unlocked) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(character.emoji + "  " + character.name)
                    .setMessage(character.description + "\n\n" + categoryLine
                            + "\n획득 조건 : " + character.condition)
                    .setPositiveButton("확인", null)
                    .show();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("🔒  아직 만나지 못한 캐릭터")
                    .setMessage(categoryLine + "\n힌트 : " + character.hint)
                    .setPositiveButton("확인", null)
                    .show();
        }
    }
}

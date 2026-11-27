package com.cookandroid.runcatch.ui.collection;

import com.cookandroid.runcatch.R;
import com.cookandroid.runcatch.core.Session;
import com.cookandroid.runcatch.core.domain.CharacterCategory;
import com.cookandroid.runcatch.core.domain.RunCharacter;
import com.cookandroid.runcatch.core.local.RunCatchDbHelper;
import com.cookandroid.runcatch.ui.common.BaseActivity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;


import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CollectionActivity extends BaseActivity {

    private static final int COLUMN_COUNT = 3;
    private static final double DISTANCE_STEP_KM = 10;

    private List<RunCharacter> characters;
    // 획득한 캐릭터 id (DB user_character)
    private Set<Integer> ownedIds;
    private CharacterCategory.CharacterGroup selectedGroup = null;
    private TextView[] filterChips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentViewEdgeToEdgeWithHeader(R.layout.activity_collection, R.id.collection);
        setupBottomNav(R.id.collectionNavButton);

        RunCatchDbHelper db = RunCatchDbHelper.getInstance(this);
        String userId = Session.userId(this);

        double totalDistanceKm = db.getTotalDistance(userId) / 1000.0;
        characters = RunCharacter.ALL;
        ownedIds = db.getOwnedCharacterIds(userId);

        bindSummary();
        bindNextCharacter(totalDistanceKm);
        setupFilters();
        bindCharacterGrid();
    }

    private void bindSummary() {
        TextView collectedCountText = findViewById(R.id.collectedCountText);
        ProgressBar collectionProgress = findViewById(R.id.collectionProgress);

        int unlocked = countUnlocked(null);
        int total = characters.size();

        collectedCountText.setText(unlocked + " / " + total);
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
            if (isOwned(character) && (group == null || character.category.group == group)) {
                count++;
            }
        }
        return count;
    }

    private boolean isOwned(RunCharacter character) {
        return ownedIds.contains(character.id);
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
            if (character.category == CharacterCategory.DISTANCE && !isOwned(character)) {
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

        if (isOwned(character)) {
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

        if (isOwned(character)) {
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

package com.cookandroid.runcatch;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;

/**
 * 05_1 캐릭터 획득 창.
 * 러닝 측정 화면에서 "종료"를 눌렀을 때, 서버가 캐릭터를 획득했다고 알려준 경우에만 보여준다.
 * 여러 캐릭터를 획득하면 "다음"을 누를 때마다 같은 화면에서 캐릭터 정보만 바뀌고,
 * 마지막 캐릭터에서 "다음"을 누르면 상세 러닝 결과 화면으로 넘어간다.
 *
 * 흐름: RunningActivity(종료) → RewardServer(획득 여부 판단)
 *         → [획득] CharacterRewardActivity → "다음" × 획득 수 → ResultActivity(상세 결과)
 *         → [미획득] ResultActivity(상세 결과)
 */
public class CharacterRewardActivity extends BaseActivity {

    // ===== Intent로 주고받는 값 =====
    // distance, elapsedTime 은 RunningActivity → ResultActivity 가 쓰는 키와 똑같이 맞춤.
    // 이 화면은 값을 그대로 ResultActivity 로 넘겨주기만 하면 됨
    public static final String EXTRA_DISTANCE = "distance";          // float, km
    public static final String EXTRA_ELAPSED_TIME = "elapsedTime";   // long, ms

    // 획득한 캐릭터 정보. 여러 개일 수 있어서 같은 순서의 String 배열로 주고받는다 (putRewards 사용)
    public static final String EXTRA_CHARACTER_NAMES = "characterNames";
    public static final String EXTRA_CHARACTER_EMOJIS = "characterEmojis";
    public static final String EXTRA_CONDITION_TEXTS = "conditionTexts";
    public static final String EXTRA_DESCRIPTIONS = "characterDescriptions";

    private TextView tvCharacterEmoji;
    private TextView tvCharacterName;
    private TextView tvCondition;
    private TextView tvDescription;

    private String[] emojis;
    private String[] names;
    private String[] conditions;
    private String[] descriptions;
    private int currentIndex = 0;

    /** 서버에서 받은 획득 캐릭터 목록을 Intent에 담는다. RunningActivity에서 사용. */
    public static void putRewards(Intent intent, List<RewardServer.Reward> rewards) {
        int size = rewards.size();
        String[] emojis = new String[size];
        String[] names = new String[size];
        String[] conditions = new String[size];
        String[] descriptions = new String[size];

        for (int i = 0; i < size; i++) {
            RewardServer.Reward reward = rewards.get(i);
            emojis[i] = reward.character.emoji;
            names[i] = reward.character.name;
            conditions[i] = reward.conditionText;
            descriptions[i] = reward.character.description;
        }

        intent.putExtra(EXTRA_CHARACTER_EMOJIS, emojis);
        intent.putExtra(EXTRA_CHARACTER_NAMES, names);
        intent.putExtra(EXTRA_CONDITION_TEXTS, conditions);
        intent.putExtra(EXTRA_DESCRIPTIONS, descriptions);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentViewEdgeToEdge(R.layout.activity_character_reward, R.id.characterReward);

        tvCharacterEmoji = findViewById(R.id.tvCharacterEmoji);
        tvCharacterName = findViewById(R.id.tvCharacterName);
        tvCondition = findViewById(R.id.tvCondition);
        tvDescription = findViewById(R.id.tvDescription);
        Button btnNext = findViewById(R.id.btnNext);

        Intent in = getIntent();
        emojis = in.getStringArrayExtra(EXTRA_CHARACTER_EMOJIS);
        names = in.getStringArrayExtra(EXTRA_CHARACTER_NAMES);
        conditions = in.getStringArrayExtra(EXTRA_CONDITION_TEXTS);
        descriptions = in.getStringArrayExtra(EXTRA_DESCRIPTIONS);

        // 받은 캐릭터가 없으면 보여줄 게 없으므로 바로 결과 화면으로
        if (emojis == null || emojis.length == 0) {
            goToResult();
            return;
        }

        showCharacter(0);

        btnNext.setOnClickListener(v -> {
            if (currentIndex + 1 < emojis.length) {
                showCharacter(currentIndex + 1);
            } else {
                goToResult();
            }
        });
    }

    /** index번째 획득 캐릭터로 화면 내용 교체 */
    private void showCharacter(int index) {
        currentIndex = index;
        tvCharacterEmoji.setText(emojis[index]);
        tvCharacterName.setText(names[index]);
        tvCondition.setText(conditions[index]);
        tvDescription.setText(descriptions[index]);

        playPopInAnimation(tvCharacterEmoji);
    }

    /** 캐릭터가 통통 튀면서 나타나는 연출 */
    private void playPopInAnimation(TextView target) {
        target.setScaleX(0f);
        target.setScaleY(0f);
        target.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(700)
                .setStartDelay(150)
                .setInterpolator(new OvershootInterpolator(2f))
                .start();
    }

    /** 다음 → 상세 러닝 결과 화면. 받은 거리/시간은 그대로 전달한다. */
    private void goToResult() {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra(EXTRA_DISTANCE, getIntent().getFloatExtra(EXTRA_DISTANCE, 0f));
        intent.putExtra(EXTRA_ELAPSED_TIME, getIntent().getLongExtra(EXTRA_ELAPSED_TIME, 0L));
        startActivity(intent);
        finish();
    }
}

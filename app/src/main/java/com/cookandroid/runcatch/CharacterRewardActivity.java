package com.cookandroid.runcatch;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.TextView;

/**
 * 05_1 캐릭터 획득 창.
 * 러닝 측정 화면에서 "종료"를 눌렀을 때, 획득 조건을 달성한 경우에만 보여준다.
 * "다음"을 누르면 상세 러닝 결과 화면으로 넘어간다.
 *
 * 흐름: RunningActivity(종료)
 *         → [조건 달성] CharacterRewardActivity → "다음" → ResultActivity(상세 결과)
 *         → [조건 미달] ResultActivity(상세 결과)
 */
public class CharacterRewardActivity extends BaseActivity {

    // ===== 획득 조건 (아직 확정 전) ====
    public static final float REWARD_DISTANCE_KM = 2.0f;

    /** 이 거리(km)로 캐릭터를 받을 수 있는지 판단. RunningActivity에서 화면 분기할 때 사용. */
    public static boolean isRewardEligible(float distanceKm) {
        return distanceKm >= REWARD_DISTANCE_KM;
    }

    // ===== Intent로 주고받는 값 =====
    // distance, elapsedTime 은 RunningActivity → ResultActivity 가 쓰는 키와 똑같이 맞춤.
    // 이 화면은 값을 그대로 ResultActivity 로 넘겨주기만 하면 됨
    public static final String EXTRA_DISTANCE = "distance";          // float, km
    public static final String EXTRA_ELAPSED_TIME = "elapsedTime";   // long, ms

    // 획득한 캐릭터 정보 (백엔드에서 내려주면 이 키로 넘겨받는다. 없으면 아래 기본값 사용)
    public static final String EXTRA_CHARACTER_NAME = "characterName";
    public static final String EXTRA_CHARACTER_EMOJI = "characterEmoji";
    public static final String EXTRA_CONDITION_TEXT = "conditionText";
    public static final String EXTRA_DESCRIPTION = "characterDescription";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentViewEdgeToEdge(R.layout.activity_character_reward, R.id.characterReward);

        TextView tvCharacterEmoji = findViewById(R.id.tvCharacterEmoji);
        TextView tvCharacterName = findViewById(R.id.tvCharacterName);
        TextView tvCondition = findViewById(R.id.tvCondition);
        TextView tvDescription = findViewById(R.id.tvDescription);
        Button btnNext = findViewById(R.id.btnNext);

        // TODO(백엔드): 이번 러닝으로 획득한 캐릭터 정보(이름, 이미지, 조건, 설명)를 서버에서 받아서
        //               아래 EXTRA_* 키로 이 화면에 넘겨주면 된다. 지금은 기본값(삐약이)으로 표시한다.
        Intent in = getIntent();
        tvCharacterEmoji.setText(valueOrDefault(in.getStringExtra(EXTRA_CHARACTER_EMOJI), "🐥"));
        tvCharacterName.setText(valueOrDefault(in.getStringExtra(EXTRA_CHARACTER_NAME), "삐약이"));
        tvCondition.setText(valueOrDefault(in.getStringExtra(EXTRA_CONDITION_TEXT),
                "획득 조건 : 2Km 이상 러닝 완료"));
        tvDescription.setText(valueOrDefault(in.getStringExtra(EXTRA_DESCRIPTION),
                "처음 만나는 러닝 동료"));

        playPopInAnimation(tvCharacterEmoji);

        btnNext.setOnClickListener(v -> goToResult());
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

    private static String valueOrDefault(String value, String fallback) {
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}

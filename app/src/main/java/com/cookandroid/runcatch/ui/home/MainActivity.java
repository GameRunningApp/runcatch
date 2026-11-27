package com.cookandroid.runcatch.ui.home;

import com.cookandroid.runcatch.R;
import com.cookandroid.runcatch.core.Session;
import com.cookandroid.runcatch.core.domain.ExpRules;
import com.cookandroid.runcatch.core.domain.RunCharacter;
import com.cookandroid.runcatch.core.domain.RunRecord;
import com.cookandroid.runcatch.core.domain.RunStreak;
import com.cookandroid.runcatch.core.local.RunCatchDbHelper;
import com.cookandroid.runcatch.ui.common.BaseActivity;
import com.cookandroid.runcatch.format.RunFormat;
import com.cookandroid.runcatch.ui.running.RunningActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 *   주 시작 계산은 RunStreak.weekStartMillis()로, 시간 표시는 RunFormat으로 옮김
 *   다음 레벨을 5km 고정 대신 대표 캐릭터 경험치(ExpRules)로 계산
 */
public class MainActivity extends BaseActivity {

    private static final int[] RECENT_RUN_ITEM_IDS = {
            R.id.recentRun1, R.id.recentRun2, R.id.recentRun3};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 공통 화면 설정 및 시스템 바 여백 처리
        setContentViewEdgeToEdge(R.layout.activity_main, R.id.main);

        // 하단 내비게이션
        setupBottomNav(R.id.homeNavButton);

        // 러닝 시작 버튼
        Button startButton = findViewById(R.id.startButton);

        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this, RunningActivity.class);
            startActivity(intent);
        });

        // 러닝 기록 "더보기" → 기록 화면 (하단 메뉴의 기록 버튼을 누른 것과 똑같이 이동)
        findViewById(R.id.moreRecordsButton).setOnClickListener(v ->
                findViewById(R.id.recordNavButton).performClick());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 러닝을 마치고 돌아왔을 때도 최신 기록이 보이도록 onResume에서 DB 조회
        bindFromDb();
    }

    private void bindFromDb() {
        RunCatchDbHelper db = RunCatchDbHelper.getInstance(this);
        String userId = Session.userId(this);

        double totalDistanceKm = db.getTotalDistance(userId) / 1000.0;

        // 프로필 및 대표 캐릭터 정보
        int characterId = db.getCharacterId(userId);
        bindProfile(userId, RunCharacter.findById(characterId), totalDistanceKm);

        // 다음 레벨 진행도: 대표 캐릭터의 누적 경험치 (user_character.exp)
        bindNextLevel(characterId == -1 ? -1 : db.getCharacterExp(userId, characterId));

        // 최근 러닝 기록
        List<RunRecord> recentRuns =
                db.getRecentRuns(userId, RECENT_RUN_ITEM_IDS.length);
        for (int i = 0; i < RECENT_RUN_ITEM_IDS.length; i++) {
            View item = findViewById(RECENT_RUN_ITEM_IDS[i]);
            if (i < recentRuns.size()) {
                item.setVisibility(View.VISIBLE);
                bindRecentRun(item, recentRuns.get(i));
            } else {
                item.setVisibility(View.GONE);
            }
        }

        // 주간 러닝 통계
        bindWeeklySummary(
                db.getDistanceSince(userId, RunStreak.weekStartMillis(System.currentTimeMillis(), TimeZone.getDefault())) / 1000.0,
                RunStreak.countDays(db.getRunDates(userId),
                        System.currentTimeMillis(), java.util.TimeZone.getDefault()));
    }

    private void bindProfile(
            String userName,
            RunCharacter character,
            double totalDistanceKm) {

        TextView greetingText = findViewById(R.id.greetingText);
        TextView characterImageText =
                findViewById(R.id.characterImageText);
        TextView characterNameText =
                findViewById(R.id.characterNameText);
        TextView distanceText = findViewById(R.id.distanceText);

        greetingText.setText("안녕하세요, " + userName + "님!");
        if (character != null) {
            characterImageText.setText(character.emoji);
            characterNameText.setText(character.name);
        } else {
            // 아직 획득한 캐릭터가 없는 상태 (처음 시작했을 때)
            characterImageText.setText("❔");
            characterNameText.setText("아직 만난 캐릭터가 없어요");
        }
        distanceText.setText(
                String.format(Locale.US, "%.1f Km", totalDistanceKm));
    }

    // 대표 캐릭터의 레벨과 다음 레벨까지의 경험치 (ExpRules). 대표 캐릭터가 없으면 exp = -1
    private void bindNextLevel(int exp) {
        ProgressBar nextLevelProgress =
                findViewById(R.id.nextLevelProgress);
        TextView nextLevelText =
                findViewById(R.id.nextLevelText);

        if (exp < 0) {
            nextLevelProgress.setProgress(0);
            nextLevelText.setText("캐릭터를 만나면 시작");
            return;
        }

        int level = ExpRules.levelOf(exp);
        int inLevel = ExpRules.expInLevel(exp);
        int needed = ExpRules.expToNextLevel(level);

        nextLevelProgress.setProgress(inLevel * 100 / needed);
        nextLevelText.setText(
                String.format(Locale.US, "Lv.%d · %d / %d EXP", level, inLevel, needed));
    }

    private void bindRecentRun(View item, RunRecord run) {
        TextView runDistanceText =
                item.findViewById(R.id.runDistanceText);
        TextView runTimeText =
                item.findViewById(R.id.runTimeText);
        TextView runDateText =
                item.findViewById(R.id.runDateText);

        String number = String.format(Locale.US, "%.1f", run.distance / 1000f);
        SpannableString distance =
                new SpannableString(number + " Km");

        distance.setSpan(
                new RelativeSizeSpan(0.55f),
                number.length(),
                distance.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        long totalSeconds = (long) run.duration;
        String time = RunFormat.clock(totalSeconds, " : ");
        String date = new SimpleDateFormat("yy/M/d", Locale.US)
                .format(new Date(run.runDate));

        runDistanceText.setText(distance);
        runTimeText.setText(time);
        runDateText.setText(date);
    }

    private void bindWeeklySummary(
            double weeklyDistanceKm, int streakDays) {

        TextView weeklyDistanceText =
                findViewById(R.id.weeklyDistanceText);
        TextView streakText = findViewById(R.id.streakText);

        weeklyDistanceText.setText(
                String.format(Locale.US, "%.1f km", weeklyDistanceKm));
        streakText.setText("🔥 연속 러닝 " + streakDays + "일째");
    }
}

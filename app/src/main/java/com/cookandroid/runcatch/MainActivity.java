package com.cookandroid.runcatch;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.cookandroid.runcatch.backend.RunCatchDbHelper;
import com.cookandroid.runcatch.backend.RunCharacter;
import com.cookandroid.runcatch.backend.RunRecord;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends BaseActivity {

    // 레벨 1개당 기준 거리
    private static final int LEVEL_DISTANCE_KM = 5;

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 러닝을 마치고 돌아왔을 때도 최신 기록이 보이도록 onResume에서 DB 조회
        bindFromDb();
    }

    private void bindFromDb() {
        RunCatchDbHelper db = RunCatchDbHelper.getInstance(this);
        String userId = RunCatchDbHelper.CURRENT_USER_ID;

        double totalDistanceKm = db.getTotalDistance(userId) / 1000.0;

        // 프로필 및 대표 캐릭터 정보
        bindProfile(userId,
                RunCharacter.findById(db.getCharacterId(userId)),
                totalDistanceKm);

        // 다음 레벨 진행도
        bindNextLevel(totalDistanceKm);

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
                db.getDistanceSince(userId, startOfWeek()) / 1000.0,
                countStreakDays(db.getRunDates(userId)));
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

    /** 누적 거리와 다음 레벨 기준 거리 표시 (레벨마다 LEVEL_DISTANCE_KM) */
    private void bindNextLevel(double totalDistanceKm) {
        ProgressBar nextLevelProgress =
                findViewById(R.id.nextLevelProgress);
        TextView nextLevelText =
                findViewById(R.id.nextLevelText);

        int nextLevelKm =
                ((int) (totalDistanceKm / LEVEL_DISTANCE_KM) + 1) * LEVEL_DISTANCE_KM;
        double levelStartKm = nextLevelKm - LEVEL_DISTANCE_KM;

        int percent = (int) Math.round(
                (totalDistanceKm - levelStartKm) / LEVEL_DISTANCE_KM * 100);

        nextLevelProgress.setProgress(percent);
        nextLevelText.setText(
                String.format(
                        Locale.US, "%.1f / %d Km", totalDistanceKm, nextLevelKm));
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
        String time = String.format(Locale.US, "%02d : %02d : %02d",
                totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60);
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

    /** 이번 주 월요일 0시 (epoch ms) */
    private static long startOfWeek() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        return cal.getTimeInMillis();
    }

    /**
     * 연속 러닝 일수. 오늘 뛰었으면 오늘부터, 아직 안 뛰었으면 어제부터 거꾸로 센다.
     *
     * @param runDates 러닝 시작 시각 목록 (epoch ms)
     */
    private static int countStreakDays(List<Long> runDates) {
        Set<Long> runDays = new HashSet<>();
        for (long date : runDates) {
            runDays.add(startOfDay(date));
        }

        long day = startOfDay(System.currentTimeMillis());
        if (!runDays.contains(day)) {
            day = previousDay(day);
        }

        int streak = 0;
        while (runDays.contains(day)) {
            streak++;
            day = previousDay(day);
        }
        return streak;
    }

    private static long startOfDay(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private static long previousDay(long dayStartMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dayStartMillis);
        cal.add(Calendar.DAY_OF_MONTH, -1);
        return cal.getTimeInMillis();
    }
}

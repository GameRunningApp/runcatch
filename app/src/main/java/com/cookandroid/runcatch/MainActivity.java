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

import java.util.Locale;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 공통 화면 설정 및 시스템 바 여백 처리
        setContentViewEdgeToEdge(R.layout.activity_main, R.id.main);

        // 하단 내비게이션
        setupBottomNav(R.id.homeNavButton);

        // 프로필 및 캐릭터 정보
        bindProfile("○○", "🐰", "토끼", 15.1);

        // 다음 캐릭터 획득 진행도
        bindNextCharacter(7.4, 10);

        // 최근 러닝 기록
        bindRecentRun(
                R.id.recentRun1, 3.2, "00 : 24 : 18", "26/9/23");
        bindRecentRun(
                R.id.recentRun2, 4.1, "00 : 27 : 42", "26/9/22");
        bindRecentRun(
                R.id.recentRun3, 5.1, "00 : 35 : 05", "26/9/21");

        // 주간 러닝 통계
        bindWeeklySummary(12.4, 3);

        // 러닝 시작 버튼
        Button startButton = findViewById(R.id.startButton);

        startButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    MainActivity.this, RunningActivity.class);
            startActivity(intent);
        });
    }

    private void bindProfile(
            String userName,
            String characterEmoji,
            String characterName,
            double totalDistanceKm) {

        TextView greetingText = findViewById(R.id.greetingText);
        TextView characterImageText =
                findViewById(R.id.characterImageText);
        TextView characterNameText =
                findViewById(R.id.characterNameText);
        TextView distanceText = findViewById(R.id.distanceText);

        greetingText.setText("안녕하세요, " + userName + "님!");
        characterImageText.setText(characterEmoji);
        characterNameText.setText(characterName);
        distanceText.setText(
                String.format(Locale.US, "%.1f Km", totalDistanceKm));
    }

    private void bindNextCharacter(double currentKm, int goalKm) {
        ProgressBar nextCharacterProgress =
                findViewById(R.id.nextCharacterProgress);
        TextView nextCharacterText =
                findViewById(R.id.nextCharacterText);

        int percent = goalKm > 0
                ? (int) Math.min(
                100, Math.round(currentKm / goalKm * 100))
                : 0;

        nextCharacterProgress.setProgress(percent);
        nextCharacterText.setText(
                String.format(
                        Locale.US, "%.1f / %d Km", currentKm, goalKm));
    }

    private void bindRecentRun(
            int itemId,
            double distanceKm,
            String time,
            String date) {

        View item = findViewById(itemId);

        TextView runDistanceText =
                item.findViewById(R.id.runDistanceText);
        TextView runTimeText =
                item.findViewById(R.id.runTimeText);
        TextView runDateText =
                item.findViewById(R.id.runDateText);

        String number = String.format(Locale.US, "%.1f", distanceKm);
        SpannableString distance =
                new SpannableString(number + " Km");

        distance.setSpan(
                new RelativeSizeSpan(0.55f),
                number.length(),
                distance.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

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
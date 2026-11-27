package com.cookandroid.runcatch.ui.running;

import com.cookandroid.runcatch.R;
import com.cookandroid.runcatch.core.domain.ExpRules;
import com.cookandroid.runcatch.core.domain.RunCharacter;
import com.cookandroid.runcatch.run.ExpGain;
import com.cookandroid.runcatch.format.RunFormat;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;


import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.annotations.Polyline;
import org.maplibre.android.annotations.PolylineOptions;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLngBounds;

import java.util.ArrayList;
import java.util.List;

/**
 *   Intent 키는 RunExtras, 지도 주소는 RunningActivity.MAP_STYLE_URL,
 *   페이스 · 시간 표시는 RunFormat 사용
 */
public class ResultActivity extends AppCompatActivity {

    private TextView resultDistanceText;
    private TextView resultTimeText;
    private TextView resultPaceText;
    private TextView resultXpText;
    // 러닝 GPS 경로
    private List<LatLng> routePoints = new ArrayList<>();
    private MapView resultMapView;
    private MapLibreMap resultMap;
    private Polyline resultRoute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        // 러닝 화면에서 전달받은 최종 환경
        String finalEnvironment = getIntent().getStringExtra(RunExtras.FINAL_ENVIRONMENT);

        // 환경 정보 확인
        Log.d("ENV_RESULT", "최종 러닝 환경: " + finalEnvironment);

        // 계속하기 버튼
        Button continueButton = findViewById(R.id.continueButton);

        // 버튼 클릭 시 홈 화면으로 이동
        continueButton.setOnClickListener(v -> {
            finish();
        });

        resultDistanceText = findViewById(R.id.resultDistanceText);
        resultTimeText = findViewById(R.id.resultTimeText);
        resultPaceText = findViewById(R.id.resultPaceText);
        resultXpText = findViewById(R.id.resultXPText);

        // 결과 화면 지도 초기화
        resultMapView = findViewById(R.id.resultMapView);
        resultMapView.onCreate(savedInstanceState);

        // 지도 준비 완료 후 실행
        resultMapView.getMapAsync(map -> {
            resultMap = map;

            // 지도 스타일 불러오기
            map.setStyle(RunningActivity.MAP_STYLE_URL, style -> {

                // 지도 스타일 로딩 완료 후 GPS 경로 그리기
                drawRoute();

            });
        });

        float distance = getIntent().getFloatExtra(RunExtras.DISTANCE, 0);
        long elapsedTime = getIntent().getLongExtra(RunExtras.ELAPSED_TIME, 0);
        // 러닝 화면에서 전달받은 GPS 경로
        double[] routeLatitude =
                getIntent().getDoubleArrayExtra(RunExtras.ROUTE_LATITUDE);

        double[] routeLongitude =
                getIntent().getDoubleArrayExtra(RunExtras.ROUTE_LONGITUDE);

        // 위도와 경도를 LatLng으로 변환
        if (routeLatitude != null && routeLongitude != null) {

            int count = Math.min(
                    routeLatitude.length,
                    routeLongitude.length
            );

            for (int i = 0; i < count; i++) {

                LatLng position = new LatLng(
                        routeLatitude[i],
                        routeLongitude[i]
                );

                routePoints.add(position);
            }
        }
        //시간 표시 (다른 화면과 같은 00:31:20 형식)
        resultTimeText.setText(RunFormat.clock(elapsedTime / 1000));
        //거리 표시
        resultDistanceText.setText(
                String.format(java.util.Locale.getDefault(),
                        "%.2f km", distance)
        );
        //페이스 표시
        resultPaceText.setText(RunFormat.pace(distance > 0 ? elapsedTime / 1000.0 / distance : 0));
        //경험치 표시
        bindExp(ExpGain.from(getIntent()));
    }
    private void bindExp(ExpGain gain) {
        View levelBox = findViewById(R.id.resultLevelBox);
        if (gain == null) {
            resultXpText.setText("0 XP");
            levelBox.setVisibility(View.GONE);
            return;
        }
        resultXpText.setText("+" + gain.total() + " XP");

        TextView characterText = findViewById(R.id.resultLevelCharacterText);
        TextView levelUpText = findViewById(R.id.resultLevelUpText);
        ProgressBar levelProgress = findViewById(R.id.resultLevelProgress);
        TextView expDetailText = findViewById(R.id.resultExpDetailText);
        TextView levelExpText = findViewById(R.id.resultLevelExpText);

        expDetailText.setText(expDetail(gain));

        RunCharacter character = gain.hasCharacter() ? RunCharacter.findById(gain.characterId) : null;
        if (character == null) {
            // 아직 캐릭터가 없으면 경험치를 쌓을 대표 캐릭터가 없음
            characterText.setText("첫 캐릭터를 얻으면 경험치가 쌓여요");
            levelProgress.setVisibility(View.GONE);
            levelExpText.setVisibility(View.GONE);
            return;
        }

        int level = gain.levelAfter();
        int need = ExpRules.expToNextLevel(level);
        int inLevel = ExpRules.expInLevel(gain.expAfter);
        characterText.setText(character.emoji + " " + character.name + "  Lv." + level);
        levelExpText.setText(inLevel + " / " + need + " XP");

        // 진행바: 레벨이 그대로면 이전 위치에서, 레벨업이면 0에서 채워진다
        int toPercent = inLevel * 100 / need;
        int fromPercent = gain.leveledUp() ? 0
                : ExpRules.expInLevel(gain.expBefore) * 100 / need;
        levelProgress.setProgress(fromPercent);
        ObjectAnimator bar = ObjectAnimator.ofInt(levelProgress, "progress", fromPercent, toPercent);
        bar.setDuration(800);
        bar.setStartDelay(300);
        bar.setInterpolator(new DecelerateInterpolator());
        bar.start();

        if (gain.leveledUp()) {
            levelUpText.setText(gain.levelAfter() - gain.levelBefore() > 1
                    ? "LEVEL UP! ×" + (gain.levelAfter() - gain.levelBefore())
                    : "LEVEL UP!");
            levelUpText.setVisibility(View.VISIBLE);
            // 캐릭터 획득 화면과 같은 통통 튀는 연출
            levelUpText.setScaleX(0f);
            levelUpText.setScaleY(0f);
            levelUpText.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(600)
                    .setStartDelay(900)
                    .setInterpolator(new OvershootInterpolator(2f))
                    .start();
        }
    }
    private String expDetail(ExpGain gain) {
        StringBuilder sb = new StringBuilder("러닝 완료 +" + gain.runExp);
        if (gain.distanceExp > 0) sb.append(" · 거리 +").append(gain.distanceExp);
        if (gain.goalExp > 0) sb.append(" · 목표 +").append(gain.goalExp);
        if (gain.streakExp > 0) {
            sb.append(" · 연속 ").append(gain.streakDays).append("일 +").append(gain.streakExp);
        }
        return sb.toString();
    }

    //화면이 재생성되는 상황에서도 지도 상태를 저장하는 메서드
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        resultMapView.onSaveInstanceState(outState);
    }

    // GPS 경로를 지도에 그리는 메서드
    private void drawRoute() {

        // 지도 또는 GPS 경로가 준비되지 않았으면 종료
        if (resultMap == null || routePoints.size() < 2) {
            return;
        }

        // 기존 경로가 있다면 제거
        if (resultRoute != null) {
            resultMap.removePolyline(resultRoute);
        }

        // GPS 좌표들을 연결하는 선 생성
        resultRoute = resultMap.addPolyline(
                new PolylineOptions()
                        .addAll(routePoints)
                        .color(android.graphics.Color.BLUE)
                        .width(5)
        );

        // 전체 GPS 경로를 포함하는 영역 계산
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        for (LatLng point : routePoints) {
            builder.include(point);
        }

        LatLngBounds bounds = builder.build();

        // 전체 경로가 화면에 들어오도록 지도 이동 및 확대
        resultMap.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, 80)
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        resultMapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resultMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resultMapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        resultMapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        resultMapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        resultMapView.onLowMemory();
    }
}
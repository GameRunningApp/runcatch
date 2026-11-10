package com.cookandroid.runcatch;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.cookandroid.runcatch.backend.RunCatchDbHelper;
import com.cookandroid.runcatch.backend.RunCharacter;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MyActivity extends BaseActivity {

    // 이동 범위 맨 위(먼 곳)와 맨 아래(가까운 곳)에서의 캐릭터 크기 배율
    private static final float FAR_SCALE = 0.4f;
    private static final float NEAR_SCALE = 1.5f;

    private final Random random = new Random();
    private final List<WanderingCharacter> wanderingCharacters = new ArrayList<>();

    private ViewGroup characterArea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-Edge + 레이아웃 + 인셋 처리
        setContentViewEdgeToEdge(R.layout.activity_my, R.id.my);
        // 하단 내비게이션 (현재 화면 = 도감)
        setupBottomNav(R.id.myNavButton);

        characterArea = findViewById(R.id.characterArea);
        addOwnedCharacters();
    }

    /** user_character에 저장된 캐릭터 전부를 영역에 추가한다 */
    private void addOwnedCharacters() {
        Set<Integer> ownedIds = RunCatchDbHelper.getInstance(this)
                .getOwnedCharacterIds(RunCatchDbHelper.CURRENT_USER_ID);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (RunCharacter character : RunCharacter.ALL) {
            if (ownedIds.contains(character.id)) {
                TextView view = (TextView) inflater.inflate(
                        R.layout.item_wandering_character, characterArea, false);
                Motion motion = Motion.findById(character.id);
                view.setText(character.emoji);
                // TODO(임시 코드): 캐릭터 이미지가 들어가면 없앤다
                if (motion.hasFrames()) {
                    fixWidthToWidestFrame(view, motion);
                }
                characterArea.addView(view);
                wanderingCharacters.add(new WanderingCharacter(view, motion));
            }
        }
    }

    /**
     * 프레임마다 글자 길이가 달라도 뷰 너비가 변하지 않도록 가장 긴 프레임에 맞춘다.
     * TODO(임시 코드): 애니메이션을 글자로 표현해서 필요한 작업이다. 캐릭터 이미지가 들어가면 없앤다
     */
    private void fixWidthToWidestFrame(TextView view, Motion motion) {
        float widest = 0;
        for (Motion.State state : Motion.State.values()) {
            for (int i = 0; i < Motion.FRAME_COUNT; i++) {
                widest = Math.max(widest,
                        view.getPaint().measureText(motion.getFrame(state, i)));
            }
        }
        view.setWidth((int) Math.ceil(widest));
    }

    @Override
    protected void onStart() {
        super.onStart();
        for (WanderingCharacter character : wanderingCharacters) {
            character.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 화면이 안 보이는 동안에는 움직임을 멈춘다
        for (WanderingCharacter character : wanderingCharacters) {
            character.stop();
        }
    }

    /**
     * 캐릭터 한 마리의 뷰와 움직임. 캐릭터마다 따로 움직인다.
     * 시스템 애니메이션 설정이 꺼져 있어도 움직이도록 ValueAnimator 대신 매 프레임 직접 계산한다.
     */
    private class WanderingCharacter {

        private final TextView view;
        // 속도, 이동 거리, 쉬는 시간, 애니메이션 프레임은 캐릭터마다 다르다
        private final Motion motion;
        private final Runnable moveRunnable = this::moveToNearbyPosition;
        private final Runnable frameRunnable = this::updateFrame;
        private final Runnable animationRunnable = this::updateAnimation;
        private final Runnable restMotionStartRunnable = this::startRestMotion;
        private final Runnable restMotionEndRunnable = this::endRestMotion;
        private boolean placed = false;
        private Motion.State state = Motion.State.IDLE;
        private long stateStartTime;
        private long restEndTime;

        private float targetX;
        private float targetY;
        private float minY;
        private float maxY;
        private long lastFrameTime;

        WanderingCharacter(TextView view, Motion motion) {
            this.view = view;
            this.motion = motion;
        }

        void start() {
            // 영역 크기는 레이아웃이 끝나야 알 수 있으므로 post로 미룬다
            view.post(moveRunnable);
            if (motion.hasFrames()) {
                setState(Motion.State.IDLE);
                updateAnimation();
            }
        }

        void stop() {
            // 예약된 다음 이동, 다음 프레임, 다음 애니메이션, 쉬는 모션을 모두 지운다
            view.removeCallbacks(moveRunnable);
            view.removeCallbacks(frameRunnable);
            view.removeCallbacks(animationRunnable);
            view.removeCallbacks(restMotionStartRunnable);
            view.removeCallbacks(restMotionEndRunnable);
        }

        /** 동작이 바뀌면 애니메이션을 첫 프레임부터 다시 시작한다 */
        private void setState(Motion.State newState) {
            state = newState;
            stateStartTime = SystemClock.uptimeMillis();
        }

        /** 지난 시간으로 현재 프레임을 골라 표시한다. 글자가 바뀔 때만 뷰를 갱신한다 */
        private void updateAnimation() {
            long elapsed = SystemClock.uptimeMillis() - stateStartTime;
            int frameNumber = (int) (elapsed * Motion.FRAMES_PER_SECOND / 1000);
            String frame = motion.getFrame(state, frameNumber);
            if (!frame.contentEquals(view.getText())) {
                view.setText(frame);
            }
            view.postDelayed(animationRunnable, Motion.FRAME_DURATION_MS);
        }

        /** 정지 상태로 쉬기 시작한다. 프레임이 없는 캐릭터는 쉬는 시간만 기다렸다가 이동한다 */
        private void startRest() {
            setState(Motion.State.IDLE);
            int pause = randomBetween(motion.minPauseMs, motion.maxPauseMs);
            restEndTime = SystemClock.uptimeMillis() + pause;

            int restMotionDelay = randomBetween(
                    Motion.MIN_REST_MOTION_DELAY_MS, Motion.MAX_REST_MOTION_DELAY_MS);
            // 쉬는 모션을 시작할 시간이 쉬는 시간보다 길면 모션 없이 이동한다
            if (motion.hasFrames() && restMotionDelay < pause) {
                view.postDelayed(restMotionStartRunnable, restMotionDelay);
            } else {
                view.postDelayed(moveRunnable, pause);
            }
        }

        /** 무작위 쉬는 모션을 무작위 시간 동안 재생한다 */
        private void startRestMotion() {
            setState(Motion.REST_MOTIONS[random.nextInt(Motion.REST_MOTIONS.length)]);
            int duration = randomBetween(
                    Motion.MIN_REST_MOTION_DURATION_MS, Motion.MAX_REST_MOTION_DURATION_MS);
            view.postDelayed(restMotionEndRunnable, duration);
        }

        /** 정지 상태로 돌아가 남은 쉬는 시간을 기다린다. 쉬는 시간이 이미 지났으면 바로 이동한다 */
        private void endRestMotion() {
            setState(Motion.State.IDLE);
            long remaining = Math.max(0, restEndTime - SystemClock.uptimeMillis());
            view.postDelayed(moveRunnable, remaining);
        }

        /** 이동 방향으로 동작을 정한다. 좌우로 더 많이 움직이면 옆, 아래면 앞, 위면 뒤 */
        private Motion.State stateForMove(float dx, float dy) {
            if (Math.abs(dx) >= Math.abs(dy)) {
                return Motion.State.SIDE;
            }
            return dy > 0 ? Motion.State.FRONT : Motion.State.BACK;
        }

        /** 이동 범위 안에서 현재 위치 근처의 임의의 지점으로 이동을 시작한다 */
        private void moveToNearbyPosition() {
            // 1배보다 크게 그려지면 좌우로 넘치므로, 가장 클 때 넘치는 만큼 양옆을 비운다
            float minX = view.getWidth() * (NEAR_SCALE - 1f) / 2f;
            float maxX = Math.max(minX, characterArea.getWidth() - view.getWidth() - minX);
            maxY = Math.max(0, characterArea.getHeight() - view.getHeight());
            // 맨 위에서 캐릭터의 다리가 영역 절반에 닿는 위치까지만 올라간다
            float halfLineY = characterArea.getHeight() / 2f - view.getHeight();
            minY = Math.min(Math.max(0, halfLineY), maxY);

            if (!placed) {
                place(minX, maxX);
            }

            float startX = view.getTranslationX();
            float startY = view.getTranslationY();

            // 임의의 방향과 거리를 고른 뒤, 이동 범위를 벗어나지 않게 맞춘다
            // 멀리 있을수록(크기가 작을수록) 조금만 움직인다
            float density = getResources().getDisplayMetrics().density;
            double angle = random.nextDouble() * 2 * Math.PI;
            float radius = randomBetween(motion.minMoveRadiusDp, motion.maxMoveRadiusDp)
                    * density * scaleAt(startY);
            targetX = clamp(startX + (float) Math.cos(angle) * radius, minX, maxX);
            targetY = clamp(startY + (float) Math.sin(angle) * radius, minY, maxY);

            setState(stateForMove(targetX - startX, targetY - startY));
            lastFrameTime = SystemClock.uptimeMillis();
            view.postOnAnimation(frameRunnable);
        }

        /** 현재 크기에 비례한 속도로 목표를 향해 한 프레임만큼 움직인다. 도착하면 다음 이동을 예약한다 */
        private void updateFrame() {
            long now = SystemClock.uptimeMillis();
            float seconds = (now - lastFrameTime) / 1000f;
            lastFrameTime = now;

            float x = view.getTranslationX();
            float y = view.getTranslationY();
            float remaining = (float) Math.hypot(targetX - x, targetY - y);

            // 아래로 갈수록(크기가 클수록) 빠르게 움직인다
            float density = getResources().getDisplayMetrics().density;
            float step = motion.moveSpeedDpPerSec * density * scaleAt(y) * seconds;

            if (step >= remaining) {
                setPosition(targetX, targetY);
                startRest();
            } else {
                float ratio = step / remaining;
                setPosition(x + (targetX - x) * ratio, y + (targetY - y) * ratio);
                view.postOnAnimation(frameRunnable);
            }
        }

        private void setPosition(float x, float y) {
            view.setTranslationX(x);
            view.setTranslationY(y);
            applyDepth(y);
        }

        /** 처음 한 번, 캐릭터가 한곳에 겹치지 않도록 이동 범위 안의 임의의 위치에 놓는다 */
        private void place(float minX, float maxX) {
            // 발끝을 기준으로 크기가 변하도록 기준점을 아래쪽 가운데로 둔다
            view.setPivotX(view.getWidth() / 2f);
            view.setPivotY(view.getHeight());

            setPosition(randomBetween(minX, maxX), randomBetween(minY, maxY));
            view.setVisibility(View.VISIBLE);
            placed = true;
        }

        /** 위로 갈수록(y가 작을수록) 작게 만들고, 아래에 있는 캐릭터가 앞에 그려지게 한다 */
        private void applyDepth(float y) {
            float scale = scaleAt(y);
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setZ(y);
        }

        /** 위치 y에서의 크기 배율. 이동 범위 맨 위는 FAR_SCALE, 맨 아래는 NEAR_SCALE */
        private float scaleAt(float y) {
            float depth = maxY > minY ? (y - minY) / (maxY - minY) : 1f;
            return FAR_SCALE + (NEAR_SCALE - FAR_SCALE) * depth;
        }
    }

    private float randomBetween(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

package com.cookandroid.runcatch.ui.my;

import com.cookandroid.runcatch.R;
import com.cookandroid.runcatch.core.Session;
import com.cookandroid.runcatch.core.domain.RunCharacter;
import com.cookandroid.runcatch.core.local.RunCatchDbHelper;
import com.cookandroid.runcatch.ui.common.BaseActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;


import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class MyActivity extends BaseActivity {

    // 이동 범위 맨 위(먼 곳)와 맨 아래(가까운 곳)에서의 캐릭터 크기 배율
    private static final float FAR_SCALE = 0.4f;
    private static final float NEAR_SCALE = 1.5f;
    // 쓰다듬기 동작을 시트의 프레임 시간보다 몇 배 빠르게 재생할지
    private static final int PAT_SPEED = 3;
    private static final String SPRITE_DIR = "character_motion/";
    // 캐릭터가 돌아다니는 땅의 너비 (화면 너비의 몇 배)
    private static final int GROUND_WIDTH_SCREENS = 3;

    private final Random random = new Random();
    private final List<WanderingCharacter> wanderingCharacters = new ArrayList<>();

    private ViewGroup characterArea;
    // 불러온 시트. 동작이 바뀔 때마다 파일을 다시 읽지 않도록 화면에 있는 동안 들고 있는다
    private final Map<String, Bitmap> spriteSheets = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-Edge + 레이아웃 + 인셋 처리
        setContentViewEdgeToEdge(R.layout.activity_my, R.id.my);
        // 하단 내비게이션 (현재 화면 = 도감)
        setupBottomNav(R.id.myNavButton);

        characterArea = findViewById(R.id.characterArea);
        // HorizontalScrollView는 자식의 layout_width를 무시하고 내용 크기로 재므로 최소 너비로 정한다
        characterArea.setMinimumWidth(
                getResources().getDisplayMetrics().widthPixels * GROUND_WIDTH_SCREENS);
        // 처음에는 땅의 가운데를 보여 준다
        HorizontalScrollView groundScroll = findViewById(R.id.groundScroll);
        groundScroll.post(() -> groundScroll.scrollTo(
                (characterArea.getWidth() - groundScroll.getWidth()) / 2, 0));
        addOwnedCharacters();
    }

    // assets/character_motion 에서 시트를 읽는다 (fileName은 "폴더/파일"). 읽지 못하면 null
    private Bitmap loadSpriteSheet(String fileName) {
        Bitmap sheet = spriteSheets.get(fileName);
        if (sheet != null) return sheet;
        try (InputStream in = getAssets().open(SPRITE_DIR + fileName)) {
            sheet = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            Log.e("MyActivity", "시트를 읽지 못함: " + fileName, e);
            return null;
        }
        if (sheet == null) return null;
        // 칸 좌표가 픽셀 기준이라 화면 밀도에 따라 크기가 바뀌지 않게 한다
        sheet.setDensity(Bitmap.DENSITY_NONE);
        spriteSheets.put(fileName, sheet);
        return sheet;
    }

    // user_character에 저장된 캐릭터 전부를 영역에 추가한다
    private void addOwnedCharacters() {
        Set<Integer> ownedIds = RunCatchDbHelper.getInstance(this)
                .getOwnedCharacterIds(Session.userId(this));
        LayoutInflater inflater = LayoutInflater.from(this);

        for (RunCharacter character : RunCharacter.ALL) {
            if (ownedIds.contains(character.id)) {
                View view = inflater.inflate(
                        R.layout.item_wandering_character, characterArea, false);
                TextView textView = view.findViewById(R.id.characterText);
                ImageView imageView = view.findViewById(R.id.characterImage);
                Motion motion = Motion.findById(character.id);
                textView.setText(character.emoji);
                if (motion.hasSprites()) {
                    ViewGroup.LayoutParams size = imageView.getLayoutParams();
                    size.width = Math.round(size.width * motion.sizeScale);
                    size.height = Math.round(size.height * motion.sizeScale);
                    imageView.setVisibility(View.VISIBLE);
                }
                characterArea.addView(view);
                wanderingCharacters.add(new WanderingCharacter(view, textView, imageView, motion));
            }
        }
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

    // 시스템 애니메이션 설정이 꺼져 있어도 움직이도록
    private class WanderingCharacter {

        private final View view;
        private final TextView textView;
        private final ImageView imageView;
        // 속도, 이동 거리, 쉬는 시간, 애니메이션 프레임은 캐릭터마다 다르다
        private final Motion motion;
        // 이 캐릭터가 재생할 수 있는 쉬는 모션
        private final List<Motion.State> restMotions;
        private final Runnable moveRunnable = this::moveToNearbyPosition;
        private final Runnable frameRunnable = this::updateFrame;
        private final Runnable animationRunnable = this::updateAnimation;
        private final Runnable restMotionStartRunnable = this::startRestMotion;
        private final Runnable restMotionEndRunnable = this::endRestMotion;
        private final Runnable patEndRunnable = this::startRest;
        private final Matrix spriteMatrix = new Matrix();
        private final Rect spriteClip = new Rect();
        // 지금 imageView에 들어 있는 시트 파일 이름. null이면 비어 있다
        private String shownSheet = null;
        // 왼쪽으로 옆 걸음을 걸을 때 오른쪽 시트를 좌우반전한다
        private boolean facingLeft = false;
        private boolean placed = false;
        private Motion.State state = Motion.State.IDLE;
        private long stateStartTime;
        private long restEndTime;

        private float targetX;
        private float targetY;
        private float minY;
        private float maxY;
        private long lastFrameTime;

        WanderingCharacter(View view, TextView textView, ImageView imageView, Motion motion) {
            this.view = view;
            this.textView = textView;
            this.imageView = imageView;
            this.motion = motion;
            this.restMotions = motion.getRestMotions();
            if (motion.getSprite(Motion.State.PAT) != null) {
                view.setOnTouchListener(this::onTouch);
            }
        }
        private boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startPat();
                    break;
                case MotionEvent.ACTION_UP:
                    v.performClick();
                    endPatAfterLoop();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    endPatAfterLoop();
                    break;
            }
            return true;
        }
        private void startPat() {
            view.removeCallbacks(moveRunnable);
            view.removeCallbacks(frameRunnable);
            view.removeCallbacks(restMotionStartRunnable);
            view.removeCallbacks(restMotionEndRunnable);
            view.removeCallbacks(patEndRunnable);
            setState(Motion.State.PAT);
        }
        private void endPatAfterLoop() {
            if (state != Motion.State.PAT) return;
            long loopMs = motion.getSprite(Motion.State.PAT).totalDurationMs / PAT_SPEED;
            long elapsed = SystemClock.uptimeMillis() - stateStartTime;
            view.postDelayed(patEndRunnable, loopMs - elapsed % loopMs);
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
            view.removeCallbacks(patEndRunnable);
        }

        // 동작이 바뀌면 애니메이션을 첫 프레임부터 다시 시작
        private void setState(Motion.State newState) {
            state = newState;
            stateStartTime = SystemClock.uptimeMillis();
        }
        private void updateAnimation() {
            long elapsed = SystemClock.uptimeMillis() - stateStartTime;
            Motion.Sprite sprite = motion.getSprite(state);
            if (sprite != null) {
                textView.setText("");
                if (state == Motion.State.PAT) elapsed *= PAT_SPEED;
                showSpriteFrame(sprite, sprite.frameAt(elapsed),
                        state == Motion.State.SIDE && facingLeft);
            } else {
                if (shownSheet != null) {
                    imageView.setImageDrawable(null);
                    shownSheet = null;
                }
                int frameNumber = (int) (elapsed * Motion.FRAMES_PER_SECOND / 1000);
                String frame = motion.getFrame(state, frameNumber);
                if (!frame.contentEquals(textView.getText())) {
                    textView.setText(frame);
                }
            }
            // 화면 갱신마다 확인한다. 3배 빠른 쓰다듬기 프레임(약 27ms)이 24fps 간격보다 짧아서
            view.postOnAnimation(animationRunnable);
        }
        private void showSpriteFrame(Motion.Sprite sprite, int frame, boolean mirror) {
            float viewWidth = imageView.getWidth();
            float viewHeight = imageView.getHeight();
            if (viewWidth == 0 || viewHeight == 0) return; // 레이아웃 전
            if (!sprite.fileName.equals(shownSheet)) {
                Bitmap sheet = loadSpriteSheet(sprite.fileName);
                if (sheet == null) return;
                imageView.setImageBitmap(sheet);
                shownSheet = sprite.fileName;
            }
            float scale = Math.min(viewWidth / sprite.frameWidth, viewHeight / sprite.frameHeight);
            float drawnWidth = sprite.frameWidth * scale;
            float drawnHeight = sprite.frameHeight * scale;
            float left = (viewWidth - drawnWidth) / 2f;
            float top = viewHeight - drawnHeight;

            spriteMatrix.setScale(scale, scale);
            spriteMatrix.postTranslate(left - sprite.frameLeft(frame) * scale,
                    top - sprite.frameTop(frame) * scale);
            if (mirror) {
                spriteMatrix.postScale(-1f, 1f, viewWidth / 2f, 0f);
            }
            imageView.setImageMatrix(spriteMatrix);
            // 칸이 뷰보다 좁으면 옆 칸이 보이므로 그린 칸 밖은 잘라 낸다
            spriteClip.set((int) left, (int) top,
                    (int) Math.ceil(left + drawnWidth), (int) viewHeight);
            imageView.setClipBounds(spriteClip);
        }
        private void startRest() {
            setState(Motion.State.IDLE);
            int pause = randomBetween(motion.minPauseMs, motion.maxPauseMs);
            restEndTime = SystemClock.uptimeMillis() + pause;

            int restMotionDelay = randomBetween(
                    Motion.MIN_REST_MOTION_DELAY_MS, Motion.MAX_REST_MOTION_DELAY_MS);
            // 쉬는 모션을 시작할 시간이 쉬는 시간보다 길면 모션 없이 이동한다
            if (!restMotions.isEmpty() && restMotionDelay < pause) {
                view.postDelayed(restMotionStartRunnable, restMotionDelay);
            } else {
                view.postDelayed(moveRunnable, pause);
            }
        }

        // 무작위 쉬는 모션을 무작위 시간 동안 재생
        private void startRestMotion() {
            setState(restMotions.get(random.nextInt(restMotions.size())));
            int duration = randomBetween(
                    Motion.MIN_REST_MOTION_DURATION_MS, Motion.MAX_REST_MOTION_DURATION_MS);
            view.postDelayed(restMotionEndRunnable, duration);
        }

        // 정지 상태로 돌아가 남은 쉬는 시간을 기다린다. 쉬는 시간이 이미 지났으면 바로 이동
        private void endRestMotion() {
            setState(Motion.State.IDLE);
            long remaining = Math.max(0, restEndTime - SystemClock.uptimeMillis());
            view.postDelayed(moveRunnable, remaining);
        }

        // 이동 방향으로 동작을 정한다. 좌우로 더 많이 움직이면 옆, 아래면 앞, 위면 뒤
        private Motion.State stateForMove(float dx, float dy) {
            if (Math.abs(dx) >= Math.abs(dy)) {
                return Motion.State.SIDE;
            }
            return dy > 0 ? Motion.State.FRONT : Motion.State.BACK;
        }

        // 이동 범위 안에서 현재 위치 근처의 임의의 지점으로 이동을 시작
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

            facingLeft = targetX < startX;
            setState(stateForMove(targetX - startX, targetY - startY));
            lastFrameTime = SystemClock.uptimeMillis();
            view.postOnAnimation(frameRunnable);
        }
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

        // 처음 한 번, 캐릭터가 한곳에 겹치지 않도록 이동 범위 안의 임의의 위치에 놓는다
        private void place(float minX, float maxX) {
            // 발끝을 기준으로 크기가 변하도록 기준점을 아래쪽 가운데로 둔다
            view.setPivotX(view.getWidth() / 2f);
            view.setPivotY(view.getHeight());

            setPosition(randomBetween(minX, maxX), randomBetween(minY, maxY));
            view.setVisibility(View.VISIBLE);
            placed = true;
        }

        // 위로 갈수록(y가 작을수록) 작게 만들고, 아래에 있는 캐릭터가 앞에
        private void applyDepth(float y) {
            float scale = scaleAt(y);
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setZ(y);
        }
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

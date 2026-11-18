package com.cookandroid.runcatch;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Random;

/**
 * 커뮤니티 화면의 반응 · 응원 애니메이션.
 *
 * 이모지는 화면 맨 위(윈도우의 DecorView)에 잠깐 띄웠다가 지워서,
 * 카드나 스크롤 영역 밖으로 날아가도 잘리지 않게 한다.
 * 화면만 바뀌는 효과이고 데이터 저장과는 관계없다.
 */
public final class CommunityEffects {

    /** 응원하기 폭죽에 섞어서 쓰는 이모지 */
    private static final String[] CHEER_EMOJIS = {"🎉", "👏", "💙", "⭐", "🔥", "💪"};
    private static final int CHEER_PARTICLE_COUNT = 10;

    private static final Random RANDOM = new Random();

    private CommunityEffects() {
    }

    // ===== 반응 칩 (👏 🔥 💪) =====

    /** 반응 누름: 칩이 통통 튀고, 칩 위로 같은 이모지 3개가 떠오르며 사라진다 */
    public static void reactionPop(Activity activity, View chip, String emoji) {
        chip.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        bounce(chip, 1.25f);
        for (int i = 0; i < 3; i++) {
            int sizeSp = (i == 0) ? 28 : 18;
            floatUp(activity, chip, emoji, sizeSp, i * 90L);
        }
    }

    /** 반응 취소: 칩이 살짝 작아졌다 돌아온다 */
    public static void reactionCancel(View chip) {
        bounce(chip, 0.88f);
    }

    // ===== 응원하기 =====

    /**
     * 응원하기: 버튼이 눌렸다가 튀어나오고, 버튼에서 이모지 폭죽이 위쪽으로 터진다.
     * 응원을 받은 러너의 캐릭터(avatar)는 한 번 점프하고 머리 위로 💙가 떠오른다.
     */
    public static void cheerBurst(Activity activity, View button, View avatar) {
        button.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        // 버튼: 눌린 것처럼 작아졌다가 튕기듯 원래 크기로
        button.animate().cancel();
        button.setScaleX(0.85f);
        button.setScaleY(0.85f);
        button.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(380)
                .setInterpolator(new OvershootInterpolator(4f))
                .start();

        // 이모지 폭죽: 위쪽 부채꼴(±75도) 방향으로 퍼지며 회전하고 사라짐
        for (int i = 0; i < CHEER_PARTICLE_COUNT; i++) {
            String emoji = CHEER_EMOJIS[RANDOM.nextInt(CHEER_EMOJIS.length)];
            TextView p = addParticle(activity, button, emoji, 14 + RANDOM.nextInt(10));

            double angle = Math.toRadians(-90 + (RANDOM.nextDouble() * 2 - 1) * 75);
            float distance = dp(activity, 70 + RANDOM.nextInt(80));
            float dx = (float) (Math.cos(angle) * distance);
            float dy = (float) (Math.sin(angle) * distance);

            p.setScaleX(0.4f);
            p.setScaleY(0.4f);
            AnimatorSet move = new AnimatorSet();
            move.playTogether(
                    ObjectAnimator.ofFloat(p, View.TRANSLATION_X, p.getTranslationX(), p.getTranslationX() + dx),
                    ObjectAnimator.ofFloat(p, View.TRANSLATION_Y, p.getTranslationY(), p.getTranslationY() + dy),
                    ObjectAnimator.ofFloat(p, View.ROTATION, 0f, RANDOM.nextInt(121) - 60),
                    ObjectAnimator.ofFloat(p, View.SCALE_X, 0.4f, 1.1f),
                    ObjectAnimator.ofFloat(p, View.SCALE_Y, 0.4f, 1.1f));
            move.setDuration(700);
            move.setInterpolator(new DecelerateInterpolator(1.6f));

            ObjectAnimator fade = ObjectAnimator.ofFloat(p, View.ALPHA, 1f, 0f);
            fade.setStartDelay(380);
            fade.setDuration(320);
            fade.setInterpolator(new AccelerateInterpolator());

            AnimatorSet all = new AnimatorSet();
            all.playTogether(move, fade);
            all.setStartDelay(RANDOM.nextInt(60));
            removeWhenDone(all, p);
            all.start();
        }

        // 응원 받은 러너 캐릭터가 한 번 점프 + 💙
        if (avatar != null) {
            ObjectAnimator jump = ObjectAnimator.ofFloat(avatar, View.TRANSLATION_Y,
                    0f, -dp(activity, 12), 0f);
            jump.setDuration(450);
            jump.setInterpolator(new DecelerateInterpolator());
            jump.setStartDelay(120);
            jump.start();
            floatUp(activity, avatar, "💙", 22, 180L);
        }
    }

    // ===== 공통 =====

    /** 이모지 하나가 anchor 가운데에서 위로 떠오르며 커졌다가 사라진다 */
    private static void floatUp(Activity activity, View anchor, String emoji, int sizeSp, long delay) {
        TextView p = addParticle(activity, anchor, emoji, sizeSp);
        float drift = dp(activity, RANDOM.nextInt(41) - 20);   // 좌우로 -20 ~ 20dp 흔들림
        float rise = dp(activity, 90 + RANDOM.nextInt(40));

        p.setAlpha(0f);
        AnimatorSet move = new AnimatorSet();
        move.playTogether(
                ObjectAnimator.ofFloat(p, View.TRANSLATION_Y, p.getTranslationY(), p.getTranslationY() - rise),
                ObjectAnimator.ofFloat(p, View.TRANSLATION_X, p.getTranslationX(), p.getTranslationX() + drift),
                ObjectAnimator.ofFloat(p, View.SCALE_X, 0.6f, 1.3f),
                ObjectAnimator.ofFloat(p, View.SCALE_Y, 0.6f, 1.3f));
        move.setDuration(850);
        move.setInterpolator(new DecelerateInterpolator());

        ObjectAnimator appear = ObjectAnimator.ofFloat(p, View.ALPHA, 0f, 1f);
        appear.setDuration(120);
        ObjectAnimator fade = ObjectAnimator.ofFloat(p, View.ALPHA, 1f, 0f);
        fade.setStartDelay(450);
        fade.setDuration(400);

        AnimatorSet all = new AnimatorSet();
        all.playTogether(move, appear, fade);
        all.setStartDelay(delay);
        removeWhenDone(all, p);
        all.start();
    }

    /** 칩 · 버튼을 peak 배율까지 키웠다가(또는 줄였다가) 원래 크기로 */
    private static void bounce(View v, float peak) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, peak, 1f),
                ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, peak, 1f));
        set.setDuration(300);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    /** anchor 가운데 위치에 이모지 TextView를 화면 맨 위 레이어로 추가 */
    private static TextView addParticle(Activity activity, View anchor, String emoji, int sizeSp) {
        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();

        TextView p = new TextView(activity);
        p.setText(emoji);
        p.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        p.setIncludeFontPadding(false);
        p.setClickable(false);
        p.setElevation(dp(activity, 16));
        p.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        p.measure(unspecified, unspecified);

        int[] anchorPos = new int[2];
        int[] rootPos = new int[2];
        anchor.getLocationInWindow(anchorPos);
        root.getLocationInWindow(rootPos);
        float centerX = anchorPos[0] - rootPos[0] + anchor.getWidth() / 2f;
        float centerY = anchorPos[1] - rootPos[1] + anchor.getHeight() / 2f;

        root.addView(p, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        p.setTranslationX(centerX - p.getMeasuredWidth() / 2f);
        p.setTranslationY(centerY - p.getMeasuredHeight() / 2f);
        return p;
    }

    private static void removeWhenDone(Animator animator, View particle) {
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ViewGroup parent = (ViewGroup) particle.getParent();
                if (parent != null) parent.removeView(particle);
            }
        });
    }

    private static float dp(Activity activity, float value) {
        return value * activity.getResources().getDisplayMetrics().density;
    }
}

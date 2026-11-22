package com.cookandroid.runcatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.emoji2.emojipicker.EmojiPickerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 커뮤니티 화면 (화면 틀 단계).
 * 같은 레벨 그룹 러너들의 피드 · 월간 리그 · 레이드를 탭으로 나눠 보여준다.
 *
 * 반응 · 응원 버튼 애니메이션은 CommunityEffects에 있다.
 *
 * TODO(서버): 지금은 CommunityDummyData의 임시 데이터로 그린다.
 *             반응 · 응원도 화면에서만 바뀌고 저장되지 않는다.
 */
public class CommunityActivity extends BaseActivity {

    private static final String[] MEDALS = {"🥇", "🥈", "🥉"};

    private TextView[] tabs;
    private View[] sections;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentViewEdgeToEdge(R.layout.activity_community, R.id.community);

        // 하단 내비게이션
        setupBottomNav(R.id.communityNavButton);

        findViewById(R.id.communityAlarmButton).setOnClickListener(v ->
                Toast.makeText(this, "알림 기능은 준비 중입니다", Toast.LENGTH_SHORT).show());

        bindGroup();
        setupTabs();
        bindFeed();
        bindLeague();
        bindRaid();
    }

    // ===== 그룹 카드 =====

    private void bindGroup() {
        CommunityDummyData.Group group = CommunityDummyData.GROUP;
        ((TextView) findViewById(R.id.groupAvatarText)).setText(group.emoji);
        ((TextView) findViewById(R.id.groupNameText)).setText(group.name);
        ((TextView) findViewById(R.id.groupInfoText)).setText(String.format(Locale.US,
                "페이스 %s~%s · %d명",
                CommunityDummyData.formatPace(group.paceMinSec).replace("/km", ""),
                CommunityDummyData.formatPace(group.paceMaxSec),
                group.memberCount));

        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;
        ((TextView) findViewById(R.id.seasonText)).setText(month + "월 시즌");
    }

    // ===== 탭 =====

    private void setupTabs() {
        tabs = new TextView[]{
                findViewById(R.id.tabFeed),
                findViewById(R.id.tabLeague),
                findViewById(R.id.tabRaid)
        };
        sections = new View[]{
                findViewById(R.id.feedSection),
                findViewById(R.id.leagueSection),
                findViewById(R.id.raidSection)
        };
        for (int i = 0; i < tabs.length; i++) {
            int index = i;
            tabs[i].setOnClickListener(v -> selectTab(index));
        }
        selectTab(0);
    }

    private void selectTab(int index) {
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setSelected(i == index);
            sections[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
    }

    // ===== 피드 =====

    private void bindFeed() {
        LinearLayout feedList = findViewById(R.id.feedList);
        feedList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (CommunityDummyData.FeedPost post : CommunityDummyData.feed()) {
            View item = inflater.inflate(R.layout.item_feed_post, feedList, false);

            ((TextView) item.findViewById(R.id.feedAvatarText)).setText(post.emoji);
            ((TextView) item.findViewById(R.id.feedNameText)).setText(post.nickname);
            ((TextView) item.findViewById(R.id.feedLevelText)).setText("LV." + post.level);
            ((TextView) item.findViewById(R.id.feedTimeText)).setText(post.timeAgo);
            ((TextView) item.findViewById(R.id.feedDistanceText))
                    .setText(String.format(Locale.US, "%.1f km", post.distanceKm));
            ((TextView) item.findViewById(R.id.feedDurationText))
                    .setText(CommunityDummyData.formatDuration(post.durationSec));
            ((TextView) item.findViewById(R.id.feedPaceText))
                    .setText(CommunityDummyData.formatPace(post.paceSec()));

            TextView clap = item.findViewById(R.id.feedClapChip);
            TextView fire = item.findViewById(R.id.feedFireChip);
            TextView muscle = item.findViewById(R.id.feedMuscleChip);
            bindReaction(clap, "👏", post.clapCount);
            bindReaction(fire, "🔥", post.fireCount);
            bindReaction(muscle, "💪", post.muscleCount);

            // "+" : 휴대폰 전체 이모지에서 골라 반응 보내기
            HorizontalScrollView reactionScroll = item.findViewById(R.id.feedReactionScroll);
            LinearLayout reactionRow = item.findViewById(R.id.feedReactionRow);
            View addChip = item.findViewById(R.id.feedAddReactionChip);
            addChip.setOnClickListener(v -> showEmojiPicker(emoji ->
                    sendCustomReaction(reactionScroll, reactionRow, addChip, emoji)));

            TextView cheer = item.findViewById(R.id.feedCheerButton);
            View avatar = item.findViewById(R.id.feedAvatarText);
            cheer.setOnClickListener(v -> {
                // 응원 애니메이션: 버튼 튕김 + 이모지 폭죽 + 받은 러너 캐릭터 점프
                CommunityEffects.cheerBurst(this, cheer, avatar);
                cheer.setText("응원 완료");
                cheer.setEnabled(false);
                cheer.setAlpha(0.6f);
                Toast.makeText(this, post.nickname + "님에게 응원을 보냈어요",
                        Toast.LENGTH_SHORT).show();
            });

            feedList.addView(item);
        }
    }

    /**
     * 반응 칩: 누르면 내 반응이 +1 되고 하늘색, 다시 누르면 취소.
     * 누를 때 해당 이모지가 칩 위로 떠오르는 애니메이션이 나온다.
     */
    private void bindReaction(TextView chip, String emoji, int baseCount) {
        bindReaction(chip, emoji, baseCount, false);
    }

    /** removable : "+"로 새로 만든 칩이면 true → 취소해서 0개가 되면 칩을 없앰 */
    private void bindReaction(TextView chip, String emoji, int baseCount, boolean removable) {
        chip.setTag(emoji);   // 같은 이모지 칩을 찾을 때 사용
        chip.setText(emoji + " " + baseCount);
        chip.setOnClickListener(v -> {
            boolean mine = !chip.isSelected();
            chip.setSelected(mine);
            chip.setText(emoji + " " + (baseCount + (mine ? 1 : 0)));
            if (mine) {
                CommunityEffects.reactionPop(this, chip, emoji);
            } else {
                CommunityEffects.reactionCancel(chip);
                // "+"로 내가 추가한 이모지라서 0개가 되면 칩을 없앰
                if (removable && baseCount == 0) {
                    chip.setOnClickListener(null);
                    chip.postDelayed(() -> {
                        ViewGroup parent = (ViewGroup) chip.getParent();
                        if (parent != null) parent.removeView(chip);
                    }, 250);
                }
            }
        });
    }

    // ===== 이모지 선택창 ("+" 칩) =====

    private interface OnEmojiChosen {
        void onChosen(String emoji);
    }

    /** 아래에서 올라오는 창에 휴대폰 전체 이모지(EmojiPickerView)를 띄우고, 고르면 닫음 */
    private void showEmojiPicker(OnEmojiChosen listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_emoji_picker, null, false);
        EmojiPickerView picker = sheet.findViewById(R.id.emojiPicker);
        picker.setOnEmojiPickedListener(item -> {
            dialog.dismiss();
            listener.onChosen(item.getEmoji());
        });
        dialog.setContentView(sheet);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        dialog.show();
    }

    /**
     * 고른 이모지로 반응 보내기.
     * - 카드에 이미 있는 이모지 : 그 칩을 누른 것과 같음 (이미 눌렀으면 애니메이션만 다시)
     * - 처음 보는 이모지 : "+" 앞에 새 칩을 만들고 1로 시작
     * TODO(서버): 반응 저장 API가 생기면 여기서 이모지 문자열을 그대로 보내면 됨 (MySQL은 utf8mb4 필요)
     */
    private void sendCustomReaction(HorizontalScrollView scroll, LinearLayout row,
                                    View addChip, String emoji) {
        TextView chip = row.findViewWithTag(emoji);
        if (chip == null) {
            chip = new TextView(this, null, 0, R.style.ReactionChip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
            lp.setMarginStart(dp(6));
            row.addView(chip, row.indexOfChild(addChip), lp);
            bindReaction(chip, emoji, 0, true);
        }
        TextView target = chip;
        // 새 칩이 화면에 자리를 잡은 뒤 눌러야 애니메이션 위치가 맞음
        row.post(() -> {
            scroll.scrollTo(Math.max(0, target.getRight() - scroll.getWidth() + dp(40)), 0);
            if (target.isSelected()) {
                CommunityEffects.reactionPop(this, target, emoji);
            } else {
                target.performClick();
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ===== 월간 리그 =====

    private void bindLeague() {
        Calendar now = Calendar.getInstance();
        int month = now.get(Calendar.MONTH) + 1;
        int lastDay = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        int dDay = lastDay - now.get(Calendar.DAY_OF_MONTH);

        ((TextView) findViewById(R.id.leagueTitleText)).setText(month + "월 리그 · 한 달 시즌");
        ((TextView) findViewById(R.id.leagueDeadlineText))
                .setText("D-" + dDay + " · " + month + "월 " + lastDay + "일 마감");

        List<CommunityDummyData.LeagueRank> ranks = CommunityDummyData.league();
        LinearLayout leagueList = findViewById(R.id.leagueList);
        leagueList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < ranks.size(); i++) {
            CommunityDummyData.LeagueRank rank = ranks.get(i);
            View row = inflater.inflate(R.layout.item_league_rank, leagueList, false);

            TextView number = row.findViewById(R.id.rankNumberText);
            if (i < MEDALS.length) {
                number.setText(MEDALS[i]);
            } else {
                number.setText(String.valueOf(i + 1));
            }
            ((TextView) row.findViewById(R.id.rankAvatarText)).setText(rank.emoji);
            ((TextView) row.findViewById(R.id.rankNameText)).setText(rank.nickname);
            ((TextView) row.findViewById(R.id.rankLevelText)).setText("LV." + rank.level);
            ((TextView) row.findViewById(R.id.rankDistanceText))
                    .setText(String.format(Locale.US, "%.1f km", rank.distanceKm));

            if (rank.isMe) {
                row.setBackgroundResource(R.drawable.bg_rank_me);
                TextView streak = row.findViewById(R.id.rankStreakText);
                if (rank.streakDays > 0) {
                    streak.setText("🔥" + rank.streakDays + "일");
                    streak.setVisibility(View.VISIBLE);
                }
                ((TextView) findViewById(R.id.myRankText)).setText(
                        String.format(Locale.US, "%d위 / %d명", i + 1,
                                CommunityDummyData.GROUP.memberCount));
                ((TextView) findViewById(R.id.myLeagueDistanceText))
                        .setText(String.format(Locale.US, "%.1f km", rank.distanceKm));
            }
            leagueList.addView(row);
        }
    }

    // ===== 레이드 =====

    private void bindRaid() {
        CommunityDummyData.Raid raid = CommunityDummyData.RAID;
        ((TextView) findViewById(R.id.raidProgressText)).setText(String.format(Locale.US,
                "%,.0f / %,.0f km", raid.currentKm, raid.goalKm));
        ((ProgressBar) findViewById(R.id.raidProgress))
                .setProgress(Math.round(raid.currentKm * 100 / raid.goalKm));
        ((TextView) findViewById(R.id.raidMyDistanceText))
                .setText(String.format(Locale.US, "%.1f km", raid.myKm));
        ((TextView) findViewById(R.id.raidRemainText)).setText(String.format(Locale.US,
                "%,.0f km", Math.max(raid.goalKm - raid.currentKm, 0)));

        Calendar now = Calendar.getInstance();
        int dDay = now.getActualMaximum(Calendar.DAY_OF_MONTH) - now.get(Calendar.DAY_OF_MONTH);
        ((TextView) findViewById(R.id.raidDdayText)).setText("D-" + dDay);
    }
}

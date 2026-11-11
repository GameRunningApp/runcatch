package com.cookandroid.runcatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 커뮤니티 화면 (화면 틀 단계).
 * 같은 레벨 그룹 러너들의 피드 · 월간 리그 · 레이드를 탭으로 나눠 보여준다.
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

            TextView cheer = item.findViewById(R.id.feedCheerButton);
            cheer.setOnClickListener(v -> {
                cheer.setText("응원 완료");
                cheer.setEnabled(false);
                cheer.setAlpha(0.6f);
                Toast.makeText(this, post.nickname + "님에게 응원을 보냈어요",
                        Toast.LENGTH_SHORT).show();
            });

            feedList.addView(item);
        }
    }

    /** 반응 칩: 누르면 내 반응이 +1 되고 하늘색, 다시 누르면 취소 */
    private void bindReaction(TextView chip, String emoji, int baseCount) {
        chip.setText(emoji + " " + baseCount);
        chip.setOnClickListener(v -> {
            boolean mine = !chip.isSelected();
            chip.setSelected(mine);
            chip.setText(emoji + " " + (baseCount + (mine ? 1 : 0)));
        });
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

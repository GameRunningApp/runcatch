
package com.cookandroid.runcatch.ui.community;

import com.cookandroid.runcatch.R;
import com.cookandroid.runcatch.core.Session;
import com.cookandroid.runcatch.remote.RunCatchApi;
import com.cookandroid.runcatch.ui.common.BaseActivity;
import com.cookandroid.runcatch.format.RunFormat;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.emoji2.emojipicker.EmojiPickerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONObject;
import org.json.JSONArray;


import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *   HTTP 연결 코드를 RunCatchApi로 옮김. 페이스 · 시간 표시는 RunFormat
 *   테스트 계정 고정을 Session.userId()로, 반응 · 응원을 서버 저장으로 바꿈
 *   CommunityDummyData는 삭제
 */
public class CommunityActivity extends BaseActivity {

    private static final String[] MEDALS = {"🥇", "🥈", "🥉"};

    // 로그인한 유저 (서버 API의 user_id)
    private String userId;

    private TextView[] tabs;
    private View[] sections;

    private TextView groupAvatarText;
    private TextView groupNameText;
    private TextView groupInfoText;
    private TextView groupJoinButton;

    // 그룹 가입 여부에 따른 화면 전환
    private View communityScroll;
    private LinearLayout groupJoinScreen;
    private TextView groupJoinActionButton;

    private final ExecutorService networkExecutor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentViewEdgeToEdge(R.layout.activity_community, R.id.community);
        userId = Session.userId(this);

        setupBottomNav(R.id.communityNavButton);

        findViewById(R.id.communityAlarmButton).setOnClickListener(v ->
                Toast.makeText(this, "알림 기능은 준비 중입니다",
                        Toast.LENGTH_SHORT).show());

        bindGroup();
        setupTabs();

        // 서버에서 실제 그룹 정보 조회
        requestGroupInfo();
    }

    // ===== 그룹 카드 =====

    private void bindGroup() {
        groupAvatarText = findViewById(R.id.groupAvatarText);
        groupNameText = findViewById(R.id.groupNameText);
        groupInfoText = findViewById(R.id.groupInfoText);
        groupJoinButton = findViewById(R.id.groupJoinButton);

        // 기존 커뮤니티 화면과 신규 참여 화면
        communityScroll = findViewById(R.id.communityScroll);
        groupJoinScreen = findViewById(R.id.groupJoinScreen);
        groupJoinActionButton = findViewById(R.id.groupJoinActionButton);

        // 서버 응답 전에는 두 화면 모두 숨김
        communityScroll.setVisibility(View.GONE);
        groupJoinScreen.setVisibility(View.GONE);

        // 기존 그룹 카드 내부의 참여 버튼은 사용하지 않음
        groupJoinButton.setVisibility(View.GONE);

        int month = Calendar.getInstance().get(Calendar.MONTH) + 1;

        ((TextView) findViewById(R.id.seasonText))
                .setText(month + "월 시즌");

        // 신규 그룹 참여 화면의 버튼
        groupJoinActionButton.setOnClickListener(v -> requestGroupJoin());
    }

    // 서버에서 사용자 그룹 정보 조회
    private void requestGroupInfo() {
        networkExecutor.execute(() -> {
            try {
                JSONObject json = RunCatchApi.get("/community-groups/" + RunCatchApi.encode(userId));

                boolean isJoined = json.optBoolean("is_joined", false);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (isJoined) {
                        showJoinedGroup(json);
                    } else {
                        showNotJoinedGroup();
                    }
                });

            } catch (Exception e) {
                Log.e("CommunityActivity", "그룹 정보 조회 실패", e);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(this,
                            "그룹 정보를 불러오지 못했습니다: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });

            }
        });
    }

    // 가입된 그룹 정보 표시
    private void showJoinedGroup(JSONObject json) {

        // 기존 커뮤니티 화면 표시
        communityScroll.setVisibility(View.VISIBLE);
        groupJoinScreen.setVisibility(View.GONE);

        groupAvatarText.setVisibility(View.VISIBLE);
        groupNameText.setVisibility(View.VISIBLE);
        groupInfoText.setVisibility(View.VISIBLE);
        groupJoinButton.setVisibility(View.GONE);

        String emoji = json.optString("group_emoji", "🐥");
        String groupName = json.optString("group_name", "내 그룹");

        groupAvatarText.setText(emoji);
        groupNameText.setText(groupName);

        int memberCount = json.optInt("member_count", 0);

        if (json.isNull("pace_min_sec")
                || json.isNull("pace_max_sec")) {

            groupInfoText.setText(
                    String.format(Locale.KOREA,
                            "페이스 정보 없음 · %d명", memberCount)
            );

        } else {

            double minPace = json.optDouble("pace_min_sec");
            double maxPace = json.optDouble("pace_max_sec");

            groupInfoText.setText(String.format(Locale.KOREA,
                    "페이스 %s~%s · %d명",
                    RunFormat.pace(minPace),
                    RunFormat.pace(maxPace),
                    memberCount));
        }

        bindFeed();
        bindLeague();

        // 가입된 그룹의 레이드 정보 조회
        requestRaidStatus();
    }

    // 가입되지 않은 상태 표시
    private void showNotJoinedGroup() {

        // 기존 커뮤니티 화면 숨김
        communityScroll.setVisibility(View.GONE);

        // 신규 그룹 참여 화면 표시
        groupJoinScreen.setVisibility(View.VISIBLE);

        // 기존 카드 내부 참여 버튼 사용 안 함
        groupJoinButton.setVisibility(View.GONE);

        // 참여 버튼 초기화
        groupJoinActionButton.setText("그룹 참여하기");
        groupJoinActionButton.setEnabled(true);
        groupJoinActionButton.setAlpha(1.0f);
    }

    // 그룹 참여 API 호출
    private void requestGroupJoin() {
        groupJoinActionButton.setEnabled(false);
        groupJoinActionButton.setText("참여 중...");
        groupJoinActionButton.setAlpha(0.6f);

        networkExecutor.execute(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("user_id", userId);
                RunCatchApi.post("/community-groups/join", requestBody);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(this,
                            "그룹에 참여했습니다!",
                            Toast.LENGTH_SHORT).show();

                    // 참여 성공 후 최신 그룹 정보 다시 조회
                    requestGroupInfo();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    groupJoinActionButton.setEnabled(true);
                    groupJoinActionButton.setText("그룹 참여하기");
                    groupJoinActionButton.setAlpha(1.0f);

                    Toast.makeText(this,
                            "그룹 참여에 실패했습니다: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });

            }
        });
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
            sections[i].setVisibility(
                    i == index ? View.VISIBLE : View.GONE
            );
        }
    }

    // ===== 피드 =====

    private void bindFeed() {
        requestFeed();
    }

    // 서버에서 그룹 피드 조회
    private void requestFeed() {
        networkExecutor.execute(() -> {
            try {
                JSONObject json = RunCatchApi.get("/community-groups/" + RunCatchApi.encode(userId) + "/feed");

                if (!json.optBoolean("is_joined", false)) {
                    return;
                }

                JSONArray posts = json.optJSONArray("posts");

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    renderFeed(posts);
                });

            } catch (Exception e) {
                Log.e("CommunityActivity", "피드 조회 실패", e);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(this,
                            "피드를 불러오지 못했습니다: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });

            }
        });
    }

    // 서버에서 받은 게시글을 화면에 표시
    private void renderFeed(JSONArray posts) {
        LinearLayout feedList = findViewById(R.id.feedList);
        feedList.removeAllViews();

        if (posts == null) return;

        LayoutInflater inflater = LayoutInflater.from(this);

        int displayCount = Math.min(posts.length(), 10);

        for (int i = 0; i < displayCount; i++) {
            JSONObject post = posts.optJSONObject(i);

            if (post == null) continue;

            View item = inflater.inflate(
                    R.layout.item_feed_post, feedList, false
            );

            String emoji = post.optString("character_emoji", "🐥");
            String nickname = post.optString("nickname", "러너");
            int level = post.optInt("level", 1);

            String postUserId = post.optString("user_id", "");
            boolean isMyPost = userId.equals(postUserId);

            long runDate = post.optLong("run_date", 0);

            double distanceKm = post.optDouble("distance_km", 0);
            double durationSec = post.optDouble("duration_sec", 0);
            double paceSec = post.optDouble("pace_sec", 0);

            long runningNumber = post.optLong("running_number", -1);
            boolean canReact = !isMyPost && runningNumber > 0;
            Map<String, int[]> reactions = parseReactions(post.optJSONArray("reactions"));

            ((TextView) item.findViewById(R.id.feedAvatarText))
                    .setText(emoji);

            ((TextView) item.findViewById(R.id.feedNameText))
                    .setText(nickname);

            ((TextView) item.findViewById(R.id.feedLevelText))
                    .setText("LV." + level);

            ((TextView) item.findViewById(R.id.feedTimeText))
                    .setText(formatTimeAgo(runDate));

            ((TextView) item.findViewById(R.id.feedDistanceText))
                    .setText(String.format(Locale.US,
                            "%.1f km", distanceKm));

            ((TextView) item.findViewById(R.id.feedDurationText))
                    .setText(RunFormat.clock(Math.round(durationSec)));

            ((TextView) item.findViewById(R.id.feedPaceText))
                    .setText(RunFormat.pace(paceSec));

            TextView clap = item.findViewById(R.id.feedClapChip);
            TextView fire = item.findViewById(R.id.feedFireChip);
            TextView muscle = item.findViewById(R.id.feedMuscleChip);

            // 기본 3종은 개수가 0이어도 표시, 나머지 이모지는 "+" 앞에 칩으로 추가
            bindReaction(clap, "👏", reactions.remove("👏"), canReact, false, runningNumber);
            bindReaction(fire, "🔥", reactions.remove("🔥"), canReact, false, runningNumber);
            bindReaction(muscle, "💪", reactions.remove("💪"), canReact, false, runningNumber);

            // "+" : 휴대폰 전체 이모지에서 골라 반응 보내기
            HorizontalScrollView reactionScroll = item.findViewById(R.id.feedReactionScroll);
            LinearLayout reactionRow = item.findViewById(R.id.feedReactionRow);
            View addChip = item.findViewById(R.id.feedAddReactionChip);

            for (Map.Entry<String, int[]> reaction : reactions.entrySet()) {
                addReactionChip(reactionRow, addChip, reaction.getKey(), reaction.getValue(),
                        canReact, runningNumber);
            }

            // 내 게시글에는 반응을 추가할 수 없도록 제한
            addChip.setVisibility(canReact ? View.VISIBLE : View.GONE);

            if (canReact) {
                addChip.setOnClickListener(v -> showEmojiPicker(selectedEmoji ->
                        sendCustomReaction(reactionScroll, reactionRow, addChip, selectedEmoji,
                                runningNumber)));
            }

            TextView cheer = item.findViewById(R.id.feedCheerButton);
            View avatar = item.findViewById(R.id.feedAvatarText);

            if (!canReact) {
                cheer.setVisibility(View.GONE);
            } else {
                cheer.setVisibility(View.VISIBLE);

                if (post.optBoolean("cheered_by_me", false)) {
                    showCheered(cheer);
                } else {
                    cheer.setOnClickListener(v -> {
                        CharSequence before = cheer.getText();
                        CommunityEffects.cheerBurst(this, cheer, avatar);
                        showCheered(cheer);
                        sendCheer(runningNumber, cheer, before, nickname);
                    });
                }
            }

            feedList.addView(item);
        }
    }

    // Unix timestamp(ms)를 상대 시간으로 변환
    private String formatTimeAgo(long timestamp) {
        if (timestamp <= 0) {
            return "";
        }

        long diff = System.currentTimeMillis() - timestamp;

        if (diff < 0) {
            return "방금 전";
        }

        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);
        long days = diff / (24 * 60 * 60 * 1000);

        if (minutes < 1) {
            return "방금 전";
        } else if (minutes < 60) {
            return minutes + "분 전";
        } else if (hours < 24) {
            return hours + "시간 전";
        } else if (days < 30) {
            return days + "일 전";
        } else {
            Calendar date = Calendar.getInstance();
            date.setTimeInMillis(timestamp);

            return String.format(Locale.KOREA,
                    "%d월 %d일",
                    date.get(Calendar.MONTH) + 1,
                    date.get(Calendar.DAY_OF_MONTH));
        }
    }

    // 서버 reactions 배열 → 이모지별 {개수, 내가 눌렀으면 1} (서버가 보낸 순서 유지)
    private Map<String, int[]> parseReactions(JSONArray array) {
        Map<String, int[]> reactions = new LinkedHashMap<>();
        if (array == null) return reactions;
        for (int i = 0; i < array.length(); i++) {
            JSONObject reaction = array.optJSONObject(i);
            if (reaction == null) continue;
            reactions.put(reaction.optString("emoji"), new int[]{
                    reaction.optInt("count", 0), reaction.optBoolean("mine", false) ? 1 : 0});
        }
        return reactions;
    }

    /**
     * 반응 칩: 누르면 +1 되고 하늘색, 다시 누르면 취소. 서버에 바로 저장
     *
     * @param state     {개수, 내가 눌렀으면 1}. 아무도 안 눌렀으면 null
     * @param removable "+"로 추가한 칩이면 true (취소해서 0개가 되면 칩을 없앰)
     */
    private void bindReaction(TextView chip, String emoji, int[] state, boolean canReact,
                              boolean removable, long runningNumber) {
        int count = state == null ? 0 : state[0];
        boolean mine = state != null && state[1] == 1;

        chip.setTag(emoji);
        chip.setSelected(mine);
        chip.setText(emoji + " " + count);

        if (!canReact) {
            chip.setOnClickListener(null);
            chip.setClickable(false);
            return;
        }

        chip.setClickable(true);

        // 내 반응을 뺀 개수
        int others = count - (mine ? 1 : 0);

        chip.setOnClickListener(v -> {
            boolean add = !chip.isSelected();
            showReaction(chip, emoji, others, add);

            if (add) {
                CommunityEffects.reactionPop(this, chip, emoji);
            } else {
                CommunityEffects.reactionCancel(chip);
            }
            sendReaction(runningNumber, emoji, add, chip, others, removable);
        });
    }

    private void showReaction(TextView chip, String emoji, int others, boolean mine) {
        chip.setSelected(mine);
        chip.setText(emoji + " " + (others + (mine ? 1 : 0)));
    }

    // 반응 저장 · 취소 (POST / DELETE /runs/{running_number}/reactions). 실패하면 칩을 되돌림
    private void sendReaction(long runningNumber, String emoji, boolean add, TextView chip,
                              int others, boolean removable) {
        networkExecutor.execute(() -> {
            try {
                String path = "/runs/" + runningNumber + "/reactions";
                if (add) {
                    JSONObject body = new JSONObject();
                    body.put("user_id", userId);
                    body.put("emoji", emoji);
                    RunCatchApi.post(path, body);
                } else {
                    RunCatchApi.delete(path + "?user_id=" + RunCatchApi.encode(userId)
                            + "&emoji=" + RunCatchApi.encode(emoji));
                }
                runOnUiThread(() -> {
                    if (!add && removable && others == 0) {
                        chip.setOnClickListener(null);
                        ViewGroup parent = (ViewGroup) chip.getParent();
                        if (parent != null) parent.removeView(chip);
                    }
                });
            } catch (Exception e) {
                Log.e("CommunityActivity", "반응 저장 실패", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    showReaction(chip, emoji, others, !add);
                    Toast.makeText(this, "반응을 저장하지 못했습니다", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showCheered(TextView cheer) {
        cheer.setText("응원 완료");
        cheer.setEnabled(false);
        cheer.setAlpha(0.6f);
    }

    // 응원 저장 (POST /runs/{running_number}/cheer). 실패하면 버튼을 되돌림
    private void sendCheer(long runningNumber, TextView cheer, CharSequence before, String nickname) {
        networkExecutor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", userId);
                RunCatchApi.post("/runs/" + runningNumber + "/cheer", body);
                runOnUiThread(() -> Toast.makeText(this,
                        nickname + "님에게 응원을 보냈어요", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e("CommunityActivity", "응원 저장 실패", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    cheer.setText(before);
                    cheer.setEnabled(true);
                    cheer.setAlpha(1f);
                    Toast.makeText(this, "응원을 보내지 못했습니다", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private TextView addReactionChip(LinearLayout row, View addChip, String emoji, int[] state,
                                     boolean canReact, long runningNumber) {
        TextView chip = new TextView(this, null, 0, R.style.ReactionChip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        lp.setMarginStart(dp(6));
        row.addView(chip, row.indexOfChild(addChip), lp);
        bindReaction(chip, emoji, state, canReact, true, runningNumber);
        return chip;
    }

    // ===== 이모지 선택창 ("+" 칩) =====

    private interface OnEmojiChosen {
        void onChosen(String emoji);
    }

    private void showEmojiPicker(OnEmojiChosen listener) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View sheet = LayoutInflater.from(this).inflate(R.layout.dialog_emoji_picker, null, false);
        EmojiPickerView picker = sheet.findViewById(R.id.emojiPicker);
        picker.setOnEmojiPickedListener(item -> {
            dialog.dismiss();
            listener.onChosen(item.getEmoji());
        });
        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
    }

    /**
     * 고른 이모지로 반응 보내기.
     * - 카드에 이미 있는 이모지
     * - 처음 보는 이모지
     * 이모지 문자열을 그대로 서버에 보냄
     */
    private void sendCustomReaction(HorizontalScrollView scroll, LinearLayout row,
                                    View addChip, String emoji, long runningNumber) {
        TextView chip = row.findViewWithTag(emoji);

        if (chip == null) {
            // 새로 추가한 이모지는 클릭 가능하며 취소하면 칩을 제거
            chip = addReactionChip(row, addChip, emoji, null, true, runningNumber);
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

        ((TextView) findViewById(R.id.leagueTitleText))
                .setText(month + "월 리그 · 한 달 시즌");

        ((TextView) findViewById(R.id.leagueDeadlineText))
                .setText("D-" + dDay + " · "
                        + month + "월 " + lastDay + "일 마감");

        requestLeague();
    }

    // 서버에서 월간 랭킹 조회
    private void requestLeague() {
        networkExecutor.execute(() -> {
            try {
                JSONObject json = RunCatchApi.get("/community-groups/" + RunCatchApi.encode(userId) + "/league");

                if (!json.optBoolean("is_joined", false)) {
                    return;
                }

                JSONArray rankings = json.optJSONArray("rankings");

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    renderLeague(rankings);
                });

            } catch (Exception e) {
                Log.e("CommunityActivity", "랭킹 조회 실패", e);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;

                    Toast.makeText(this,
                            "랭킹을 불러오지 못했습니다: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });

            }
        });
    }

    // 서버에서 받은 랭킹을 화면에 표시
    private void renderLeague(JSONArray rankings) {
        LinearLayout leagueList = findViewById(R.id.leagueList);
        leagueList.removeAllViews();

        if (rankings == null) return;

        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < rankings.length(); i++) {
            JSONObject rank = rankings.optJSONObject(i);

            if (rank == null) continue;

            int rankNumber = rank.optInt("rank", i + 1);

            String emoji = rank.optString("character_emoji", "🐥");
            String nickname = rank.optString("nickname", "러너");

            int level = rank.optInt("level", 1);
            double distanceKm = rank.optDouble("distance_km", 0);

            boolean isMe = rank.optBoolean("is_me", false);
            int streakDays = rank.optInt("streak_days", 0);

            View row = inflater.inflate(
                    R.layout.item_league_rank, leagueList, false
            );

            TextView number = row.findViewById(R.id.rankNumberText);

            if (rankNumber >= 1 && rankNumber <= MEDALS.length) {
                number.setText(MEDALS[rankNumber - 1]);
            } else {
                number.setText(String.valueOf(rankNumber));
            }

            ((TextView) row.findViewById(R.id.rankAvatarText))
                    .setText(emoji);

            ((TextView) row.findViewById(R.id.rankNameText))
                    .setText(nickname);

            ((TextView) row.findViewById(R.id.rankLevelText))
                    .setText("LV." + level);

            ((TextView) row.findViewById(R.id.rankDistanceText))
                    .setText(String.format(Locale.US,
                            "%.1f km", distanceKm));

            TextView streak = row.findViewById(R.id.rankStreakText);
            streak.setVisibility(View.GONE);

            if (streakDays > 0) {
                streak.setText("🔥" + streakDays + "일");
                streak.setVisibility(View.VISIBLE);
            }

            if (isMe) {
                row.setBackgroundResource(R.drawable.bg_rank_me);

                ((TextView) findViewById(R.id.myRankText)).setText(
                        String.format(Locale.US, "%d위 / %d명",
                                rankNumber, rankings.length())
                );

                ((TextView) findViewById(R.id.myLeagueDistanceText))
                        .setText(String.format(Locale.US,
                                "%.1f km", distanceKm));
            }

            leagueList.addView(row);
        }
    }

    // ===== 레이드 =====

    // 서버에서 그룹 레이드 정보 조회
    private void requestRaidStatus() {
        networkExecutor.execute(() -> {
            try {
                JSONObject json = RunCatchApi.get("/community-raids/" + RunCatchApi.encode(userId));

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    if (!json.optBoolean("is_joined", false)) {
                        return;
                    }

                    renderRaid(json);

                    if (json.optBoolean("just_completed", false)) {
                        Toast.makeText(
                                this,
                                "레이드 달성! 그룹 전원에게 보상이 지급되었습니다.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });

            } catch (Exception e) {
                Log.e("COMMUNITY_RAID", "레이드 정보 조회 실패", e);

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(
                                this,
                                "레이드 정보를 불러오지 못했습니다.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });

            }
        });
    }

    // 서버에서 받은 레이드 정보를 화면에 표시
    private void renderRaid(JSONObject json) {
        double targetKm =
                json.optDouble("target_distance", 0) / 1000.0;

        double currentKm =
                json.optDouble("current_distance", 0) / 1000.0;

        double myKm =
                json.optDouble("my_distance", 0) / 1000.0;

        double progressPercent =
                json.optDouble("progress_percent", 0);

        double remainKm = Math.max(targetKm - currentKm, 0);

        boolean isCompleted =
                json.optBoolean("is_completed", false);

        int rewardExp =
                json.optInt("reward_exp", 200);

        // 그룹 누적 거리 / 목표 거리
        ((TextView) findViewById(R.id.raidProgressText))
                .setText(String.format(
                        Locale.US,
                        "%,.1f / %,.0f km",
                        currentKm,
                        targetKm
                ));

        // 진행률
        ProgressBar progressBar =
                findViewById(R.id.raidProgress);

        progressBar.setMax(100);
        progressBar.setProgress(
                (int) Math.max(
                        0,
                        Math.min(100, Math.round(progressPercent))
                )
        );

        // 내 기여 거리
        ((TextView) findViewById(R.id.raidMyDistanceText))
                .setText(String.format(
                        Locale.US,
                        "%.1f km",
                        myKm
                ));

        // 남은 거리
        ((TextView) findViewById(R.id.raidRemainText))
                .setText(String.format(
                        Locale.US,
                        "%,.1f km",
                        remainKm
                ));

        // 이번 달 마감까지 남은 날짜
        Calendar now = Calendar.getInstance();

        int dDay = now.getActualMaximum(Calendar.DAY_OF_MONTH)
                - now.get(Calendar.DAY_OF_MONTH);

        ((TextView) findViewById(R.id.raidDdayText))
                .setText("D-" + dDay);

        // 레이드 보상 및 완료 상태
        TextView rewardTitle =
                findViewById(R.id.raidRewardTitleText);

        TextView rewardDescription =
                findViewById(R.id.raidRewardDescriptionText);

        if (isCompleted) {
            rewardTitle.setText("레이드 달성 완료!");

            rewardDescription.setText(
                    "그룹 전원 +" + rewardExp + " EXP 지급 완료"
            );
        } else {
            rewardTitle.setText("🎁 레이드 성공 보상");

            rewardDescription.setText(
                    "목표 달성 시 그룹 전원 +" + rewardExp + " EXP"
            );
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        networkExecutor.shutdownNow();
    }
}

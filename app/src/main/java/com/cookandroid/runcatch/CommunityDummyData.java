package com.cookandroid.runcatch;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 커뮤니티 화면에 보여줄 임시 데이터.
 *
 * TODO(서버): 그룹 · 피드 · 리그 · 레이드 데이터는 서버에서 받아오도록 바꾼다.
 *             화면 코드는 이 클래스의 모양(Group, FeedPost, LeagueRank, Raid)만 보고 그리므로,
 *             나중에 서버 응답을 같은 모양으로 만들어 넘기면 화면은 그대로 쓸 수 있다.
 */
public final class CommunityDummyData {

    private CommunityDummyData() {
    }

    /** 내가 속한 그룹 (총 레벨 + 평균 페이스로 묶인 20명) */
    public static class Group {
        public final String emoji;
        public final String name;
        public final int paceMinSec;   // 그룹 페이스 범위 (초/km)
        public final int paceMaxSec;
        public final int memberCount;

        public Group(String emoji, String name, int paceMinSec, int paceMaxSec, int memberCount) {
            this.emoji = emoji;
            this.name = name;
            this.paceMinSec = paceMinSec;
            this.paceMaxSec = paceMaxSec;
            this.memberCount = memberCount;
        }
    }

    /** 피드 글 1개 = 같은 그룹 러너의 러닝 기록 1개 */
    public static class FeedPost {
        public final String emoji;
        public final String nickname;
        public final int level;
        public final String timeAgo;
        public final float distanceKm;
        public final int durationSec;
        public int clapCount;
        public int fireCount;
        public int muscleCount;

        public FeedPost(String emoji, String nickname, int level, String timeAgo,
                        float distanceKm, int durationSec, int clap, int fire, int muscle) {
            this.emoji = emoji;
            this.nickname = nickname;
            this.level = level;
            this.timeAgo = timeAgo;
            this.distanceKm = distanceKm;
            this.durationSec = durationSec;
            this.clapCount = clap;
            this.fireCount = fire;
            this.muscleCount = muscle;
        }

        /** 평균 페이스 (초/km) */
        public int paceSec() {
            return distanceKm <= 0 ? 0 : Math.round(durationSec / distanceKm);
        }
    }

    /** 월간 리그 순위 한 줄 */
    public static class LeagueRank {
        public final String emoji;
        public final String nickname;
        public final int level;
        public final float distanceKm;
        public final boolean isMe;
        public final int streakDays;

        public LeagueRank(String emoji, String nickname, int level, float distanceKm,
                          boolean isMe, int streakDays) {
            this.emoji = emoji;
            this.nickname = nickname;
            this.level = level;
            this.distanceKm = distanceKm;
            this.isMe = isMe;
            this.streakDays = streakDays;
        }
    }

    /** 그룹 레이드: 그룹 전체 합산 거리 목표 */
    public static class Raid {
        public final float goalKm;
        public final float currentKm;
        public final float myKm;

        public Raid(float goalKm, float currentKm, float myKm) {
            this.goalKm = goalKm;
            this.currentKm = currentKm;
            this.myKm = myKm;
        }
    }

    public static final Group GROUP = new Group("🐥", "총 레벨 40대 그룹", 360, 390, 20);

    public static List<FeedPost> feed() {
        return Arrays.asList(
                new FeedPost("🐰", "달리는토끼", 45, "30분 전", 5.2f, 1880, 8, 3, 5),
                new FeedPost("🐢", "꾸준거북", 41, "1시간 전", 3.4f, 1265, 5, 1, 2),
                new FeedPost("🐈", "새벽냥", 43, "오전 6:40", 4.0f, 1512, 11, 4, 6),
                new FeedPost("🐻", "곰돌러너", 38, "어제", 2.8f, 1060, 3, 0, 1));
    }

    /** 거리 순으로 정렬된 상태라고 가정 */
    public static List<LeagueRank> league() {
        return Arrays.asList(
                new LeagueRank("🐰", "달리는토끼", 45, 58.0f, false, 0),
                new LeagueRank("🐢", "꾸준거북", 41, 51.4f, false, 0),
                new LeagueRank("🐥", "나", 42, 46.2f, true, 5),
                new LeagueRank("🐈", "새벽냥", 43, 39.8f, false, 0),
                new LeagueRank("🐻", "곰돌러너", 38, 35.1f, false, 0));
    }

    public static final Raid RAID = new Raid(1000f, 612f, 46.2f);

    /** 6'01"/km 형식 */
    public static String formatPace(int paceSec) {
        if (paceSec <= 0) return "-- /km";
        return String.format(Locale.US, "%d'%02d\"/km", paceSec / 60, paceSec % 60);
    }

    /** 00:31:20 형식 */
    public static String formatDuration(int sec) {
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
    }
}

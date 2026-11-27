package com.cookandroid.runcatch.ui.my;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 캐릭터 한 마리의 움직임 값과 애니메이션 프레임. characterId는 RunCharacter.id
 * 캐릭터 데이터는 core 모듈의 RunCharacter에 두고, 화면에서 움직이는 방식만 여기에
 */
public class Motion {

    // 캐릭터의 현재 동작. 앞: 아래로 이동, 뒤: 위로 이동, 옆: 좌우로 이동, 정지·쉼: 쉬는 시간, 쓰다듬기: 눌렸을 때
    public enum State {
        FRONT, BACK, SIDE, IDLE, REST_1, REST_2, REST_3, REST_4, REST_5, PAT
    }

    // 한 동작의 스프라이트 시트
    public static class Sprite {
        // assets/character_motion 안의 "폴더/파일" 이름
        public final String fileName;
        public final int frameWidth;
        public final int frameHeight;
        private final int columns;
        // 프레임마다 보여 주는 시간(ms). 시트와 함께 받은 json의 duration 값
        private final int[] durationsMs;
        public final int totalDurationMs;

        Sprite(String fileName, int columns, int frameWidth, int frameHeight, int... durationsMs) {
            this.fileName = fileName;
            this.columns = columns;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.durationsMs = durationsMs;
            int total = 0;
            for (int duration : durationsMs) total += duration;
            this.totalDurationMs = total;
        }
        public int frameAt(long elapsedMs) {
            long time = elapsedMs % totalDurationMs;
            for (int i = 0; i < durationsMs.length; i++) {
                time -= durationsMs[i];
                if (time < 0) return i;
            }
            return durationsMs.length - 1;
        }

        public int frameLeft(int frame) {
            return (frame % columns) * frameWidth;
        }

        public int frameTop(int frame) {
            return (frame / columns) * frameHeight;
        }
    }

    public static final int FRAMES_PER_SECOND = 24;
    public static final int FRAME_COUNT = 4;

    // 쉬는 시간에 무작위로 고르는 쉬는 모션
    public static final State[] REST_MOTIONS = {
            State.REST_1, State.REST_2, State.REST_3, State.REST_4, State.REST_5
    };
    // 쉬기 시작한 뒤 쉬는 모션을 시작할 때까지의 시간. 쉬는 시간보다 길면 모션 없이 이동한다
    public static final int MIN_REST_MOTION_DELAY_MS = 2000;
    public static final int MAX_REST_MOTION_DELAY_MS = 6000;
    // 쉬는 모션을 재생하는 시간. 시작까지의 시간과 합쳐 최대 30초가 되도록 맞춘 값
    public static final int MIN_REST_MOTION_DURATION_MS = 3000;
    public static final int MAX_REST_MOTION_DURATION_MS = 20000;

    // 숫자: 속도(dp/초), 최소·최대 이동 거리(dp), 최소·최대 쉬는 시간(ms), (스프라이트 캐릭터만) 크기 배율
    private static final List<Motion> ALL = Collections.unmodifiableList(Arrays.asList(
            new Motion(1, 70f, 120f, 180f, 4000, 17500),     // 수달
            new Motion(2, 40f, 80f, 120f, 6000, 20000),     // 오리
            new Motion(3, 110f, 80f, 140f, 2000, 10000),    // 다람쥐
            new Motion(4, 30f, 60f, 80f, 7500, 25000),     // 고슴도치
            new Motion(5, 80f, 160f, 200f, 10000, 30000),   // 고양이
            new Motion(6, 65f, 60f, 100f, 2500, 12500),     // 비둘기
            new Motion(7, 75f, 160f, 200f, 5000, 20000),    // 산양
            new Motion(8, 120f, 240f, 280f, 12500, 30000),  // 독수리
            new Motion(9, 130f, 200f, 220f, 3000, 12500),   // 토끼
            new Motion(10, 90f, 160f, 200f, 4000, 17500),   // 여우
            new Motion(11, 35f, 160f, 180f, 12500, 30000),   // 곰
            new Motion(12, 50f, 40f, 70f, 1500, 7500),     // 삐약이
            new Motion(13, 100f, 160f, 220f, 1500, 10000),  // 강아지
            new Motion(14, 85f, 240f, 260f, 7500, 25000),   // 유니콘
            // TODO: 임시 캐릭터. 쉼3~5는 아직 없다
            new Motion(15, 60f, 120f, 160f, 4500, 22500, 1f,
                    Collections.<State, String[]>emptyMap(), createFireSprites()),
            // TODO: 임시 캐릭터 2~4. 프레임 시간은 각 폴더의 json 값
            new Motion(16, 40f, 100f, 140f, 6000, 25000, 1f,     // 낙타
                    Collections.<State, String[]>emptyMap(), createSheetSprites("nakta",
                            new int[]{70, 60, 100, 100, 70, 60, 70, 60, 100, 100, 70, 60},
                            repeat(80, 12), repeat(75, 12),
                            new int[]{120, 120, 180, 120, 130, 180, 130})),
            new Motion(17, 100f, 160f, 220f, 3000, 15000, 1f,    // 제비
                    Collections.<State, String[]>emptyMap(), createSheetSprites("jeavi",
                            repeat(60, 12), repeat(60, 12), repeat(60, 12),
                            new int[]{1100, 60, 350, 60, 1100, 60, 350})),
            new Motion(18, 225f, 300f, 390f, 2000, 12000, 1f,    // 로드러너
                    Collections.<State, String[]>emptyMap(), createSheetSprites("roadrunner",
                            repeat(40, 12), repeat(40, 12), repeat(40, 12),
                            new int[]{120, 120, 180, 120, 130, 180, 130}))));

    // 목록에 아직 없는 캐릭터가 쓰는 기본값
    private static final Motion DEFAULT = new Motion(-1, 60f, 120f, 160f, 4500, 22500);

    public final int characterId;
    // 크기 1배일 때의 속도(dp/초)와 한 번 이동하는 거리(dp). 화면에서는 현재 크기 배율을 곱해서 쓴다
    public final float moveSpeedDpPerSec;
    public final float minMoveRadiusDp;
    public final float maxMoveRadiusDp;
    // 한 번 이동한 뒤 다음 이동까지 쉬는 시간(ms)
    public final int minPauseMs;
    public final int maxPauseMs;
    // 기본 크기(200dp)에 곱하는 캐릭터 크기 배율
    public final float sizeScale;
    // 동작별 프레임. 비어 있으면 애니메이션 없이 캐릭터 이모지를 그대로 보여 준다
    private final Map<State, String[]> frames;
    // 동작별 스프라이트 시트. 있으면 글자 프레임 대신 이미지를 보여 준다
    private final Map<State, Sprite> sprites;

    private Motion(int characterId, float moveSpeedDpPerSec,
                   float minMoveRadiusDp, float maxMoveRadiusDp,
                   int minPauseMs, int maxPauseMs) {
        this(characterId, moveSpeedDpPerSec, minMoveRadiusDp, maxMoveRadiusDp,
                minPauseMs, maxPauseMs, 1f, Collections.<State, String[]>emptyMap(),
                Collections.<State, Sprite>emptyMap());
    }

    private Motion(int characterId, float moveSpeedDpPerSec,
                   float minMoveRadiusDp, float maxMoveRadiusDp,
                   int minPauseMs, int maxPauseMs, float sizeScale,
                   Map<State, String[]> frames, Map<State, Sprite> sprites) {
        this.characterId = characterId;
        this.moveSpeedDpPerSec = moveSpeedDpPerSec;
        this.minMoveRadiusDp = minMoveRadiusDp;
        this.maxMoveRadiusDp = maxMoveRadiusDp;
        this.minPauseMs = minPauseMs;
        this.maxPauseMs = maxPauseMs;
        this.sizeScale = sizeScale;
        this.frames = frames;
        this.sprites = sprites;
    }
    public static Motion findById(int characterId) {
        for (Motion motion : ALL) {
            if (motion.characterId == characterId) return motion;
        }
        return DEFAULT;
    }

    public boolean hasFrames() {
        return !frames.isEmpty() || !sprites.isEmpty();
    }
    public boolean hasSprites() {
        return !sprites.isEmpty();
    }
    public Sprite getSprite(State state) {
        return sprites.get(state);
    }
    public List<State> getRestMotions() {
        List<State> restMotions = new ArrayList<>();
        for (State state : REST_MOTIONS) {
            if (frames.containsKey(state) || sprites.containsKey(state)) restMotions.add(state);
        }
        return restMotions;
    }
    public String getFrame(State state, int frameNumber) {
        String[] stateFrames = frames.get(state);
        if (stateFrames == null) return null;
        return stateFrames[frameNumber % stateFrames.length];
    }

    // 임시 캐릭터의 스프라이트 시트 (모든 칸 150x150). 칸 크기와 프레임 시간은 시트와 함께 받은 json 값
    private static Map<State, Sprite> createFireSprites() {
        Map<State, Sprite> sprites = new EnumMap<>(State.class);
        // 정지: 한 장짜리 이미지를 한 칸 시트로 쓴다
        sprites.put(State.IDLE, new Sprite("aaa/fire_idle.png", 1, 150, 150, 1000));
        sprites.put(State.FRONT, new Sprite("aaa/fire_walk_front.png", 4, 150, 150,
                70, 60, 100, 100, 70, 60, 70, 60, 100, 100, 70, 60));
        // 오른쪽으로 걷는 시트. 왼쪽으로 갈 때는 좌우반전해서 그린다
        sprites.put(State.SIDE, new Sprite("aaa/fire_walk_side.png", 4, 150, 150,
                75, 75, 75, 75, 75, 75, 75, 75, 75, 75, 75, 75));
        sprites.put(State.BACK, new Sprite("aaa/fire_walk_back.png", 4, 150, 150,
                75, 75, 75, 75, 75, 75, 75, 75, 75, 75, 75, 75));
        // 쉼1: 고개 갸웃거리기, 쉼2: 잠자기
        sprites.put(State.REST_1, new Sprite("aaa/fire_head_tilt.png", 4, 150, 150,
                120, 120, 180, 120, 130, 180, 130));
        sprites.put(State.REST_2, new Sprite("aaa/fire_sleep.png", 4, 150, 150,
                230, 230, 230, 230, 230, 230, 230, 230));
        sprites.put(State.PAT, new Sprite("aaa/fire_pat.png", 4, 150, 150,
                130, 80, 80, 80, 80, 80, 180, 90, 80, 90, 90, 110));
        return sprites;
    }
    private static Map<State, Sprite> createSheetSprites(String dir, int[] frontMs, int[] sideMs,
                                                         int[] backMs, int[] headTiltMs) {
        Map<State, Sprite> sprites = new EnumMap<>(State.class);
        sprites.put(State.IDLE, new Sprite(dir + "/frontal.png", 1, 150, 150, 1000));
        sprites.put(State.FRONT, new Sprite(dir + "/flare-walk-12.png", 4, 150, 150, frontMs));
        sprites.put(State.SIDE, new Sprite(dir + "/walk-right-12.png", 4, 150, 150, sideMs));
        sprites.put(State.BACK, new Sprite(dir + "/walk-back-12.png", 4, 150, 150, backMs));
        sprites.put(State.REST_1, new Sprite(dir + "/head-tilt-stable-7.png", 4, 150, 150, headTiltMs));
        sprites.put(State.REST_2, new Sprite(dir + "/sleep-8.png", 4, 150, 150, repeat(230, 8)));
        sprites.put(State.PAT, new Sprite(dir + "/happy-pat-no-finger-12.png", 4, 150, 150,
                130, 80, 80, 80, 80, 80, 180, 90, 80, 90, 90, 110));
        return sprites;
    }

    private static int[] repeat(int durationMs, int count) {
        int[] durations = new int[count];
        Arrays.fill(durations, durationMs);
        return durations;
    }
}

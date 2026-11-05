package com.cookandroid.runcatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 캐릭터 한 마리의 움직임 값과 애니메이션 프레임. characterId는 RunCharacter.id
 * 캐릭터 데이터는 backend의 RunCharacter에 두고, 화면에서 움직이는 방식만 여기에 둔다.
 */
public class Motion {

    /** 캐릭터의 현재 동작. 앞: 아래로 이동, 뒤: 위로 이동, 옆: 좌우로 이동, 정지·쉼: 쉬는 시간 */
    public enum State {
        FRONT, BACK, SIDE, IDLE, REST_1, REST_2, REST_3, REST_4, REST_5
    }

    public static final int FRAMES_PER_SECOND = 24;
    public static final int FRAME_COUNT = 4;
    public static final long FRAME_DURATION_MS = 1000 / FRAMES_PER_SECOND;

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

    // 숫자: 속도(dp/초), 최소·최대 이동 거리(dp), 최소·최대 쉬는 시간(ms)
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
            // TODO: 임시 캐릭터의 글자 프레임. 실제 이미지가 생기면 바꾼다
            new Motion(15, 60f, 120f, 160f, 4500, 22500, createTextFrames())));

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
    // 동작별 프레임. 비어 있으면 애니메이션 없이 캐릭터 이모지를 그대로 보여 준다
    private final Map<State, String[]> frames;

    private Motion(int characterId, float moveSpeedDpPerSec,
                   float minMoveRadiusDp, float maxMoveRadiusDp,
                   int minPauseMs, int maxPauseMs) {
        this(characterId, moveSpeedDpPerSec, minMoveRadiusDp, maxMoveRadiusDp,
                minPauseMs, maxPauseMs, Collections.<State, String[]>emptyMap());
    }

    private Motion(int characterId, float moveSpeedDpPerSec,
                   float minMoveRadiusDp, float maxMoveRadiusDp,
                   int minPauseMs, int maxPauseMs, Map<State, String[]> frames) {
        this.characterId = characterId;
        this.moveSpeedDpPerSec = moveSpeedDpPerSec;
        this.minMoveRadiusDp = minMoveRadiusDp;
        this.maxMoveRadiusDp = maxMoveRadiusDp;
        this.minPauseMs = minPauseMs;
        this.maxPauseMs = maxPauseMs;
        this.frames = frames;
    }

    /** 캐릭터 id로 움직임 찾기. 목록에 없으면 기본값 */
    public static Motion findById(int characterId) {
        for (Motion motion : ALL) {
            if (motion.characterId == characterId) return motion;
        }
        return DEFAULT;
    }

    public boolean hasFrames() {
        return !frames.isEmpty();
    }

    /** 동작의 frameNumber번째 프레임 (0부터 시작). 마지막 프레임 다음에는 처음으로 돌아간다 */
    public String getFrame(State state, int frameNumber) {
        String[] stateFrames = frames.get(state);
        return stateFrames[frameNumber % stateFrames.length];
    }

    private static Map<State, String[]> createTextFrames() {
        Map<State, String[]> frames = new EnumMap<>(State.class);
        frames.put(State.FRONT, createDotFrames("앞"));
        frames.put(State.BACK, createDotFrames("뒤"));
        frames.put(State.SIDE, createDotFrames("옆"));
        frames.put(State.IDLE, new String[]{"정지"});
        frames.put(State.REST_1, createDotFrames("쉼1"));
        frames.put(State.REST_2, createDotFrames("쉼2"));
        frames.put(State.REST_3, createDotFrames("쉼3"));
        frames.put(State.REST_4, createDotFrames("쉼4"));
        frames.put(State.REST_5, createDotFrames("쉼5"));
        return frames;
    }

    /** 글자 뒤에 점을 1개부터 FRAME_COUNT개까지 붙인 프레임을 만든다 ("옆.", "옆..", ...) */
    private static String[] createDotFrames(String label) {
        String[] dotFrames = new String[FRAME_COUNT];
        StringBuilder text = new StringBuilder(label);
        for (int i = 0; i < FRAME_COUNT; i++) {
            text.append('.');
            dotFrames[i] = text.toString();
        }
        return dotFrames;
    }
}

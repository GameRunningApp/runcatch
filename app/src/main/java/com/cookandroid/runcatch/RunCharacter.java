package com.cookandroid.runcatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RunCharacter {

    // 전체 캐릭터 목록. id는 DB(user.character_id, user_character.character_id)에 저장되는 값
    // 획득 여부는 여기 두지 않고 DB의 user_character로 판단한다
    public static final List<RunCharacter> ALL = Collections.unmodifiableList(Arrays.asList(
            new RunCharacter(1, "🦦", "수달", CharacterCategory.WATER,
                    "강·하천 근처 1Km 이상 러닝", "물가를 따라 달려보세요",
                    "강바람을 좋아하는 러닝 메이트"),
            new RunCharacter(2, "🦆", "오리", CharacterCategory.WATER,
                    "호수 근처 러닝 3회", "잔잔한 호숫가를 자주 찾아보세요",
                    "호숫가 산책로의 단골 손님"),
            new RunCharacter(3, "🐿️", "다람쥐", CharacterCategory.FOREST,
                    "공원·산책로 1Km 이상 러닝", "나무가 많은 곳을 달려보세요",
                    "작지만 꾸준한 러너"),
            new RunCharacter(4, "🦔", "고슴도치", CharacterCategory.FOREST,
                    "숲길 누적 5Km 러닝", "숲길을 여러 번 달려보세요",
                    "천천히 그러나 끝까지 달리는 친구"),
            new RunCharacter(5, "🐈", "고양이", CharacterCategory.CITY,
                    "도심 거리 1Km 이상 러닝", "도시 한복판을 달려보세요",
                    "밤거리 러닝을 즐기는 러너"),
            new RunCharacter(6, "🕊️", "비둘기", CharacterCategory.CITY,
                    "번화가 러닝 3회", "사람이 많은 거리를 자주 달려보세요",
                    "도시 어디서나 만나는 친구"),
            new RunCharacter(7, "🐐", "산양", CharacterCategory.MOUNTAIN,
                    "오르막길 1Km 이상 러닝", "언덕길에 도전해보세요",
                    "오르막을 두려워하지 않는 러너"),
            new RunCharacter(8, "🦅", "독수리", CharacterCategory.MOUNTAIN,
                    "산길 누적 5Km 러닝", "높은 곳을 여러 번 달려보세요",
                    "정상에서 내려다보는 걸 좋아하는 친구"),
            new RunCharacter(9, "🐰", "토끼", CharacterCategory.DISTANCE,
                    "누적 거리 10Km 달성", "조금만 더 달려보세요",
                    "가볍게 뛰는 걸 좋아하는 친구"),
            new RunCharacter(10, "🦊", "여우", CharacterCategory.DISTANCE,
                    "누적 거리 20Km 달성", "꾸준히 거리를 쌓아보세요",
                    "페이스 조절의 달인"),
            new RunCharacter(11, "🐻", "곰", CharacterCategory.DISTANCE,
                    "누적 거리 30Km 달성", "멀리 보고 달려보세요",
                    "든든한 장거리 러너"),
            new RunCharacter(12, "🐥", "삐약이", CharacterCategory.STREAK,
                    "3일 연속 러닝", "며칠 연속으로 달려보세요",
                    "처음 만나는 러닝 동료"),
            new RunCharacter(13, "🐶", "강아지", CharacterCategory.STREAK,
                    "7일 연속 러닝", "일주일 동안 하루도 빠짐없이 달려보세요",
                    "언제나 함께 달려주는 친구"),
            new RunCharacter(14, "🦄", "유니콘", CharacterCategory.LEAGUE,
                    "월간 리그 1위", "이번 달 리그 정상에 도전해보세요",
                    "리그 1위에게만 나타나는 전설의 러너")));

    public final int id;
    public final String emoji;
    public final String name;
    public final CharacterCategory category;
    public final String condition;
    public final String hint;
    public final String description;

    public RunCharacter(int id, String emoji, String name, CharacterCategory category,
                        String condition, String hint, String description) {
        this.id = id;
        this.emoji = emoji;
        this.name = name;
        this.category = category;
        this.condition = condition;
        this.hint = hint;
        this.description = description;
    }

    /** id로 캐릭터 찾기. 없으면 null */
    public static RunCharacter findById(int id) {
        for (RunCharacter character : ALL) {
            if (character.id == id) return character;
        }
        return null;
    }
}

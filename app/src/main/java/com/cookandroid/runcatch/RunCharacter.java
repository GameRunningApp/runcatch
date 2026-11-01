package com.cookandroid.runcatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RunCharacter {

    // 전체 캐릭터 목록. id는 user.character_id(대표 캐릭터)에 저장되는 값
    public static final List<RunCharacter> ALL = Collections.unmodifiableList(Arrays.asList(
            new RunCharacter(1, "🐥", "삐약이", 2, "처음 만나는 러닝 동료"),
            new RunCharacter(2, "🐰", "토끼", 5, "가볍게 뛰는 걸 좋아하는 친구"),
            new RunCharacter(3, "🐿️", "다람쥐", 10, "작지만 꾸준한 러너"),
            new RunCharacter(4, "🦊", "여우", 15, "페이스 조절의 달인"),
            new RunCharacter(5, "🐶", "강아지", 20, "언제나 함께 달려주는 친구"),
            new RunCharacter(6, "🐱", "고양이", 30, "밤 러닝을 즐기는 러너"),
            new RunCharacter(7, "🐼", "판다", 42.195, "풀코스 거리를 달성한 러너"),
            new RunCharacter(8, "🐧", "펭귄", 50, "추위에도 멈추지 않는 러너"),
            new RunCharacter(9, "🐨", "코알라", 70, "느긋하지만 끝까지 달리는 친구"),
            new RunCharacter(10, "🦁", "사자", 100, "100Km를 정복한 러닝의 왕"),
            new RunCharacter(11, "🐯", "호랑이", 150, "누구보다 빠른 질주 본능"),
            new RunCharacter(12, "🦄", "유니콘", 200, "전설로 남은 러너")));

    public final int id;
    public final String emoji;
    public final String name;
    public final double requiredKm;
    public final String description;

    public RunCharacter(int id, String emoji, String name, double requiredKm, String description) {
        this.id = id;
        this.emoji = emoji;
        this.name = name;
        this.requiredKm = requiredKm;
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

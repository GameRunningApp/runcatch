package com.cookandroid.runcatch;

public enum CharacterCategory {

    WATER("물", CharacterGroup.PLACE),
    FOREST("숲", CharacterGroup.PLACE),
    CITY("도시", CharacterGroup.PLACE),
    MOUNTAIN("산", CharacterGroup.PLACE),
    DISTANCE("거리", CharacterGroup.RECORD),
    STREAK("연속", CharacterGroup.RECORD),
    LEAGUE("리그", CharacterGroup.LEAGUE);

    public final String label;
    public final CharacterGroup group;

    CharacterCategory(String label, CharacterGroup group) {
        this.label = label;
        this.group = group;
    }

    public enum CharacterGroup {
        PLACE("장소"),
        RECORD("기록"),
        LEAGUE("리그");

        public final String label;

        CharacterGroup(String label) {
            this.label = label;
        }
    }
}

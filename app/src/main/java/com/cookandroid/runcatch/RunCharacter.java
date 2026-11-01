package com.cookandroid.runcatch;

public class RunCharacter {

    public final String emoji;
    public final String name;
    public final CharacterCategory category;
    public final String condition;
    public final String hint;
    public final String description;
    public final boolean unlocked;

    public RunCharacter(String emoji, String name, CharacterCategory category, String condition,
                        String hint, String description, boolean unlocked) {
        this.emoji = emoji;
        this.name = name;
        this.category = category;
        this.condition = condition;
        this.hint = hint;
        this.description = description;
        this.unlocked = unlocked;
    }
}

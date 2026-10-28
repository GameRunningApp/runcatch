package com.cookandroid.runcatch;

public class RunCharacter {

    public final String emoji;
    public final String name;
    public final double requiredKm;
    public final String description;

    public RunCharacter(String emoji, String name, double requiredKm, String description) {
        this.emoji = emoji;
        this.name = name;
        this.requiredKm = requiredKm;
        this.description = description;
    }

    public boolean isUnlocked(double totalDistanceKm) {
        return totalDistanceKm >= requiredKm;
    }
}

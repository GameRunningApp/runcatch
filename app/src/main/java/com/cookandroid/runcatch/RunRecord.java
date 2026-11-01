package com.cookandroid.runcatch;

public class RunRecord {

    public final int month;
    public final int day;
    public final double distanceKm;
    public final long durationSeconds;

    public RunRecord(int month, int day, double distanceKm, long durationSeconds) {
        this.month = month;
        this.day = day;
        this.distanceKm = distanceKm;
        this.durationSeconds = durationSeconds;
    }
}

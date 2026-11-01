package com.cookandroid.runcatch.backend;

/**
 * running_record 테이블 한 행.
 * 단위: distance m, duration 초, pace 초/km, avgSpeed·maxSpeed km/h, runDate epoch ms
 */
public class RunRecord {

    public final long runningNumber;   // 저장 전에는 0 (DB가 AUTOINCREMENT로 부여)
    public final String userId;
    public final long runDate;
    public final float distance;
    public final float duration;
    public final float pace;
    public final float avgSpeed;
    public final float maxSpeed;

    public RunRecord(long runningNumber, String userId, long runDate, float distance,
                     float duration, float pace, float avgSpeed, float maxSpeed) {
        this.runningNumber = runningNumber;
        this.userId = userId;
        this.runDate = runDate;
        this.distance = distance;
        this.duration = duration;
        this.pace = pace;
        this.avgSpeed = avgSpeed;
        this.maxSpeed = maxSpeed;
    }
}

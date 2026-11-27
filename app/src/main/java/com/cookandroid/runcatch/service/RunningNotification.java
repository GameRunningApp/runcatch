package com.cookandroid.runcatch.service;

import com.cookandroid.runcatch.R;
import com.cookandroid.runcatch.format.RunFormat;
import com.cookandroid.runcatch.ui.running.RunningActivity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.util.Locale;

/**
 * 러닝 중 알림 (잠금화면 측정판, 일시정지 · 다시 시작 버튼).
 * 알림을 누르면 RunningActivity가 열린다
 *
 * RunningService의 알림 채널 만들기, notification(), command(), updateNotification()을 옮겨 왔음
 */
final class RunningNotification {

    static final String CHANNEL = "running_tracking";
    static final int ID = 1001;

    private final Service service;

    RunningNotification(Service service) {
        this.service = service;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "러닝 기록", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("러닝 상태 확인 및 일시정지 / 다시 시작");
            // 러닝 측정 알림을 잠금화면에 공개 표시
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            service.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    /** 이미 떠 있는 알림 내용을 바꿈 */
    void update(RunTracker tracker, String error) {
        service.getSystemService(NotificationManager.class).notify(ID, build(tracker, error));
    }

    Notification build(RunTracker tracker, String error) {
        boolean paused = tracker.isPaused();
        long elapsedMillis = tracker.elapsedMillis();

        Intent open = new Intent(service, RunningActivity.class)
                .putExtra(RunningService.EXTRA_FROM_NOTIFICATION, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(service, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String distance = String.format(Locale.KOREA, "%.2f km", tracker.meters() / 1000f);
        // 잠금화면 측정판에 표시할 누적 시간과 페이스
        String elapsedTime = RunFormat.clock(elapsedMillis / 1000);
        String pace = RunFormat.pace(tracker.livePaceSeconds());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(service, CHANNEL)
                .setSmallIcon(R.drawable.ic_running_notification)
                // 거리와 페이스를 주요 측정값으로 바로 표시
                .setContentTitle("거리 " + distance)
                .setContentText((paused ? "일시정지" : "러닝 중")
                        + " · 페이스 " + pace
                        + (error != null ? " · " + error : ""))
                // 알림을 펼치면 시간 · 거리 · 페이스를 한 화면에서 확인
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("시간 " + elapsedTime
                                + "\n거리 " + distance
                                + "\n페이스 " + pace
                                + (error != null ? "\n" + error : ""))
                        .setSummaryText(paused ? "러닝 일시정지" : "러닝 측정 중"))
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                // 측정 내용이 잠금화면에서 가려지지 않도록 공개 표시
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        if (!paused) {
            builder.setWhen(System.currentTimeMillis() - elapsedMillis)
                    .setUsesChronometer(true)
                    .setShowWhen(true);
        } else {
            builder.setSubText("기록 시간 " + elapsedTime)
                    .setShowWhen(false);
        }

        builder.addAction(
                paused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause,
                paused ? "다시 시작" : "일시정지",
                command(paused ? RunningService.RESUME : RunningService.PAUSE, paused ? 2 : 1));

        return builder.build();
    }

    /** 알림 버튼을 누르면 서비스에 보낼 명령 */
    private PendingIntent command(String action, int requestCode) {
        return PendingIntent.getService(service, requestCode,
                new Intent(service, RunningService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

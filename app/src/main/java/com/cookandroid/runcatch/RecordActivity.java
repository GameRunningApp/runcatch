package com.cookandroid.runcatch;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecordActivity extends BaseActivity {

    private static final String[] WEEK_DAYS = {"월", "화", "수", "목", "금", "토", "일"};
    private static final int COLLAPSED_COUNT = 3;
    private static final int MAX_BAR_HEIGHT_DP = 64;
    private static final int MIN_BAR_HEIGHT_DP = 4;

    private List<RunRecord> records;
    private boolean expanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_record);

        applySystemBarInsets();
        setupBottomNav(R.id.recordNavButton);

        bindWeeklySummary(new double[]{3.2, 4.1, 5.1, 0, 0, 0, 0}, 84 * 60);

        records = createRecords();
        bindRecords();

        TextView moreRecordsButton = findViewById(R.id.moreRecordsButton);
        moreRecordsButton.setOnClickListener(v -> {
            expanded = !expanded;
            bindRecords();
        });
    }

    private void applySystemBarInsets() {
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);

        View header = findViewById(R.id.headerLayout);
        View bottomNav = findViewById(R.id.bottomNavLayout);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.record), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(0, bars.top, 0, 0);
            bottomNav.setPadding(0, 0, 0, bars.bottom);
            v.setPadding(bars.left, 0, bars.right, 0);
            return insets;
        });
    }

    private List<RunRecord> createRecords() {
        List<RunRecord> list = new ArrayList<>();
        list.add(new RunRecord(9, 23, 3.2, 24 * 60 + 18));
        list.add(new RunRecord(9, 22, 4.1, 27 * 60 + 42));
        list.add(new RunRecord(9, 21, 5.1, 35 * 60 + 5));
        list.add(new RunRecord(9, 18, 2.6, 17 * 60 + 50));
        list.add(new RunRecord(9, 16, 6.0, 41 * 60 + 12));
        list.add(new RunRecord(9, 14, 3.8, 26 * 60 + 33));
        return list;
    }

    private void bindWeeklySummary(double[] dailyKm, long totalSeconds) {
        TextView weeklyDistanceText = findViewById(R.id.weeklyDistanceText);
        TextView weeklyTimeText = findViewById(R.id.weeklyTimeText);

        double totalKm = 0;
        double maxKm = 0;
        for (double km : dailyKm) {
            totalKm += km;
            maxKm = Math.max(maxKm, km);
        }

        weeklyDistanceText.setText(String.format(Locale.US, "%.1f Km", totalKm));
        weeklyTimeText.setText(formatTotalTime(totalSeconds));

        bindWeekChart(dailyKm, maxKm);
    }

    private void bindWeekChart(double[] dailyKm, double maxKm) {
        LinearLayout weekChart = findViewById(R.id.weekChart);
        weekChart.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < WEEK_DAYS.length; i++) {
            View column = inflater.inflate(R.layout.item_week_bar, weekChart, false);
            View weekBar = column.findViewById(R.id.weekBar);
            TextView weekDayText = column.findViewById(R.id.weekDayText);

            double km = i < dailyKm.length ? dailyKm[i] : 0;
            int heightDp = maxKm > 0 && km > 0
                    ? (int) Math.max(MIN_BAR_HEIGHT_DP, Math.round(km / maxKm * MAX_BAR_HEIGHT_DP))
                    : MIN_BAR_HEIGHT_DP;

            ViewGroup.LayoutParams params = weekBar.getLayoutParams();
            params.height = Math.round(heightDp * density);
            weekBar.setLayoutParams(params);
            weekBar.setBackgroundResource(km > 0 ? R.drawable.bg_week_bar : R.drawable.bg_week_bar_empty);

            weekDayText.setText(WEEK_DAYS[i]);
            weekChart.addView(column);
        }
    }

    private void bindRecords() {
        LinearLayout recordListContainer = findViewById(R.id.recordListContainer);
        TextView emptyRecordText = findViewById(R.id.emptyRecordText);
        TextView moreRecordsButton = findViewById(R.id.moreRecordsButton);

        recordListContainer.removeAllViews();

        if (records.isEmpty()) {
            emptyRecordText.setVisibility(View.VISIBLE);
            moreRecordsButton.setVisibility(View.GONE);
            return;
        }

        emptyRecordText.setVisibility(View.GONE);

        int count = expanded ? records.size() : Math.min(COLLAPSED_COUNT, records.size());
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < count; i++) {
            RunRecord record = records.get(i);
            View item = inflater.inflate(R.layout.item_record, recordListContainer, false);

            TextView recordDateText = item.findViewById(R.id.recordDateText);
            TextView recordDistanceText = item.findViewById(R.id.recordDistanceText);
            TextView recordTimeText = item.findViewById(R.id.recordTimeText);

            recordDateText.setText(record.month + "월 " + record.day + "일");
            recordDistanceText.setText(String.format(Locale.US, "%.1f Km", record.distanceKm));
            recordTimeText.setText(formatDuration(record.durationSeconds));

            recordListContainer.addView(item);
        }

        if (records.size() > COLLAPSED_COUNT) {
            moreRecordsButton.setVisibility(View.VISIBLE);
            moreRecordsButton.setText(expanded ? "접기" : "더보기");
        } else {
            moreRecordsButton.setVisibility(View.GONE);
        }
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return hours + "시간 " + minutes + "분 " + secs + "초";
        }
        return minutes + "분 " + secs + "초";
    }

    private String formatTotalTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + "시간 " + minutes + "분";
        }
        return minutes + "분";
    }
}

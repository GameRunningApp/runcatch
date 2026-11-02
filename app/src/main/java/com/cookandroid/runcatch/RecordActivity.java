package com.cookandroid.runcatch;

import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.cookandroid.runcatch.backend.RunCatchDbHelper;
import com.cookandroid.runcatch.backend.RunRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordActivity extends BaseActivity {

    private static final String[] WEEK_DAYS = {"월", "화", "수", "목", "금", "토", "일"};
    private static final int COLLAPSED_COUNT = 3;
    private static final int MAX_BAR_HEIGHT_DP = 64;
    private static final int MIN_BAR_HEIGHT_DP = 4;

    // 임시 DB 테이블 표시에서 테이블당 최대 행 수 (gps_point는 행이 많아질 수 있음)
    private static final int MAX_DEBUG_ROWS = 300;

    private List<RunRecord> records;
    private boolean expanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_record);

        applySystemBarInsets();
        setupBottomNav(R.id.recordNavButton);

        RunCatchDbHelper db = RunCatchDbHelper.getInstance(this);
        String userId = RunCatchDbHelper.CURRENT_USER_ID;

        bindWeeklySummary(db.getRunsSince(userId, getWeekStartMillis()));

        records = db.getRunsSince(userId, 0);
        bindRecords();

        TextView moreRecordsButton = findViewById(R.id.moreRecordsButton);
        moreRecordsButton.setOnClickListener(v -> {
            expanded = !expanded;
            bindRecords();
        });

        // TODO(임시): DB 테이블 확인용. 확인이 끝나면 레이아웃의 debugTableCard와 함께 삭제
        LinearLayout debugTableContainer = findViewById(R.id.debugTableContainer);
        addDebugTable(debugTableContainer, RunCatchDbHelper.TABLE_USER, "mail");
        addDebugTable(debugTableContainer, RunCatchDbHelper.TABLE_RUNNING_RECORD, "running_number DESC");
        addDebugTable(debugTableContainer, RunCatchDbHelper.TABLE_GPS_POINT, "running_number DESC, sequence");
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

    /** 이번 주 월요일 0시 (epoch ms) */
    private long getWeekStartMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_MONTH, -dayIndex(cal));
        return cal.getTimeInMillis();
    }

    /** 월요일 0 ~ 일요일 6 */
    private int dayIndex(Calendar cal) {
        // Calendar.DAY_OF_WEEK는 일요일 1 ~ 토요일 7
        return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    /** 이번 주 러닝들을 요일별 거리(Km)와 총 시간으로 묶어서 표시 */
    private void bindWeeklySummary(List<RunRecord> weekRuns) {
        TextView weeklyDistanceText = findViewById(R.id.weeklyDistanceText);
        TextView weeklyTimeText = findViewById(R.id.weeklyTimeText);

        double[] dailyKm = new double[WEEK_DAYS.length];
        double totalSeconds = 0;
        Calendar cal = Calendar.getInstance();
        for (RunRecord run : weekRuns) {
            cal.setTimeInMillis(run.runDate);
            dailyKm[dayIndex(cal)] += run.distance / 1000.0;
            totalSeconds += run.duration;
        }

        double totalKm = 0;
        double maxKm = 0;
        for (double km : dailyKm) {
            totalKm += km;
            maxKm = Math.max(maxKm, km);
        }

        weeklyDistanceText.setText(String.format(Locale.US, "%.1f Km", totalKm));
        weeklyTimeText.setText(formatTotalTime(Math.round(totalSeconds)));

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
        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < count; i++) {
            RunRecord record = records.get(i);
            View item = inflater.inflate(R.layout.item_record, recordListContainer, false);

            TextView recordDateText = item.findViewById(R.id.recordDateText);
            TextView recordDistanceText = item.findViewById(R.id.recordDistanceText);
            TextView recordTimeText = item.findViewById(R.id.recordTimeText);

            cal.setTimeInMillis(record.runDate);
            recordDateText.setText((cal.get(Calendar.MONTH) + 1) + "월 "
                    + cal.get(Calendar.DAY_OF_MONTH) + "일");
            // DB 단위: distance m, duration 초
            recordDistanceText.setText(String.format(Locale.US, "%.1f Km", record.distance / 1000.0));
            recordTimeText.setText(formatDuration(Math.round(record.duration)));

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

    // ===== 임시: DB 테이블 확인용 =====

    /** 테이블 이름과 내용을 고정폭 글꼴 표로 추가 */
    private void addDebugTable(LinearLayout container, String table, String orderBy) {
        String body;
        int rowCount;
        try (Cursor c = RunCatchDbHelper.getInstance(this).getReadableDatabase().rawQuery(
                "SELECT * FROM " + table + " ORDER BY " + orderBy, null)) {
            rowCount = c.getCount();
            body = formatTable(c);
        }

        TextView title = new TextView(this);
        title.setText(table + " (" + rowCount + "행)");
        title.setTextSize(14);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(getColor(R.color.text_primary));
        title.setPadding(0, Math.round(16 * getResources().getDisplayMetrics().density), 0, 8);
        container.addView(title);

        TextView content = new TextView(this);
        content.setText(body);
        content.setTypeface(Typeface.MONOSPACE);
        content.setTextSize(11);
        content.setTextColor(getColor(R.color.text_primary));
        content.setTextIsSelectable(true);

        // 열이 많아 화면을 넘어가면 가로 스크롤
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.addView(content);
        container.addView(scroll);
    }

    private String formatTable(Cursor c) {
        int columnCount = c.getColumnCount();
        List<String[]> rows = new ArrayList<>();
        rows.add(c.getColumnNames());

        SimpleDateFormat dateFormat = new SimpleDateFormat("yy/MM/dd HH:mm:ss", Locale.US);
        while (rows.size() <= MAX_DEBUG_ROWS && c.moveToNext()) {
            String[] row = new String[columnCount];
            for (int i = 0; i < columnCount; i++) {
                if (c.isNull(i)) {
                    row[i] = "NULL";
                } else if ("run_date".equals(c.getColumnName(i))) {
                    row[i] = dateFormat.format(new Date(c.getLong(i)));
                } else {
                    row[i] = c.getString(i);
                }
            }
            rows.add(row);
        }

        // 열마다 가장 긴 값에 맞춰 너비 계산
        int[] widths = new int[columnCount];
        for (String[] row : rows) {
            for (int i = 0; i < columnCount; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < columnCount; i++) {
                sb.append(String.format(Locale.US, "%-" + (widths[i] + 2) + "s", row[i]));
            }
            sb.append('\n');
        }
        if (c.getCount() > MAX_DEBUG_ROWS) {
            sb.append("... (").append(c.getCount() - MAX_DEBUG_ROWS).append("행 생략)");
        }
        return sb.toString();
    }
}

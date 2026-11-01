package com.cookandroid.runcatch;

import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cookandroid.runcatch.backend.RunCatchDbHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordActivity extends BaseActivity {

    // 테이블당 최대 표시 행 수 (gps_point는 행이 많아질 수 있음)
    private static final int MAX_ROWS = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Edge-to-Edge + 레이아웃 + 인셋 처리
        setContentViewEdgeToEdge(R.layout.activity_record, R.id.record);
        // 하단 내비게이션 (현재 화면 = 기록)
        setupBottomNav(R.id.recordNavButton);

        // TODO(임시): DB 테이블 확인용 화면. 기록 화면 UI가 만들어지면 삭제
        LinearLayout container = findViewById(R.id.tableContainer);
        addTable(container, RunCatchDbHelper.TABLE_USER, "mail");
        addTable(container, RunCatchDbHelper.TABLE_USER_CHARACTER, "user_id, acquired_date");
        addTable(container, RunCatchDbHelper.TABLE_RUNNING_RECORD, "running_number DESC");
        addTable(container, RunCatchDbHelper.TABLE_GPS_POINT, "running_number DESC, sequence");
    }

    /** 테이블 이름과 내용을 고정폭 글꼴 표로 추가 */
    private void addTable(LinearLayout container, String table, String orderBy) {
        String body;
        int rowCount;
        try (Cursor c = RunCatchDbHelper.getInstance(this).getReadableDatabase().rawQuery(
                "SELECT * FROM " + table + " ORDER BY " + orderBy, null)) {
            rowCount = c.getCount();
            body = formatTable(c);
        }

        TextView title = new TextView(this);
        title.setText(table + " (" + rowCount + "행)");
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 48, 0, 16);
        container.addView(title);

        TextView content = new TextView(this);
        content.setText(body);
        content.setTypeface(Typeface.MONOSPACE);
        content.setTextSize(11);
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
        while (c.moveToNext() && rows.size() <= MAX_ROWS) {
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
        if (c.getCount() > MAX_ROWS) {
            sb.append("... (").append(c.getCount() - MAX_ROWS).append("행 생략)");
        }
        return sb.toString();
    }
}

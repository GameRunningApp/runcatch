package com.cookandroid.runcatch.backend;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 앱 로컬 SQLite DB.
 * 테이블: user, running_record, gps_point, user_character
 * 어느 화면에서든 RunCatchDbHelper.getInstance(this)로 같은 객체를 받아서 사용한다.
 */
public class RunCatchDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "runcatch.db";
    // 3: 도감 v2 캐릭터 id로 변경
    // 4: 처음 시작할 때 캐릭터 없이 시작 (토끼 기본 지급 제거)
    private static final int DB_VERSION = 4;

    // TODO(로그인): 닉네임 설정이 붙으면 로그인한 유저의 user_id로 바꾼다
    public static final String CURRENT_USER_ID = "aaa";

    public static final String TABLE_USER = "user";
    public static final String TABLE_RUNNING_RECORD = "running_record";
    public static final String TABLE_GPS_POINT = "gps_point";
    public static final String TABLE_USER_CHARACTER = "user_character";

    private static RunCatchDbHelper instance;

    public static synchronized RunCatchDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new RunCatchDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private RunCatchDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // SQLite는 FK 제약이 기본으로 꺼져 있어서 직접 켜야 함
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // user_id는 닉네임. running_record가 FK로 참조하므로 UNIQUE여야 함
        db.execSQL("CREATE TABLE " + TABLE_USER + " ("
                + "mail TEXT PRIMARY KEY, "
                + "user_id TEXT NOT NULL UNIQUE, "
                + "password TEXT, "
                + "character_id INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_RUNNING_RECORD + " ("
                + "running_number INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_id TEXT NOT NULL REFERENCES " + TABLE_USER + "(user_id) ON UPDATE CASCADE, "
                + "run_date INTEGER NOT NULL, "     // 러닝 시작 시각 (epoch ms)
                + "distance REAL, "                 // m
                + "duration REAL, "                 // 초
                + "pace REAL, "                     // 초/km
                + "avg_speed REAL, "                // km/h
                + "max_speed REAL)");               // km/h

        // sequence는 러닝마다 1부터 다시 매김
        db.execSQL("CREATE TABLE " + TABLE_GPS_POINT + " ("
                + "running_number INTEGER NOT NULL REFERENCES " + TABLE_RUNNING_RECORD
                + "(running_number) ON DELETE CASCADE, "
                + "sequence INTEGER NOT NULL, "
                + "latitude REAL, "
                + "longitude REAL, "
                + "PRIMARY KEY (running_number, sequence))");

        // 유저가 획득한 캐릭터. character_id는 RunCharacter.id
        db.execSQL("CREATE TABLE " + TABLE_USER_CHARACTER + " ("
                + "user_id TEXT NOT NULL REFERENCES " + TABLE_USER
                + "(user_id) ON UPDATE CASCADE ON DELETE CASCADE, "
                + "character_id INTEGER NOT NULL, "
                + "acquired_date INTEGER NOT NULL, "  // 획득 시각 (epoch ms)
                + "PRIMARY KEY (user_id, character_id))");

        // TODO(로그인): 회원가입이 생기기 전까지 쓰는 임시 유저.
        // 처음에는 획득한 캐릭터가 없으므로 대표 캐릭터(character_id)도 비워 둔다 (NULL)
        ContentValues user = new ContentValues();
        user.put("mail", "aaa@runcatch.com");
        user.put("user_id", CURRENT_USER_ID);
        user.put("password", "");
        db.insert(TABLE_USER, null, user);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 개발 단계라 기존 데이터를 지우고 새로 만든다
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER_CHARACTER);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_GPS_POINT);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RUNNING_RECORD);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER);
        onCreate(db);
    }

    // ===== 저장 =====

    /**
     * 러닝 기록과 GPS 경로를 한 트랜잭션으로 저장.
     *
     * @return 새로 부여된 running_number (실패 시 -1)
     */
    public long insertRun(RunRecord record, List<GpsPoint> points) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("user_id", record.userId);
            values.put("run_date", record.runDate);
            values.put("distance", record.distance);
            values.put("duration", record.duration);
            values.put("pace", record.pace);
            values.put("avg_speed", record.avgSpeed);
            values.put("max_speed", record.maxSpeed);
            long runningNumber = db.insertOrThrow(TABLE_RUNNING_RECORD, null, values);

            for (int i = 0; i < points.size(); i++) {
                ContentValues point = new ContentValues();
                point.put("running_number", runningNumber);
                point.put("sequence", i + 1);
                point.put("latitude", points.get(i).latitude);
                point.put("longitude", points.get(i).longitude);
                db.insertOrThrow(TABLE_GPS_POINT, null, point);
            }

            db.setTransactionSuccessful();
            return runningNumber;
        } catch (Exception e) {
            android.util.Log.e("RUNCATCH_DB", "러닝 기록 저장 실패", e);
            return -1;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 획득한 캐릭터 저장. 이미 가지고 있는 캐릭터면 저장하지 않는다.
     * 대표 캐릭터가 아직 없으면 처음 얻은 캐릭터를 대표 캐릭터로 정한다 (서버 규칙과 같음).
     *
     * @return 새로 저장했으면 true, 이미 가지고 있었으면 false
     */
    public boolean addUserCharacter(String userId, int characterId, long acquiredDate) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("character_id", characterId);
        values.put("acquired_date", acquiredDate);
        boolean added = db.insertWithOnConflict(
                TABLE_USER_CHARACTER, null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;

        if (added) {
            ContentValues main = new ContentValues();
            main.put("character_id", characterId);
            db.update(TABLE_USER, main, "user_id = ? AND character_id IS NULL",
                    new String[]{userId});
        }
        return added;
    }

    /**
     * 로그인 화면에서 입력한 이메일/비밀번호를 user 테이블에 저장.
     * TODO(로그인): 회원가입이 생기기 전까지는 임시 유저(userId) 행의 mail/password를 덮어쓴다
     *
     * @return 저장에 성공하면 true
     */
    public boolean updateUserLogin(String userId, String mail, String password) {
        ContentValues values = new ContentValues();
        values.put("mail", mail);
        values.put("password", password);
        try {
            return getWritableDatabase().update(
                    TABLE_USER, values, "user_id = ?", new String[]{userId}) > 0;
        } catch (Exception e) {
            android.util.Log.e("RUNCATCH_DB", "로그인 정보 저장 실패", e);
            return false;
        }
    }

    // ===== 조회 =====

    /** 유저가 획득한 캐릭터 id 목록 */
    public Set<Integer> getOwnedCharacterIds(String userId) {
        Set<Integer> ids = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT character_id FROM " + TABLE_USER_CHARACTER + " WHERE user_id = ?",
                new String[]{userId})) {
            while (c.moveToNext()) {
                ids.add(c.getInt(0));
            }
        }
        return ids;
    }

    /** 유저의 대표 캐릭터 id. 유저가 없거나 선택 안 했으면 -1 */
    public int getCharacterId(String userId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT character_id FROM " + TABLE_USER + " WHERE user_id = ?",
                new String[]{userId})) {
            return (c.moveToFirst() && !c.isNull(0)) ? c.getInt(0) : -1;
        }
    }

    /** 누적 거리 (m) */
    public float getTotalDistance(String userId) {
        return getDistanceSince(userId, 0);
    }

    /** fromMillis 이후 시작한 러닝들의 거리 합 (m) */
    public float getDistanceSince(String userId, long fromMillis) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT IFNULL(SUM(distance), 0) FROM " + TABLE_RUNNING_RECORD
                        + " WHERE user_id = ? AND run_date >= ?",
                new String[]{userId, String.valueOf(fromMillis)})) {
            return c.moveToFirst() ? c.getFloat(0) : 0f;
        }
    }

    private static final String RUN_COLUMNS =
            "running_number, user_id, run_date, distance, duration, pace, avg_speed, max_speed";

    /** 최근 러닝 limit건 (최신순) */
    public List<RunRecord> getRecentRuns(String userId, int limit) {
        return queryRuns("SELECT " + RUN_COLUMNS + " FROM " + TABLE_RUNNING_RECORD
                        + " WHERE user_id = ? ORDER BY run_date DESC LIMIT " + limit,
                new String[]{userId});
    }

    /** fromMillis 이후 시작한 러닝 전부 (최신순). fromMillis가 0이면 전체 기록 */
    public List<RunRecord> getRunsSince(String userId, long fromMillis) {
        return queryRuns("SELECT " + RUN_COLUMNS + " FROM " + TABLE_RUNNING_RECORD
                        + " WHERE user_id = ? AND run_date >= ? ORDER BY run_date DESC",
                new String[]{userId, String.valueOf(fromMillis)});
    }

    private List<RunRecord> queryRuns(String sql, String[] args) {
        List<RunRecord> runs = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) {
                runs.add(new RunRecord(c.getLong(0), c.getString(1), c.getLong(2), c.getFloat(3),
                        c.getFloat(4), c.getFloat(5), c.getFloat(6), c.getFloat(7)));
            }
        }
        return runs;
    }

    /** 모든 러닝 시작 시각 (최신순). 연속 러닝 일수 계산용 */
    public List<Long> getRunDates(String userId) {
        List<Long> dates = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT run_date FROM " + TABLE_RUNNING_RECORD
                        + " WHERE user_id = ? ORDER BY run_date DESC",
                new String[]{userId})) {
            while (c.moveToNext()) {
                dates.add(c.getLong(0));
            }
        }
        return dates;
    }
}

package com.cookandroid.runcatch.core.local;

import com.cookandroid.runcatch.core.domain.GpsPoint;
import com.cookandroid.runcatch.core.domain.PasswordHash;
import com.cookandroid.runcatch.core.domain.RunRecord;

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
 *
 *   CURRENT_USER_ID는 Session으로 옮김. DB 연결 작업에서 임시 유저 "aaa" 생성을 없애고
 *   회원가입 · 로그인 함수와 running_record.environment 컬럼(DB 버전 7) 추가
 */
public class RunCatchDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "runcatch.db";
    // 3: 도감 v2 캐릭터 id로 변경
    // 4: 처음 시작할 때 캐릭터 없이 시작 (토끼 기본 지급 제거)
    // 5: user_character에 exp(누적 경험치) 추가. 4에서 올라올 때는 기존 데이터를 유지한다
    // 6: my 페이지 확인용 임시 캐릭터 16~18 지급. 5에서 올라올 때는 기존 데이터를 유지한다
    // 7: running_record에 environment(러닝 환경) 추가, 임시 유저 "aaa" 대신 회원가입 사용.
    //    4~6에서 올라올 때는 기존 데이터를 유지한다
    private static final int DB_VERSION = 7;
    // my 페이지 캐릭터 상호작용을 위한 임시 캐릭터 (회원가입한 유저에게 지급)
    private static final int[] TEMP_CHARACTER_IDS = {15, 16, 17, 18};

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
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 더 높은 DB 버전의 브랜치로 실행한 뒤 낮은 버전의 브랜치로 돌아오면
        // 기본 동작은 DB를 열지 못해 로그인 · 기록 저장이 모두 실패한다.
        // 개발 단계라 버전을 올릴 때와 똑같이 지우고 새로 만든다.
        onUpgrade(db, oldVersion, newVersion);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // user_id는 닉네임. running_record가 FK로 참조하므로 UNIQUE여야 함
        // password는 PasswordHash.hash() 값 (평문 저장 안 함)
        db.execSQL("CREATE TABLE " + TABLE_USER + " ("
                + "mail TEXT PRIMARY KEY, "
                + "user_id TEXT NOT NULL UNIQUE, "
                + "password TEXT, "
                + "character_id INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_RUNNING_RECORD + " ("
                + "running_number INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_id TEXT NOT NULL REFERENCES " + TABLE_USER + "(user_id) ON UPDATE CASCADE, "
                + "run_date INTEGER NOT NULL, "     // 러닝 시작 시각 (epoch ms)
                + "environment TEXT, "              // CITY, PARK, FOREST, RIVER, LAKE (EnvironmentFeature)
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
                + "exp INTEGER NOT NULL DEFAULT 0, "  // 누적 경험치. 레벨은 ExpRules로 계산
                + "PRIMARY KEY (user_id, character_id))");
    }

    /** 임시 캐릭터를 유저에게 지급한다. 이미 있으면 건너뛴다 */
    private void insertTempCharacters(SQLiteDatabase db, String userId) {
        for (int characterId : TEMP_CHARACTER_IDS) {
            ContentValues owned = new ContentValues();
            owned.put("user_id", userId);
            owned.put("character_id", characterId);
            owned.put("acquired_date", System.currentTimeMillis());
            db.insertWithOnConflict(TABLE_USER_CHARACTER, null, owned,
                    SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 4 → 5 → 6 → 7: 컬럼만 추가하고 러닝 기록 · 캐릭터는 유지
        // (예전 임시 유저 "aaa"의 행도 남지만 로그인할 수 없어 쓰이지 않음)
        if (oldVersion >= 4 && oldVersion < newVersion) {
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE " + TABLE_USER_CHARACTER
                        + " ADD COLUMN exp INTEGER NOT NULL DEFAULT 0");
            }
            if (oldVersion < 7) {
                db.execSQL("ALTER TABLE " + TABLE_RUNNING_RECORD + " ADD COLUMN environment TEXT");
            }
            return;
        }
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
     * environment는 EnvironmentTally.finalEnvironment() 값 (모르면 null)
     *
     * @return 새로 부여된 running_number (실패 시 -1)
     */
    public long insertRun(RunRecord record, String environment, List<GpsPoint> points) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("user_id", record.userId);
            values.put("run_date", record.runDate);
            values.put("environment", environment);
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
     * 캐릭터에 경험치를 더한다.
     *
     * @return 더한 뒤의 누적 경험치. 가지고 있지 않은 캐릭터면 -1
     */
    public int addCharacterExp(String userId, int characterId, int amount) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE " + TABLE_USER_CHARACTER + " SET exp = exp + ? "
                        + "WHERE user_id = ? AND character_id = ?",
                new Object[]{amount, userId, characterId});
        return getCharacterExp(userId, characterId);
    }

    /**
     * 회원가입. 비밀번호는 해시로 저장한다.
     * TODO(임시): my 페이지 확인용 임시 캐릭터(15~18)를 지급하고 15번을 대표 캐릭터로 둔다
     *
     * @return 저장에 성공하면 true (이메일 · 닉네임 중복이면 false)
     */
    public boolean insertUser(String mail, String userId, String password) {
        return insertUserRow(mail, userId, PasswordHash.hash(password));
    }

    /**
     * 간편 로그인(카카오 · 구글)으로 처음 가입. 비밀번호가 없으므로 password는 NULL
     * → 이메일 · 비밀번호 로그인은 되지 않음 (findUserId에서 PasswordHash.matches가 false)
     *
     * @return 저장에 성공하면 true (이메일 · 닉네임 중복이면 false)
     */
    public boolean insertSocialUser(String mail, String userId) {
        return insertUserRow(mail, userId, null);
    }

    private boolean insertUserRow(String mail, String userId, String passwordHash) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues user = new ContentValues();
            user.put("mail", mail);
            user.put("user_id", userId);
            if (passwordHash == null) {
                user.putNull("password");
            } else {
                user.put("password", passwordHash);
            }
            user.put("character_id", TEMP_CHARACTER_IDS[0]);
            db.insertOrThrow(TABLE_USER, null, user);
            insertTempCharacters(db, userId);
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            android.util.Log.e("RUNCATCH_DB", "회원가입 저장 실패", e);
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public boolean isMailTaken(String mail) {
        return exists("SELECT 1 FROM " + TABLE_USER + " WHERE mail = ?", mail);
    }

    public boolean isUserIdTaken(String userId) {
        return exists("SELECT 1 FROM " + TABLE_USER + " WHERE user_id = ?", userId);
    }

    private boolean exists(String sql, String arg) {
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{arg})) {
            return c.moveToFirst();
        }
    }

    /**
     * 로그인 확인.
     *
     * @return 이메일과 비밀번호가 맞으면 그 유저의 user_id, 틀리면 null
     */
    public String findUserId(String mail, String password) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT user_id, password FROM " + TABLE_USER + " WHERE mail = ?",
                new String[]{mail})) {
            if (c.moveToFirst() && PasswordHash.matches(password, c.getString(1))) {
                return c.getString(0);
            }
            return null;
        }
    }

    /**
     * 이메일로 user_id 찾기 (간편 로그인 · 비밀번호 찾기)
     *
     * @return 가입된 이메일이면 user_id, 없으면 null
     */
    public String findUserIdByMail(String mail) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT user_id FROM " + TABLE_USER + " WHERE mail = ?", new String[]{mail})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    /**
     * 비밀번호 변경 (비밀번호 찾기). 새 비밀번호도 해시로 저장한다.
     *
     * @return 그 이메일의 유저가 있어서 바뀌었으면 true
     */
    public boolean updatePassword(String mail, String newPassword) {
        ContentValues values = new ContentValues();
        values.put("password", PasswordHash.hash(newPassword));
        return getWritableDatabase().update(TABLE_USER, values, "mail = ?", new String[]{mail}) > 0;
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

    /** 캐릭터의 누적 경험치. 가지고 있지 않은 캐릭터면 -1 */
    public int getCharacterExp(String userId, int characterId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT exp FROM " + TABLE_USER_CHARACTER + " WHERE user_id = ? AND character_id = ?",
                new String[]{userId, String.valueOf(characterId)})) {
            return c.moveToFirst() ? c.getInt(0) : -1;
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

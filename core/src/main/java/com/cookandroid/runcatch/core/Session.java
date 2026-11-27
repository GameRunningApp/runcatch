package com.cookandroid.runcatch.core;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 지금 로그인한 유저 정보.
 * 화면은 유저 ID를 여기서만 받는다 (DB 클래스가 로그인 상태까지 맡지 않도록 분리)
 * 앱 프로세스가 종료된 뒤 화면이 복원되어도 유지되도록 SharedPreferences에 저장
 *   DB 연결 작업에서 로그인한 유저를 SharedPreferences에 저장하도록 바꿈
 */
public final class Session {

    private static final String PREFS = "runcatch_session";
    private static final String KEY_USER_ID = "user_id";

    private Session() {
    }

    // 로그인 성공 시 호출. userId는 user 테이블의 user_id (닉네임)
    public static void login(Context context, String userId) {
        prefs(context).edit().putString(KEY_USER_ID, userId).apply();
    }

    // 로그인한 유저의 user_id. 로그인 전이면 ""
    public static String userId(Context context) {
        return prefs(context).getString(KEY_USER_ID, "");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

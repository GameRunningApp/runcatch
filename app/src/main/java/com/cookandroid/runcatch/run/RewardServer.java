package com.cookandroid.runcatch.run;

import com.cookandroid.runcatch.core.domain.RunCharacter;
import com.cookandroid.runcatch.core.domain.RunRecord;
import com.cookandroid.runcatch.remote.RunCatchApi;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 캐릭터 획득 여부를 서버에 묻는 부분.
 * 러닝 기록을 서버(POST /runs)에 저장하면 서버가 획득 조건으로 판단해서
 * 이번에 새로 획득한 캐릭터 목록(new_characters)을 돌려준다.
 * 이어서 서버의 보유 캐릭터 전체(GET /users/{id}/characters)도 함께 돌려준다.
 *
 *   "2km 이상이면 수달 · 다람쥐" 하드코딩을 서버 POST /runs 호출로 바꿈
 */
public class RewardServer {

    private static final String TAG = "RewardServer";
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();

    /** 서버가 돌려주는 획득 캐릭터 1건 */
    public static class Reward {
        public final RunCharacter character;
        public final String conditionText;

        public Reward(RunCharacter character, String conditionText) {
            this.character = character;
            this.conditionText = conditionText;
        }
    }

    public interface Callback {
        /**
         * 메인 스레드에서 호출
         * @param rewards 획득한 캐릭터 (없으면 빈 리스트). 서버에 연결하지 못했으면 null
         */
        void onResult(List<Reward> rewards);
    }

    public static void requestRewards(RunRecord record, String finalEnvironment, Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        NETWORK.execute(() -> {
            List<Reward> rewards;
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", record.userId);
                body.put("run_date", record.runDate);
                // 서버는 CITY, PARK, FOREST, RIVER, LAKE만 받음 (EnvironmentFeature 상수와 같음)
                if (finalEnvironment != null) body.put("environment", finalEnvironment);
                body.put("distance", record.distance);
                body.put("duration", record.duration);
                body.put("pace", record.pace);
                body.put("avg_speed", record.avgSpeed);
                body.put("max_speed", record.maxSpeed);

                JSONObject run = RunCatchApi.post("/runs", body);
                Map<Integer, Reward> byId = new LinkedHashMap<>();
                addRewards(byId, run.optJSONArray("new_characters"));
                try {
                    addRewards(byId, RunCatchApi.getArray(
                            "/users/" + RunCatchApi.encode(record.userId) + "/characters"));
                } catch (Exception e) {
                    // 러닝 저장은 성공했으므로 이번 획득분만 사용. 나머지는 다음 러닝 때 맞춰짐
                    Log.e(TAG, "서버 보유 캐릭터 조회 실패", e);
                }
                rewards = new ArrayList<>(byId.values());
            } catch (Exception e) {
                Log.e(TAG, "러닝 기록 서버 저장 · 캐릭터 판정 실패", e);
                rewards = null;
            }
            List<Reward> result = rewards;
            main.post(() -> callback.onResult(result));
        });
    }

    // 서버 캐릭터 id == RunCharacter.id (server/characters.py)
    private static void addRewards(Map<Integer, Reward> byId, JSONArray characters) {
        if (characters == null) return;
        for (int i = 0; i < characters.length(); i++) {
            JSONObject item = characters.optJSONObject(i);
            RunCharacter character = item == null ? null
                    : RunCharacter.findById(item.optInt("character_id", -1));
            if (character == null || byId.containsKey(character.id)) continue;
            byId.put(character.id, new Reward(character, "획득 조건 : " + character.condition));
        }
    }
}

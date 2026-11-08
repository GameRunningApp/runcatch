//package com.cookandroid.runcatch;
//
//import com.cookandroid.runcatch.backend.RunCharacter;
//import com.cookandroid.runcatch.backend.RunRecord;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//
///**
// * 캐릭터 획득 여부를 서버에 묻는 부분.
// * 러닝 기록을 넘기면 서버가 복합 조건으로 판단해서 이번에 획득한 캐릭터 목록을 돌려준다.
// *
// * TODO(백엔드): 지금은 서버가 없어서 결과를 하드코딩한다.
// *               서버 통신은 비동기라서 결과를 Callback으로 돌려주는 형태로 만들어 두었다.
// */
//public class RewardServer {
//
//    /** 서버가 돌려주는 획득 캐릭터 1건 */
//    public static class Reward {
//        public final RunCharacter character;
//        public final String conditionText;
//
//        public Reward(RunCharacter character, String conditionText) {
//            this.character = character;
//            this.conditionText = conditionText;
//        }
//    }
//
//    public interface Callback {
//        /** 획득한 캐릭터가 없으면 빈 리스트 */
//        void onResult(List<Reward> rewards);
//    }
//
//    public static void requestRewards(RunRecord record,  Map<String, Long> environmentTimes, Callback callback) {
//        List<Reward> rewards = new ArrayList<>();
//
//        // 환경별 체류 시간 로그
//        if (environmentTimes != null) {
//            for (Map.Entry<String, Long> entry
//                    : environmentTimes.entrySet()) {
//
//                android.util.Log.d(
//                        "REWARD_SERVER",
//                        entry.getKey() + " 체류 시간: "
//                                + entry.getValue() / 1000.0 + "초"
//                );
//            }
//        }
//
//        // 임시 하드코딩: 2Km 이상 달리면 캐릭터 2개 획득 (여러 개 획득 화면 확인용)
//        if (record.distance >= 2000) {
//            for (int id : new int[]{1, 3}) {  // 수달, 다람쥐
//                RunCharacter character = RunCharacter.findById(id);
//                rewards.add(new Reward(character, "획득 조건 : " + character.condition));
//            }
//        }
//
//        callback.onResult(rewards);
//    }
//}


package com.cookandroid.runcatch;

import android.util.Log;

import com.cookandroid.runcatch.backend.RunCharacter;
import com.cookandroid.runcatch.backend.RunRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RewardServer {

    private static final String TAG = "REWARD_SERVER";

    // 서버가 돌려주는 획득 캐릭터 1건
    public static class Reward {
        public final RunCharacter character;
        public final String conditionText;

        public Reward(RunCharacter character, String conditionText) {
            this.character = character;
            this.conditionText = conditionText;
        }
    }

    public interface Callback {
        void onResult(List<Reward> rewards);
    }

    public static void requestRewards(
            RunRecord record,
            Map<String, Long> environmentTimes,
            Callback callback
    ) {
        List<Reward> rewards = new ArrayList<>();

        if (environmentTimes == null) {
            Log.d(TAG, "환경 정보가 없습니다.");
            callback.onResult(rewards);
            return;
        }

        // 환경별 체류 시간 확인
        long forestTime = environmentTimes.getOrDefault("숲", 0L);
        long parkTime = environmentTimes.getOrDefault("공원", 0L);
        long riverTime = environmentTimes.getOrDefault("하천", 0L);

        Log.d(TAG, "숲 체류 시간: " + forestTime / 1000.0 + "초");
        Log.d(TAG, "공원 체류 시간: " + parkTime / 1000.0 + "초");
        Log.d(TAG, "하천 체류 시간: " + riverTime / 1000.0 + "초");

        // 가장 오래 머문 환경 선택
        // 동률이면 숲 > 공원 > 하천 순서
        long maxTime = 0L;
        int rewardCharacterId = -1;
        String selectedEnvironment = "";

        if (forestTime > maxTime) {
            maxTime = forestTime;
            rewardCharacterId = 4;
            selectedEnvironment = "숲";
        }

        if (parkTime > maxTime) {
            maxTime = parkTime;
            rewardCharacterId = 3;
            selectedEnvironment = "공원";
        }

        if (riverTime > maxTime) {
            maxTime = riverTime;
            rewardCharacterId = 1;
            selectedEnvironment = "하천";
        }

        // 모든 환경의 체류 시간이 0초라면 보상 없음
        if (rewardCharacterId == -1) {
            Log.d(TAG, "환경 체류 시간이 없어 캐릭터를 지급하지 않습니다.");
            callback.onResult(rewards);
            return;
        }

        // 선택된 환경에 해당하는 캐릭터 지급
        RunCharacter character = RunCharacter.findById(rewardCharacterId);

        if (character != null) {
            String conditionText =
                    "이번 러닝에서 가장 오래 머문 환경: " + selectedEnvironment;

            rewards.add(new Reward(character, conditionText));

            Log.d(TAG, "선택된 환경: " + selectedEnvironment);
            Log.d(TAG, "지급 캐릭터: " + character.name
                    + " (ID: " + character.id + ")");
        }

        callback.onResult(rewards);
    }
}


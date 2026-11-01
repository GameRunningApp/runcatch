package com.cookandroid.runcatch;

import com.cookandroid.runcatch.backend.RunRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * 캐릭터 획득 여부를 서버에 묻는 부분.
 * 러닝 기록을 넘기면 서버가 복합 조건으로 판단해서 이번에 획득한 캐릭터 목록을 돌려준다.
 *
 * TODO(백엔드): 지금은 서버가 없어서 결과를 하드코딩한다.
 *               서버 통신은 비동기라서 결과를 Callback으로 돌려주는 형태로 만들어 두었다.
 */
public class RewardServer {

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
        /** 획득한 캐릭터가 없으면 빈 리스트 */
        void onResult(List<Reward> rewards);
    }

    public static void requestRewards(RunRecord record, Callback callback) {
        List<Reward> rewards = new ArrayList<>();

        // 임시 하드코딩: 2Km 이상 달리면 캐릭터 2개 획득 (여러 개 획득 화면 확인용)
        if (record.distance >= 2000) {
            for (int id : new int[]{1, 3}) {  // 수달, 다람쥐
                RunCharacter character = RunCharacter.findById(id);
                rewards.add(new Reward(character, "획득 조건 : " + character.condition));
            }
        }

        callback.onResult(rewards);
    }
}

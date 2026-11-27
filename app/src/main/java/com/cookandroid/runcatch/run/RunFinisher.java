package com.cookandroid.runcatch.run;

import com.cookandroid.runcatch.core.domain.GpsPoint;
import com.cookandroid.runcatch.core.domain.RunRecord;
import com.cookandroid.runcatch.core.domain.RunStreak;
import com.cookandroid.runcatch.core.local.RunCatchDbHelper;

import android.content.Context;


import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * 러닝 종료 절차: 기록 저장 -> 보상 요청 -> 획득 캐릭터 저장 -> 경험치 적용.
 * RunningActivity에서 분리한 부분으로, 화면 표시와 화면 이동은 호출한 화면이 맡음.
 *
 * RunningActivity의 saveRunRecord(), saveRewards(), applyExp()를 옮겨 왔음
 *   (화면은 표시와 화면 이동만, 저장 -> 보상 -> 경험치 순서는 여기서)
 */
public class RunFinisher {

    public static class Result {
        public final RunRecord record;
        // 이번에 새로 저장된 캐릭터 (이미 가진 캐릭터는 빠짐)
        public final List<RewardServer.Reward> newRewards;
        public final ExpGain expGain;
        // 서버에 연결하지 못해 캐릭터 획득을 확인하지 못했으면 true (로컬 저장 · 경험치는 그대로 진행)
        public final boolean rewardCheckFailed;

        Result(RunRecord record, List<RewardServer.Reward> newRewards, ExpGain expGain,
               boolean rewardCheckFailed) {
            this.record = record;
            this.newRewards = newRewards;
            this.expGain = expGain;
            this.rewardCheckFailed = rewardCheckFailed;
        }
    }

    public interface Callback {
        void onDone(Result result);
    }

    private final RunCatchDbHelper db;

    public RunFinisher(Context context) {
        db = RunCatchDbHelper.getInstance(context);
    }

    /**
     * running_record + gps_point 저장. environment는 러닝 환경 (모르면 null)
     * @return 저장된 running_number가 담긴 기록. 저장에 실패하면 null
     */
    public RunRecord save(RunRecord draft, String environment, List<GpsPoint> points) {
        long runningNumber = db.insertRun(draft, environment, points);
        if (runningNumber == -1) return null;
        return new RunRecord(runningNumber, draft.userId, draft.runDate, draft.distance,
                draft.duration, draft.pace, draft.avgSpeed, draft.maxSpeed);
    }

    /**
     * 저장된 기록을 서버에 보내 보상을 받고, 결과를 로컬 DB에 반영한 뒤 callback으로
     * callback은 메인 스레드에서 호출
     */
    public void finish(RunRecord saved, String finalEnvironment, Callback callback) {
        RewardServer.requestRewards(saved, finalEnvironment, rewards -> {
            boolean failed = rewards == null;
            List<RewardServer.Reward> newRewards =
                    failed ? new ArrayList<>() : saveRewards(saved.userId, rewards);
            // 캐릭터 저장 뒤에 해야 첫 캐릭터도 경험치를 받음
            ExpGain expGain = applyExp(saved);
            callback.onDone(new Result(saved, newRewards, expGain, failed));
        });
    }

    // 획득한 캐릭터를 user_character에 저장. 새로 저장된 것만 돌려줌
    private List<RewardServer.Reward> saveRewards(String userId, List<RewardServer.Reward> rewards) {
        long now = System.currentTimeMillis();
        List<RewardServer.Reward> newRewards = new ArrayList<>();
        for (RewardServer.Reward reward : rewards) {
            if (db.addUserCharacter(userId, reward.character.id, now)) {
                newRewards.add(reward);
            }
        }
        return newRewards;
    }

    /**
     * 이번 러닝 경험치를 계산해서 대표 캐릭터에 더한다.
     * 대표 캐릭터가 없으면?
     */
    private ExpGain applyExp(RunRecord record) {
        String userId = record.userId;
        int streakDays = RunStreak.countDays(
                db.getRunDates(userId), record.runDate, TimeZone.getDefault());
        // TODO(개인 목표): 목표 설정 기능이 생기면 달성 여부를 넘긴다 (+10)
        boolean goalAchieved = false;
        ExpGain gain = ExpGain.calculate(record.distance, streakDays, goalAchieved);

        int characterId = db.getCharacterId(userId);
        if (characterId == -1) return gain;
        int before = db.getCharacterExp(userId, characterId);
        if (before == -1) return gain;
        int after = db.addCharacterExp(userId, characterId, gain.total());
        return gain.appliedTo(characterId, before, after);
    }
}

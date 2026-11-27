package com.cookandroid.runcatch.environment;

import java.util.Set;

/**
 * 러닝 중 GPS 좌표마다 걸린 환경을 세고, 러닝의 최종 환경을 정한다.
 * RunningService에 있던 환경별 좌표 수 세기, getFinalEnvironment(),
 *   getEnvironmentCountSummary()를 옮겨 왔음 (서비스는 GPS만 맡도록 분리)
 */
public class EnvironmentTally {

    private int parkCount;
    private int forestCount;
    private int riverCount;
    private int lakeCount;

    public void reset() {
        parkCount = 0;
        forestCount = 0;
        riverCount = 0;
        lakeCount = 0;
    }

    // 좌표 하나가 걸린 환경들 (EnvironmentMatcher.findMatchedCategories 결과)
    public void add(Set<String> matchedCategories) {
        if (matchedCategories.contains(EnvironmentFeature.PARK)) parkCount++;
        if (matchedCategories.contains(EnvironmentFeature.FOREST)) forestCount++;
        if (matchedCategories.contains(EnvironmentFeature.RIVER)) riverCount++;
        if (matchedCategories.contains(EnvironmentFeature.LAKE)) lakeCount++;
    }

    // 최다 집계 환경. 동률은 PARK, FOREST, RIVER, LAKE 순. 하나도 없으면 CITY
    public String finalEnvironment() {
        if (parkCount == 0 && forestCount == 0 && riverCount == 0 && lakeCount == 0) {
            return EnvironmentFeature.CITY;
        }
        int maxCount = Math.max(Math.max(parkCount, forestCount), Math.max(riverCount, lakeCount));
        if (parkCount == maxCount) return EnvironmentFeature.PARK;
        if (forestCount == maxCount) return EnvironmentFeature.FOREST;
        if (riverCount == maxCount) return EnvironmentFeature.RIVER;
        return EnvironmentFeature.LAKE;
    }

    // 로그 확인용
    public String summary() {
        return "공원: " + parkCount + ", 숲: " + forestCount
                + ", 하천: " + riverCount + ", 호수: " + lakeCount;
    }
}

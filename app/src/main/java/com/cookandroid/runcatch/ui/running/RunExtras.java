package com.cookandroid.runcatch.ui.running;

/**
 * 러닝 화면들(Running → CharacterReward → Result)이 Intent로 주고받는 값의 키.
 * 한쪽만 바꾸면 오류 없이 값이 0이나 null이 되므로 문자열을 직접 쓰지 말고 이 상수를 쓴다.
 *
 * 따로 적혀 있던 Intent 키 문자열을 모았음
 */
public final class RunExtras {

    public static final String DISTANCE = "distance";                  // float, km
    public static final String ELAPSED_TIME = "elapsedTime";           // long, ms
    public static final String FINAL_ENVIRONMENT = "finalEnvironment"; // String, EnvironmentFeature.PARK 등
    public static final String ROUTE_LATITUDE = "routeLatitude";       // double[]
    public static final String ROUTE_LONGITUDE = "routeLongitude";     // double[]
    // 알림에서 열렸는지는 RunningService.EXTRA_FROM_NOTIFICATION (서비스가 넣는 값)

    private RunExtras() {
    }
}

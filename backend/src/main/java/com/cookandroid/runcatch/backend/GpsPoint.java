package com.cookandroid.runcatch.backend;

/**
 * gps_point 테이블 한 행의 좌표.
 * backend 모듈이 지도 라이브러리(MapLibre LatLng)에 의존하지 않도록 따로 둔다.
 */
public class GpsPoint {

    public final double latitude;
    public final double longitude;

    public GpsPoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}


package com.cookandroid.runcatch;

import java.util.List;

public class EnvironmentFeature {

    // OSM 객체 종류 (way, relation 등)
    private String osmType;

    // OSM 객체 ID
    private long osmId;

    // 환경 종류 (PARK, FOREST, RIVER, LAKE)
    private String category;

    // 환경 이름
    private String name;

    // 기존 중앙 좌표
    private double latitude;
    private double longitude;

    // 하천의 전체 경로 좌표
    // 각 배열은 [위도, 경도] 순서
    private List<double[]> geometry;

    // 기존 생성자
    // 공원, 숲, 호수 등 중앙 좌표만 사용하는 환경
    public EnvironmentFeature(
            String osmType,
            long osmId,
            String category,
            String name,
            double latitude,
            double longitude
    ) {
        this(
                osmType,
                osmId,
                category,
                name,
                latitude,
                longitude,
                null
        );
    }

    // 전체 생성자
    // 하천의 geometry 좌표까지 저장할 수 있음
    public EnvironmentFeature(
            String osmType,
            long osmId,
            String category,
            String name,
            double latitude,
            double longitude,
            List<double[]> geometry
    ) {
        this.osmType = osmType;
        this.osmId = osmId;
        this.category = category;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.geometry = geometry;
    }

    public String getOsmType() {
        return osmType;
    }

    public long getOsmId() {
        return osmId;
    }

    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public List<double[]> getGeometry() {
        return geometry;
    }
}

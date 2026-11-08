
package com.cookandroid.runcatch;

import java.util.ArrayList;

public class OsmEnvironment {

    // 환경 종류 (공원, 숲, 하천 등)
    private String type;

    // 환경 이름
    private String name;

    // 기존 방식의 좌표 리스트 (way 및 하천 호환용)
    private ArrayList<OsmCoordinate> coordinates;

    // multipolygon의 바깥 경계 목록
    private ArrayList<ArrayList<OsmCoordinate>> outerRings;

    // multipolygon의 내부 제외 영역 목록
    private ArrayList<ArrayList<OsmCoordinate>> innerRings;

    // 기존 생성자
    public OsmEnvironment(String type, String name,
                          ArrayList<OsmCoordinate> coordinates) {
        this.type = type;
        this.name = name;
        this.coordinates = coordinates;

        this.outerRings = new ArrayList<>();
        this.innerRings = new ArrayList<>();
    }

    // multipolygon용 생성자
    public OsmEnvironment(
            String type,
            String name,
            ArrayList<ArrayList<OsmCoordinate>> outerRings,
            ArrayList<ArrayList<OsmCoordinate>> innerRings
    ) {
        this.type = type;
        this.name = name;

        this.outerRings = outerRings;
        this.innerRings = innerRings;

        this.coordinates = new ArrayList<>();

        // 기존 코드와의 호환을 위해 첫 번째 outer 경계를 저장
        if (outerRings != null && !outerRings.isEmpty()) {
            this.coordinates = outerRings.get(0);
        }
    }

    // 환경 종류 반환
    public String getType() {
        return type;
    }

    // 환경 이름 반환
    public String getName() {
        return name;
    }

    // 기존 좌표 리스트 반환
    public ArrayList<OsmCoordinate> getCoordinates() {
        return coordinates;
    }

    // 바깥 경계 목록 반환
    public ArrayList<ArrayList<OsmCoordinate>> getOuterRings() {
        return outerRings;
    }

    // 내부 제외 영역 목록 반환
    public ArrayList<ArrayList<OsmCoordinate>> getInnerRings() {
        return innerRings;
    }
}


package com.cookandroid.runcatch;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnvironmentAnalyzer {

    // 하천 러닝으로 인정할 거리 기준: 30m
    private static final double RIVER_DISTANCE_THRESHOLD = 30.0;

    // 지구 반지름(m)
    private static final double EARTH_RADIUS = 6371000.0;

    // GPS 구간을 5m 간격으로 나누어 검사
    private static final double SAMPLE_INTERVAL = 5.0;

    // ==========================================
    // 1. 환경별 체류 시간 계산
    // ==========================================

    /**
     * 환경별 체류 시간을 계산한다.
     *
     * route: GPS 좌표 목록
     * timestamps: 각 GPS 좌표의 러닝 경과 시간(ms)
     * breakIndices: 일시정지 후 재개한 구간의 시작 인덱스
     * environments: OSM 환경 객체 목록
     *
     * 반환값: 공원, 숲, 하천별 체류 시간(ms)
     */
    public static Map<String, Long> calculateEnvironmentTimes(
            List<OsmCoordinate> route,
            List<Long> timestamps,
            List<Integer> breakIndices,
            List<OsmEnvironment> environments) {

        Map<String, Long> result = new HashMap<>();

        result.put("공원", 0L);
        result.put("숲", 0L);
        result.put("하천", 0L);

        if (route == null || timestamps == null
                || environments == null
                || route.size() < 2
                || route.size() != timestamps.size()) {
            return result;
        }

        for (int i = 0; i < route.size() - 1; i++) {

            // 일시정지 후 재개한 지점은 이전 좌표와 연결하지 않음
            if (breakIndices != null
                    && breakIndices.contains(i + 1)) {
                continue;
            }

            long elapsed = timestamps.get(i + 1) - timestamps.get(i);

            // 비정상적인 GPS 시간 간격은 제외
            if (elapsed <= 0 || elapsed > 10000) {
                continue;
            }

            OsmCoordinate start = route.get(i);
            OsmCoordinate end = route.get(i + 1);

            // GPS 구간의 중간 지점
            OsmCoordinate middle = new OsmCoordinate(
                    (start.getLatitude() + end.getLatitude()) / 2.0,
                    (start.getLongitude() + end.getLongitude()) / 2.0
            );

            String environmentType =
                    findEnvironmentType(middle, environments);

            if (environmentType != null) {

                result.put(
                        environmentType,
                        result.get(environmentType) + elapsed
                );
            }
        }

        return result;
    }

    // ==========================================
    // 2. 특정 GPS 좌표의 환경 판별
    // ==========================================

    private static String findEnvironmentType(
            OsmCoordinate point,
            List<OsmEnvironment> environments) {

        boolean inPark = false;
        boolean inForest = false;
        boolean nearRiver = false;

        for (OsmEnvironment environment : environments) {

            String type = environment.getType();

            // 공원과 숲은 다각형 내부인지 확인
            if ("공원".equals(type) || "숲".equals(type)) {

                boolean inside = isInsideEnvironment(
                        point,
                        environment
                );

                if (inside) {
                    if ("공원".equals(type)) {
                        inPark = true;
                    } else {
                        inForest = true;
                    }
                }
            }

            // 하천은 하천 선분으로부터 30m 이내인지 확인
            else if ("하천".equals(type)) {

                List<OsmCoordinate> river =
                        environment.getCoordinates();

                if (river == null || river.size() < 2) {
                    continue;
                }

                for (int i = 0; i < river.size() - 1; i++) {

                    double distance = distanceToLineSegment(
                            point,
                            river.get(i),
                            river.get(i + 1)
                    );

                    if (distance <= RIVER_DISTANCE_THRESHOLD) {
                        nearRiver = true;
                        break;
                    }
                }
            }
        }

        // 환경이 겹치면 우선순위에 따라 하나만 선택
        if (inForest) return "숲";
        if (inPark) return "공원";
        if (nearRiver) return "하천";

        return null;
    }

    // ==========================================
    // 3. 공원 및 숲 내부 판별
    // ==========================================

    private static boolean isInsideEnvironment(
            OsmCoordinate point,
            OsmEnvironment environment) {

        List<ArrayList<OsmCoordinate>> outerRings =
                environment.getOuterRings();

        List<ArrayList<OsmCoordinate>> innerRings =
                environment.getInnerRings();

        // relation 방식
        if (outerRings != null && !outerRings.isEmpty()) {

            boolean insideOuter = false;

            for (List<OsmCoordinate> outer : outerRings) {

                if (isInsidePolygon(point, outer)) {
                    insideOuter = true;
                    break;
                }
            }

            if (!insideOuter) {
                return false;
            }

            // inner 경계 안쪽은 제외
            if (innerRings != null) {

                for (List<OsmCoordinate> inner : innerRings) {

                    if (isInsidePolygon(point, inner)) {
                        return false;
                    }
                }
            }

            return true;
        }

        // 기존 way 방식
        List<OsmCoordinate> polygon =
                environment.getCoordinates();

        return polygon != null
                && polygon.size() >= 4
                && isInsidePolygon(point, polygon);
    }

    // ==========================================
    // 4. 하천 러닝 거리 계산
    // ==========================================

    public static double calculateRiverDistance(
            List<OsmCoordinate> route,
            List<OsmEnvironment> environments) {

        double riverDistance = 0.0;

        if (route == null || route.size() < 2
                || environments == null) {
            return 0.0;
        }

        for (int i = 0; i < route.size() - 1; i++) {

            OsmCoordinate start = route.get(i);
            OsmCoordinate end = route.get(i + 1);

            double segmentDistance = calculateDistance(start, end);

            Log.d(
                    "RIVER_DISTANCE",
                    "GPS 구간 " + i
                            + " / 이동 거리: "
                            + String.format("%.2f", segmentDistance)
                            + "m"
            );

            if (segmentDistance == 0) {
                continue;
            }

            boolean nearRiver = false;

            for (OsmEnvironment environment : environments) {

                if (!"하천".equals(environment.getType())) {
                    continue;
                }

                List<OsmCoordinate> riverCoordinates =
                        environment.getCoordinates();

                if (riverCoordinates == null
                        || riverCoordinates.size() < 2) {
                    continue;
                }

                if (isNearRiver(
                        start,
                        end,
                        riverCoordinates,
                        segmentDistance)) {

                    nearRiver = true;
                    break;
                }
            }

            if (nearRiver) {

                riverDistance += segmentDistance;

                Log.d(
                        "RIVER_DISTANCE",
                        "하천 러닝 인정 구간 " + i
                                + " / 인정 거리: "
                                + String.format("%.2f", segmentDistance)
                                + "m"
                );
            }
        }

        return riverDistance;
    }

    // GPS 구간이 하천에서 일정 거리 이내인지 검사
    private static boolean isNearRiver(
            OsmCoordinate start,
            OsmCoordinate end,
            List<OsmCoordinate> river,
            double segmentDistance) {

        int sampleCount = Math.max(
                1,
                (int) Math.ceil(segmentDistance / SAMPLE_INTERVAL)
        );

        for (int i = 0; i <= sampleCount; i++) {

            double ratio = (double) i / sampleCount;

            double latitude = start.getLatitude()
                    + (end.getLatitude() - start.getLatitude()) * ratio;

            double longitude = start.getLongitude()
                    + (end.getLongitude() - start.getLongitude()) * ratio;

            OsmCoordinate sample =
                    new OsmCoordinate(latitude, longitude);

            for (int j = 0; j < river.size() - 1; j++) {

                OsmCoordinate riverStart = river.get(j);
                OsmCoordinate riverEnd = river.get(j + 1);

                double distance = distanceToLineSegment(
                        sample,
                        riverStart,
                        riverEnd
                );

                if (distance <= RIVER_DISTANCE_THRESHOLD) {
                    return true;
                }
            }
        }

        return false;
    }

    // ==========================================
    // 5. 두 GPS 좌표 사이의 실제 거리
    // ==========================================

    private static double calculateDistance(
            OsmCoordinate a,
            OsmCoordinate b) {

        double lat1 = Math.toRadians(a.getLatitude());
        double lat2 = Math.toRadians(b.getLatitude());

        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(
                b.getLongitude() - a.getLongitude()
        );

        double h = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        return 2 * EARTH_RADIUS
                * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    // ==========================================
    // 6. 한 점에서 선분까지의 최단 거리
    // ==========================================

    private static double distanceToLineSegment(
            OsmCoordinate point,
            OsmCoordinate lineStart,
            OsmCoordinate lineEnd) {

        double referenceLatitude =
                Math.toRadians(point.getLatitude());

        double px = 0;
        double py = 0;

        double ax = Math.toRadians(
                lineStart.getLongitude() - point.getLongitude())
                * EARTH_RADIUS * Math.cos(referenceLatitude);

        double ay = Math.toRadians(
                lineStart.getLatitude() - point.getLatitude())
                * EARTH_RADIUS;

        double bx = Math.toRadians(
                lineEnd.getLongitude() - point.getLongitude())
                * EARTH_RADIUS * Math.cos(referenceLatitude);

        double by = Math.toRadians(
                lineEnd.getLatitude() - point.getLatitude())
                * EARTH_RADIUS;

        double dx = bx - ax;
        double dy = by - ay;

        double lengthSquared = dx * dx + dy * dy;

        if (lengthSquared == 0) {
            return Math.sqrt(ax * ax + ay * ay);
        }

        double t = ((px - ax) * dx + (py - ay) * dy)
                / lengthSquared;

        t = Math.max(0, Math.min(1, t));

        double closestX = ax + t * dx;
        double closestY = ay + t * dy;

        double diffX = px - closestX;
        double diffY = py - closestY;

        return Math.sqrt(diffX * diffX + diffY * diffY);
    }

    // ==========================================
    // 7. 공원 및 숲 러닝 거리 계산
    // ==========================================

    public static double calculateParkDistance(
            List<OsmCoordinate> route,
            List<OsmEnvironment> environments) {

        return calculateAreaDistance(route, environments, "공원");
    }

    public static double calculateForestDistance(
            List<OsmCoordinate> route,
            List<OsmEnvironment> environments) {

        return calculateAreaDistance(route, environments, "숲");
    }

    // 공원 또는 숲 내부에서 달린 거리 계산
    private static double calculateAreaDistance(
            List<OsmCoordinate> route,
            List<OsmEnvironment> environments,
            String targetType) {

        double totalDistance = 0.0;

        if (route == null || route.size() < 2
                || environments == null) {
            return 0.0;
        }

        // GPS 경로의 좌표 범위 계산
        double routeMinLat = Double.MAX_VALUE;
        double routeMaxLat = -Double.MAX_VALUE;
        double routeMinLon = Double.MAX_VALUE;
        double routeMaxLon = -Double.MAX_VALUE;

        for (OsmCoordinate point : route) {

            routeMinLat = Math.min(routeMinLat, point.getLatitude());
            routeMaxLat = Math.max(routeMaxLat, point.getLatitude());

            routeMinLon = Math.min(routeMinLon, point.getLongitude());
            routeMaxLon = Math.max(routeMaxLon, point.getLongitude());
        }

        Log.d("AREA_DEBUG",
                "GPS 범위: 위도 "
                        + routeMinLat + " ~ " + routeMaxLat
                        + " / 경도 "
                        + routeMinLon + " ~ " + routeMaxLon);

        // 환경 객체의 외곽 경계 범위 계산
        for (OsmEnvironment environment : environments) {

            if (!targetType.equals(environment.getType())) {
                continue;
            }

            for (List<OsmCoordinate> ring : environment.getOuterRings()) {

                double minLat = Double.MAX_VALUE;
                double maxLat = -Double.MAX_VALUE;
                double minLon = Double.MAX_VALUE;
                double maxLon = -Double.MAX_VALUE;

                for (OsmCoordinate point : ring) {

                    minLat = Math.min(minLat, point.getLatitude());
                    maxLat = Math.max(maxLat, point.getLatitude());

                    minLon = Math.min(minLon, point.getLongitude());
                    maxLon = Math.max(maxLon, point.getLongitude());
                }

                Log.d("AREA_DEBUG",
                        environment.getName()
                                + " 외곽 경계 범위: 위도 "
                                + minLat + " ~ " + maxLat
                                + " / 경도 "
                                + minLon + " ~ " + maxLon);
            }
        }

        for (int i = 0; i < route.size() - 1; i++) {

            OsmCoordinate start = route.get(i);
            OsmCoordinate end = route.get(i + 1);

            double segmentDistance = calculateDistance(start, end);

            if (segmentDistance == 0) {
                continue;
            }

            int sampleCount = Math.max(
                    1,
                    (int) Math.ceil(segmentDistance / SAMPLE_INTERVAL)
            );

            double sampleDistance = segmentDistance / sampleCount;

            for (int j = 0; j < sampleCount; j++) {

                double ratio = (j + 0.5) / sampleCount;

                double latitude = start.getLatitude()
                        + (end.getLatitude() - start.getLatitude()) * ratio;

                double longitude = start.getLongitude()
                        + (end.getLongitude() - start.getLongitude()) * ratio;

                OsmCoordinate sample =
                        new OsmCoordinate(latitude, longitude);

                boolean insideArea = false;

                for (OsmEnvironment environment : environments) {

                    if (!targetType.equals(environment.getType())) {
                        continue;
                    }

                    if (isInsideEnvironment(sample, environment)) {
                        insideArea = true;
                        break;
                    }
                }

                if (insideArea) {
                    totalDistance += sampleDistance;
                }
            }
        }

        return totalDistance;
    }

    // ==========================================
    // 8. GPS 좌표가 다각형 내부에 있는지 확인
    // ==========================================

    private static boolean isInsidePolygon(
            OsmCoordinate point,
            List<OsmCoordinate> polygon) {

        if (polygon == null || polygon.size() < 4) {
            return false;
        }

        int count = polygon.size();

        OsmCoordinate first = polygon.get(0);
        OsmCoordinate last = polygon.get(count - 1);

        // 시작점과 끝점이 같지 않으면 닫힌 영역이 아님
        if (Math.abs(first.getLatitude() - last.getLatitude()) > 0.0000001
                || Math.abs(first.getLongitude() - last.getLongitude()) > 0.0000001) {
            return false;
        }

        boolean inside = false;

        double x = point.getLongitude();
        double y = point.getLatitude();

        for (int i = 0, j = count - 1; i < count; j = i++) {

            double xi = polygon.get(i).getLongitude();
            double yi = polygon.get(i).getLatitude();

            double xj = polygon.get(j).getLongitude();
            double yj = polygon.get(j).getLatitude();

            boolean intersects = ((yi > y) != (yj > y))
                    && (x < (xj - xi) * (y - yi)
                    / (yj - yi) + xi);

            if (intersects) {
                inside = !inside;
            }
        }

        return inside;
    }
}

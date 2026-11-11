
package com.cookandroid.runcatch;

import android.util.Log;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EnvironmentMatcher {

    private static final double EARTH_RADIUS = 6371000.0;

    // 환경별 인식 반경
    private static final double PARK_RADIUS = 100.0;
    private static final double FOREST_RADIUS = 100.0;
    private static final double RIVER_RADIUS = 50.0;
    private static final double LAKE_RADIUS = 150.0;

    public static Set<String> findMatchedCategories(
            double latitude,
            double longitude,
            List<EnvironmentFeature> features
    ) {
        Set<String> matchedCategories = new HashSet<>();

        if (features == null || features.isEmpty()) {
            return matchedCategories;
        }

        double nearestParkDistance = Double.MAX_VALUE;
        double nearestLakeDistance = Double.MAX_VALUE;
        double nearestRiverDistance = Double.MAX_VALUE;

        EnvironmentFeature nearestPark = null;
        EnvironmentFeature nearestLake = null;
        EnvironmentFeature nearestRiver = null;

        for (EnvironmentFeature feature : features) {

            String category = feature.getCategory();

            double distance;

            // 하천은 실제 경로와 GPS 좌표 사이의 최단 거리 계산
            if ("RIVER".equals(category)
                    && feature.getGeometry() != null
                    && feature.getGeometry().size() >= 2) {

                distance = calculateDistanceToRiver(
                        latitude,
                        longitude,
                        feature.getGeometry()
                );

            } else {

                // 공원, 숲, 호수는 기존 중앙 좌표 기준 거리 계산
                distance = calculateDistance(
                        latitude,
                        longitude,
                        feature.getLatitude(),
                        feature.getLongitude()
                );
            }

            double radius = getRadius(category);

            // 가장 가까운 공원 기록
            if ("PARK".equals(category)
                    && distance < nearestParkDistance) {

                nearestParkDistance = distance;
                nearestPark = feature;
            }

            // 가장 가까운 호수 기록
            if ("LAKE".equals(category)
                    && distance < nearestLakeDistance) {

                nearestLakeDistance = distance;
                nearestLake = feature;
            }

            // 가장 가까운 하천 기록
            if ("RIVER".equals(category)
                    && distance < nearestRiverDistance) {

                nearestRiverDistance = distance;
                nearestRiver = feature;
            }

            // 해당 환경의 인식 반경 안에 있으면 카운트 대상
            if (radius > 0 && distance <= radius) {
                matchedCategories.add(category);
            }
        }

        // 가장 가까운 환경 진단 로그
        if (nearestPark != null) {
            Log.d("ENV_DEBUG",
                    "공원: " + nearestPark.getName()
                            + " / 거리: "
                            + String.format(Locale.US, "%.2f", nearestParkDistance)
                            + "m / 반경: " + PARK_RADIUS + "m");
        }

        if (nearestLake != null) {
            Log.d("ENV_DEBUG",
                    "호수: " + nearestLake.getName()
                            + " / 거리: "
                            + String.format(Locale.US, "%.2f", nearestLakeDistance)
                            + "m / 반경: " + LAKE_RADIUS + "m");
        }

        if (nearestRiver != null) {
            Log.d("ENV_DEBUG",
                    "하천: " + nearestRiver.getName()
                            + " / 실제 경로까지 거리: "
                            + String.format(Locale.US, "%.2f", nearestRiverDistance)
                            + "m / 반경: " + RIVER_RADIUS + "m"
                            + " / 인식 여부: "
                            + (nearestRiverDistance <= RIVER_RADIUS));
        } else {
            Log.d("ENV_DEBUG", "하천 데이터가 JSON에 없습니다.");
        }

        return matchedCategories;
    }

    private static double getRadius(String category) {

        switch (category) {
            case "PARK":
                return PARK_RADIUS;

            case "FOREST":
                return FOREST_RADIUS;

            case "RIVER":
                return RIVER_RADIUS;

            case "LAKE":
                return LAKE_RADIUS;

            default:
                return 0;
        }
    }

    // GPS 좌표와 하천 전체 경로 사이의 최단 거리
    private static double calculateDistanceToRiver(
            double latitude,
            double longitude,
            List<double[]> geometry
    ) {

        double minimumDistance = Double.MAX_VALUE;

        // 하천의 연속된 두 좌표를 하나의 선분으로 계산
        for (int i = 0; i < geometry.size() - 1; i++) {

            double[] start = geometry.get(i);
            double[] end = geometry.get(i + 1);

            double distance = calculatePointToSegmentDistance(
                    latitude,
                    longitude,
                    start[0],
                    start[1],
                    end[0],
                    end[1]
            );

            if (distance < minimumDistance) {
                minimumDistance = distance;
            }
        }

        return minimumDistance;
    }

    // GPS 좌표와 두 좌표로 이루어진 선분 사이의 최단 거리
    private static double calculatePointToSegmentDistance(
            double pointLat,
            double pointLon,
            double startLat,
            double startLon,
            double endLat,
            double endLon
    ) {

        // GPS 좌표를 기준으로 주변 좌표를 평면 좌표(m)로 변환
        double referenceLat = Math.toRadians(pointLat);

        double pointX = 0;
        double pointY = 0;

        double startX = Math.toRadians(startLon - pointLon)
                * EARTH_RADIUS * Math.cos(referenceLat);

        double startY = Math.toRadians(startLat - pointLat)
                * EARTH_RADIUS;

        double endX = Math.toRadians(endLon - pointLon)
                * EARTH_RADIUS * Math.cos(referenceLat);

        double endY = Math.toRadians(endLat - pointLat)
                * EARTH_RADIUS;

        // 선분의 방향 벡터
        double dx = endX - startX;
        double dy = endY - startY;

        // 선분의 길이가 0인 경우
        double segmentLengthSquared = dx * dx + dy * dy;

        if (segmentLengthSquared == 0) {
            return Math.sqrt(
                    startX * startX + startY * startY
            );
        }

        // GPS 좌표를 선분에 투영한 위치 계산
        double t = (
                (pointX - startX) * dx
                        + (pointY - startY) * dy
        ) / segmentLengthSquared;

        // 선분 범위를 벗어나지 않도록 제한
        t = Math.max(0, Math.min(1, t));

        // 선분 위에서 GPS 좌표와 가장 가까운 지점
        double closestX = startX + t * dx;
        double closestY = startY + t * dy;

        // GPS 좌표와 가장 가까운 지점 사이의 거리
        double distanceX = pointX - closestX;
        double distanceY = pointY - closestY;

        return Math.sqrt(
                distanceX * distanceX
                        + distanceY * distanceY
        );
    }

    // 두 GPS 좌표 사이의 거리 계산 (Haversine 공식)
    private static double calculateDistance(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2)
                * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2)
                * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1 - a)
        );

        return EARTH_RADIUS * c;
    }
}


package com.cookandroid.runcatch;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class EnvironmentDataLoader {

    private static final String TAG = "EnvironmentDataLoader";
    private static final String FILE_NAME = "environment_data.json";

    public static ArrayList<EnvironmentFeature> load(Context context) {

        ArrayList<EnvironmentFeature> features = new ArrayList<>();

        int parkCount = 0;
        int forestCount = 0;
        int riverCount = 0;
        int lakeCount = 0;
        int riverGeometryCount = 0;

        try (
                InputStream inputStream =
                        context.getAssets().open(FILE_NAME);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                inputStream,
                                StandardCharsets.UTF_8
                        )
                )
        ) {

            // JSON 파일 전체 읽기
            StringBuilder jsonBuilder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            // Overpass JSON 파싱
            JSONObject root = new JSONObject(jsonBuilder.toString());
            JSONArray elements = root.getJSONArray("elements");

            for (int i = 0; i < elements.length(); i++) {

                JSONObject item = elements.getJSONObject(i);

                // OSM 기본 정보
                String osmType = item.optString("type");
                long osmId = item.optLong("id");

                // 태그 정보
                JSONObject tags = item.optJSONObject("tags");

                if (tags == null) {
                    continue;
                }

                String category = getCategory(tags);

                // 필요한 환경이 아니면 제외
                if (category == null) {
                    continue;
                }

                String name = tags.optString("name", "");

                double latitude = 0;
                double longitude = 0;

                // 하천의 전체 경로 좌표
                List<double[]> geometry = new ArrayList<>();

                if ("RIVER".equals(category)) {

                    JSONArray geometryArray =
                            item.optJSONArray("geometry");

                    if (geometryArray != null) {

                        for (int j = 0; j < geometryArray.length(); j++) {

                            JSONObject coordinate =
                                    geometryArray.getJSONObject(j);

                            double pointLatitude =
                                    coordinate.getDouble("lat");

                            double pointLongitude =
                                    coordinate.getDouble("lon");

                            geometry.add(new double[]{
                                    pointLatitude,
                                    pointLongitude
                            });
                        }
                    }

                    // 하천 경로가 없거나 선분을 만들 수 없으면 제외
                    if (geometry.size() < 2) {
                        continue;
                    }

                    // 첫 번째 좌표를 대표 좌표로 저장
                    latitude = geometry.get(0)[0];
                    longitude = geometry.get(0)[1];

                    riverCount++;
                    riverGeometryCount++;

                } else {

                    // 공원, 숲, 호수는 중앙 좌표 사용
                    JSONObject center = item.optJSONObject("center");

                    if (center == null) {
                        continue;
                    }

                    latitude = center.optDouble("lat", 0);
                    longitude = center.optDouble("lon", 0);

                    if ("PARK".equals(category)) {
                        parkCount++;

                    } else if ("FOREST".equals(category)) {
                        forestCount++;

                    } else if ("LAKE".equals(category)) {
                        lakeCount++;
                    }
                }

                // EnvironmentFeature 생성
                EnvironmentFeature feature = new EnvironmentFeature(
                        osmType,
                        osmId,
                        category,
                        name,
                        latitude,
                        longitude,
                        geometry.isEmpty() ? null : geometry
                );

                features.add(feature);
            }

            // 로딩 결과 로그
            Log.d(TAG, "환경 데이터 로딩 성공");
            Log.d(TAG, "전체 환경 데이터: " + features.size() + "개");
            Log.d(TAG, "공원: " + parkCount);
            Log.d(TAG, "숲: " + forestCount);
            Log.d(TAG, "하천: " + riverCount);
            Log.d(TAG, "호수: " + lakeCount);
            Log.d(TAG, "하천 경로 좌표 로딩: " + riverGeometryCount + "개");

        } catch (Exception e) {

            Log.e(TAG, "환경 데이터 로딩 실패", e);
        }

        return features;
    }

    // OSM 태그를 앱에서 사용하는 환경 종류로 변환
    private static String getCategory(JSONObject tags) {

        String waterway = tags.optString("waterway", "");

        if ("river".equals(waterway)
                || "stream".equals(waterway)
                || "canal".equals(waterway)) {

            return "RIVER";
        }

        if ("park".equals(tags.optString("leisure", ""))) {
            return "PARK";
        }

        if ("forest".equals(tags.optString("landuse", ""))
                || "wood".equals(tags.optString("natural", ""))) {

            return "FOREST";
        }

        if ("water".equals(tags.optString("natural", ""))
                || "lake".equals(tags.optString("water", ""))) {

            return "LAKE";
        }

        return null;
    }
}


package com.cookandroid.runcatch;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class EnvironmentParser {

    private static final String TAG = "EnvironmentParser";

    private static final double COORDINATE_EPSILON = 0.0000001;

    public static ArrayList<OsmEnvironment> parse(String result)
            throws JSONException {

        JSONObject jsonObject = new JSONObject(result);
        JSONArray elements = jsonObject.getJSONArray("elements");

        ArrayList<OsmEnvironment> environments = new ArrayList<>();

        // 1. 최상위 elements에서 way ID와 좌표를 미리 저장
        HashMap<Long, ArrayList<OsmCoordinate>> wayGeometries =
                new HashMap<>();

        // Overpass 응답 데이터 확인
        for (int i = 0; i < Math.min(elements.length(), 10); i++) {

            JSONObject element = elements.getJSONObject(i);

            Log.d(TAG,
                    "응답 요소 확인"
                            + " / type: " + element.optString("type")
                            + " / id: " + element.optLong("id")
                            + " / tags: " + element.optJSONObject("tags")
                            + " / geometry: " + (element.optJSONArray("geometry") != null)
                            + " / members: " + (element.optJSONArray("members") != null)
            );
        }

        for (int i = 0; i < elements.length(); i++) {

            JSONObject element = elements.getJSONObject(i);

            if (!"way".equals(element.optString("type"))) {
                continue;
            }

            JSONArray geometry = element.optJSONArray("geometry");

            if (geometry == null || geometry.length() < 2) {
                continue;
            }

            long wayId = element.getLong("id");

            wayGeometries.put(
                    wayId,
                    parseGeometry(geometry)
            );
        }

        Log.d(TAG, "way 좌표 저장 개수: " + wayGeometries.size());

        // 2. 환경 객체 파싱
        for (int i = 0; i < elements.length(); i++) {

            JSONObject element = elements.getJSONObject(i);
            JSONObject tags = element.optJSONObject("tags");

            if (tags == null) {
                continue;
            }

            String elementType = element.optString("type");

            String name = tags.optString("name", "이름 없음");
            String environment = getEnvironmentType(tags);

            if (environment == null) {
                continue;
            }

            // 3. relation 처리
            if ("relation".equals(elementType)) {

                JSONArray members = element.optJSONArray("members");

                if (members == null) {
                    continue;
                }

                ArrayList<ArrayList<OsmCoordinate>> outerWays =
                        new ArrayList<>();

                ArrayList<ArrayList<OsmCoordinate>> innerWays =
                        new ArrayList<>();

                for (int j = 0; j < members.length(); j++) {

                    JSONObject member = members.getJSONObject(j);

                    if (!"way".equals(member.optString("type"))) {
                        continue;
                    }

                    long ref = member.optLong("ref", -1);
                    String role = member.optString("role");

                    // member의 ref와 일치하는 way 좌표 검색
                    ArrayList<OsmCoordinate> coordinates =
                            wayGeometries.get(ref);

                    if (coordinates == null || coordinates.size() < 2) {
                        Log.w(TAG, "way 좌표를 찾을 수 없음: " + ref);
                        continue;
                    }

                    if ("outer".equals(role)) {
                        outerWays.add(new ArrayList<>(coordinates));
                    } else if ("inner".equals(role)) {
                        innerWays.add(new ArrayList<>(coordinates));
                    }
                }

                // 여러 way를 연결하여 닫힌 경계 생성
                ArrayList<ArrayList<OsmCoordinate>> outerRings =
                        connectWays(outerWays);

                ArrayList<ArrayList<OsmCoordinate>> innerRings =
                        connectWays(innerWays);

                if (outerRings.isEmpty()) {
                    Log.w(TAG, "relation 외곽 경계 생성 실패: " + name);
                    continue;
                }

                OsmEnvironment osmEnvironment =
                        new OsmEnvironment(
                                environment,
                                name,
                                outerRings,
                                innerRings
                        );

                environments.add(osmEnvironment);

                Log.d(TAG,
                        "relation 파싱 완료: " + name
                                + " / outer: " + outerRings.size()
                                + " / inner: " + innerRings.size());
                Log.d("EnvironmentParser",
                        "relation 태그 확인: " + tags.toString());

                continue;
            }

            // 4. 기존 node 및 way 처리
            ArrayList<OsmCoordinate> coordinates =
                    new ArrayList<>();

            if (element.has("lat") && element.has("lon")) {

                coordinates.add(
                        new OsmCoordinate(
                                element.getDouble("lat"),
                                element.getDouble("lon")
                        )
                );

            } else {

                JSONArray geometry = element.optJSONArray("geometry");

                if (geometry == null || geometry.length() == 0) {
                    continue;
                }

                coordinates = parseGeometry(geometry);
            }

            environments.add(
                    new OsmEnvironment(
                            environment,
                            name,
                            coordinates
                    )
            );
        }

        Log.d(TAG, "Overpass 응답 객체 수: " + elements.length());
        Log.d(TAG, "파싱된 환경 객체 수: " + environments.size());

        return environments;
    }

    // 환경 종류 판별
    private static String getEnvironmentType(JSONObject tags) {

        if ("park".equals(tags.optString("leisure"))) {
            return "공원";
        }

        if ("forest".equals(tags.optString("landuse"))
                || "wood".equals(tags.optString("natural"))) {
            return "숲";
        }

        if (tags.has("waterway")) {
            return "하천";
        }

        return null;
    }

    // JSON geometry를 좌표 리스트로 변환
    private static ArrayList<OsmCoordinate> parseGeometry(
            JSONArray geometry) throws JSONException {

        ArrayList<OsmCoordinate> coordinates = new ArrayList<>();

        for (int i = 0; i < geometry.length(); i++) {

            JSONObject point = geometry.getJSONObject(i);

            coordinates.add(
                    new OsmCoordinate(
                            point.getDouble("lat"),
                            point.getDouble("lon")
                    )
            );
        }

        return coordinates;
    }

    // 여러 way를 연결하여 하나 이상의 닫힌 경계 생성
    private static ArrayList<ArrayList<OsmCoordinate>> connectWays(
            ArrayList<ArrayList<OsmCoordinate>> ways) {

        ArrayList<ArrayList<OsmCoordinate>> rings = new ArrayList<>();
        ArrayList<ArrayList<OsmCoordinate>> remaining = new ArrayList<>();

        for (ArrayList<OsmCoordinate> way : ways) {

            if (way != null && way.size() >= 2) {
                remaining.add(new ArrayList<>(way));
            }
        }

        while (!remaining.isEmpty()) {

            ArrayList<OsmCoordinate> ring = remaining.remove(0);

            boolean connected = true;

            while (connected && !isClosed(ring)) {

                connected = false;

                OsmCoordinate ringStart = ring.get(0);
                OsmCoordinate ringEnd = ring.get(ring.size() - 1);

                for (int i = 0; i < remaining.size(); i++) {

                    ArrayList<OsmCoordinate> candidate = remaining.get(i);

                    OsmCoordinate candidateStart = candidate.get(0);
                    OsmCoordinate candidateEnd =
                            candidate.get(candidate.size() - 1);

                    // 현재 끝과 후보 시작이 연결되는 경우
                    if (sameCoordinate(ringEnd, candidateStart)) {

                        appendCoordinates(ring, candidate, 1);
                        remaining.remove(i);
                        connected = true;
                        break;
                    }

                    // 현재 끝과 후보 끝이 연결되는 경우
                    if (sameCoordinate(ringEnd, candidateEnd)) {

                        Collections.reverse(candidate);
                        appendCoordinates(ring, candidate, 1);
                        remaining.remove(i);
                        connected = true;
                        break;
                    }

                    // 후보 끝과 현재 시작이 연결되는 경우
                    if (sameCoordinate(candidateEnd, ringStart)) {

                        appendCoordinates(ring, candidate, 0);
                        remaining.remove(i);
                        connected = true;
                        break;
                    }

                    // 후보 시작과 현재 시작이 연결되는 경우
                    if (sameCoordinate(candidateStart, ringStart)) {

                        Collections.reverse(candidate);
                        appendCoordinates(ring, candidate, 0);
                        remaining.remove(i);
                        connected = true;
                        break;
                    }
                }
            }

            if (isClosed(ring) && ring.size() >= 4) {

                rings.add(ring);

                Log.d(TAG,
                        "닫힌 경계 생성 완료 / 좌표 수: " + ring.size());

            } else {

                Log.w(TAG,
                        "닫힌 경계 생성 실패 / 좌표 수: " + ring.size());
            }
        }

        return rings;
    }

    // 두 좌표가 같은 위치인지 비교
    private static boolean sameCoordinate(
            OsmCoordinate a,
            OsmCoordinate b) {

        return Math.abs(a.getLatitude() - b.getLatitude())
                <= COORDINATE_EPSILON
                && Math.abs(a.getLongitude() - b.getLongitude())
                <= COORDINATE_EPSILON;
    }

    // 좌표 리스트 연결
    private static void appendCoordinates(
            ArrayList<OsmCoordinate> target,
            ArrayList<OsmCoordinate> source,
            int startIndex) {

        for (int i = startIndex; i < source.size(); i++) {
            target.add(source.get(i));
        }
    }

    // 경계가 닫혀 있는지 확인
    private static boolean isClosed(
            ArrayList<OsmCoordinate> ring) {

        if (ring == null || ring.size() < 4) {
            return false;
        }

        return sameCoordinate(
                ring.get(0),
                ring.get(ring.size() - 1)
        );
    }
}

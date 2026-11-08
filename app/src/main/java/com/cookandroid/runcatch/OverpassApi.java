
package com.cookandroid.runcatch;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OverpassApi {

    private static final String TAG = "OverpassApi";

    private static final String OVERPASS_URL =
            "https://overpass-api.de/api/interpreter";

    private static final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public interface Callback {
        void onSuccess(String result);
        void onError(Exception e);
    }

    // 특정 좌표를 중심으로 반경 내 환경 정보 검색
    public static void searchEnvironment(
            double latitude,
            double longitude,
            int radius,
            Callback callback
    ) {

        String query =
                "[out:json][timeout:25];"
                        + "("
                        + "nwr(around:" + radius + "," + latitude + "," + longitude + ")[\"leisure\"=\"park\"];"
                        + "nwr(around:" + radius + "," + latitude + "," + longitude + ")[\"landuse\"=\"forest\"];"
                        + "nwr(around:" + radius + "," + latitude + "," + longitude + ")[\"natural\"=\"wood\"];"
                        + "nwr(around:" + radius + "," + latitude + "," + longitude + ")[\"waterway\"~\"^(river|stream|canal)$\"];"
                        + ");"
                        + "out geom;";

        // request() 내부에서 백그라운드 실행
        request(query, callback);
    }

    // 러닝 경로의 최소/최대 좌표로 환경 정보 검색
    public static void searchEnvironment(
            double south,
            double west,
            double north,
            double east,
            Callback callback
    ) {

        Log.d(TAG, "검색 영역: "
                + south + ", " + west + ", "
                + north + ", " + east);

        String query =
                "[out:json][timeout:25];"
                        + "("
                        + "nwr(" + south + "," + west + "," + north + "," + east + ")[\"leisure\"=\"park\"];"
                        + "nwr(" + south + "," + west + "," + north + "," + east + ")[\"landuse\"=\"forest\"];"
                        + "nwr(" + south + "," + west + "," + north + "," + east + ")[\"natural\"=\"wood\"];"
                        + "nwr(" + south + "," + west + "," + north + "," + east + ")[\"waterway\"~\"^(river|stream|canal)$\"];"
                        + ");"
                        + "out geom;";

        // request() 내부에서 백그라운드 실행
        request(query, callback);
    }

    // 특정 relation의 구성 요소 조회
    public static void searchRelation(
            long relationId,
            Callback callback
    ) {

        String query =
                "[out:json][timeout:25];"
                        + "rel(" + relationId + ");"
                        + "out body;"
                        + ">;"
                        + "out geom;";

        request(query, callback);
    }

    // Overpass API 요청
    private static void request(String query, Callback callback) {

        executor.execute(() -> {

            Exception lastError = null;
            long waitTime = 2000;

            // 최초 요청 1회 + 재시도 최대 3회
            for (int attempt = 0; attempt <= 3; attempt++) {

                HttpURLConnection connection = null;

                try {

                    URL url = new URL(OVERPASS_URL);
                    connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("POST");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    connection.setDoOutput(true);

                    connection.setRequestProperty(
                            "User-Agent",
                            "RUNCATCH/1.0 (+https://github.com/GameRunningApp/runcatch)"
                    );

                    connection.setRequestProperty(
                            "Accept",
                            "application/json"
                    );

                    connection.setRequestProperty(
                            "Content-Type",
                            "application/x-www-form-urlencoded; charset=UTF-8"
                    );

                    String requestBody =
                            "data=" + URLEncoder.encode(query, "UTF-8");

                    byte[] requestBytes =
                            requestBody.getBytes(StandardCharsets.UTF_8);

                    try (OutputStream outputStream = connection.getOutputStream()) {
                        outputStream.write(requestBytes);
                        outputStream.flush();
                    }

                    int responseCode = connection.getResponseCode();

                    Log.d(TAG, "HTTP 응답 코드: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) {

                        StringBuilder result = new StringBuilder();

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(
                                        connection.getInputStream(),
                                        StandardCharsets.UTF_8
                                ))) {

                            String line;

                            while ((line = reader.readLine()) != null) {
                                result.append(line);
                            }
                        }

                        Log.d(TAG, "API 응답 성공");

                        callback.onSuccess(result.toString());
                        return;
                    }

                    // 서버 오류 응답 내용 확인
                    StringBuilder errorResult = new StringBuilder();

                    if (connection.getErrorStream() != null) {

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(
                                        connection.getErrorStream(),
                                        StandardCharsets.UTF_8
                                ))) {

                            String line;

                            while ((line = reader.readLine()) != null) {
                                errorResult.append(line);
                            }
                        }
                    }

                    lastError = new Exception(
                            "서버 응답 오류: " + responseCode
                                    + " / " + errorResult
                    );

                    Log.e(TAG, "서버 오류 응답 내용: " + errorResult);

                    // 재시도할 서버 응답 코드
                    boolean retryable =
                            responseCode == 429
                                    || responseCode == 502
                                    || responseCode == 503
                                    || responseCode == 504;

                    if (!retryable || attempt == 3) {
                        break;
                    }

                } catch (SocketTimeoutException e) {

                    lastError = e;

                    if (attempt == 3) {
                        break;
                    }

                } catch (Exception e) {

                    lastError = e;
                    break;

                } finally {

                    if (connection != null) {
                        connection.disconnect();
                    }
                }

                // 다음 재시도까지 대기
                Log.w(TAG,
                        (attempt + 1) + "번째 요청 실패. "
                                + (waitTime / 1000) + "초 후 재시도합니다."
                );

                try {

                    Thread.sleep(waitTime);

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                    lastError = e;
                    break;
                }

                waitTime *= 2;
            }

            Log.e(TAG, "API 요청 최종 실패", lastError);

            callback.onError(lastError);
        });
    }
}

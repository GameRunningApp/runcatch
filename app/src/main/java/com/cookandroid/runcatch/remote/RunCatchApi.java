package com.cookandroid.runcatch.remote;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * FastAPI 서버(server/) 호출.
 * 화면에서 HTTP 연결 코드를 분리. 네트워크라서 메인 스레드가 아닌 곳에서 호출
 *
 * CommunityActivity 안에 반복되던 HttpURLConnection 코드를 모았음
 *   DB 연결 작업에서 delete(), getArray(), encode(), ApiException 추가
 */
public final class RunCatchApi {

    // 에뮬레이터에서 PC의 로컬 백엔드에 접속하는 주소
    // [백엔드] 실제 서버 배포 시 서버 주소로 변경
    public static final String BASE_URL = "http://127.0.0.1:8000";

    private static final int TIMEOUT_MS = 5000;

    // 예외 처리
    public static class ApiException extends IOException {
        public final int code;

        ApiException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private RunCatchApi() {
    }

    public static JSONObject get(String path) throws IOException, JSONException {
        return toObject(send("GET", path, null));
    }

    // 응답이 JSON 배열인 API
    public static JSONArray getArray(String path) throws IOException, JSONException {
        String response = send("GET", path, null);
        return response.isEmpty() ? new JSONArray() : new JSONArray(response);
    }

    public static JSONObject post(String path, JSONObject body) throws IOException, JSONException {
        return toObject(send("POST", path, body));
    }

    public static JSONObject delete(String path) throws IOException, JSONException {
        return toObject(send("DELETE", path, null));
    }

    private static JSONObject toObject(String response) throws JSONException {
        return response.isEmpty() ? new JSONObject() : new JSONObject(response);
    }

    //  문자열에 넣을 값 인코딩 (이모지 · 한글 닉네임)
    public static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);  // UTF-8은 항상 지원됨
        }
    }

    // 응답
    private static String send(String method, String path, JSONObject body)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }

            int responseCode = connection.getResponseCode();
            boolean ok = responseCode >= 200 && responseCode < 300;
            InputStream stream = ok ? connection.getInputStream() : connection.getErrorStream();
            String response = stream == null ? "" : read(stream);

            if (!ok) {
                String detail = null;
                try {
                    detail = new JSONObject(response).optString("detail", null);
                } catch (JSONException ignored) {
                    // 오류 응답이 JSON이 아니면 HTTP 코드만
                }
                throw new ApiException(responseCode, detail != null ? detail : "HTTP " + responseCode);
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream inputStream) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        return result.toString();
    }
}

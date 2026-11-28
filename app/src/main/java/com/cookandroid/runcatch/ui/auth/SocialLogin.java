package com.cookandroid.runcatch.ui.auth;

import com.cookandroid.runcatch.BuildConfig;

import android.app.Activity;
import android.os.CancellationSignal;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.kakao.sdk.auth.model.OAuthToken;
import com.kakao.sdk.common.KakaoSdk;
import com.kakao.sdk.common.model.ClientError;
import com.kakao.sdk.common.model.ClientErrorCause;
import com.kakao.sdk.user.UserApiClient;
import com.kakao.sdk.user.model.Account;
import com.kakao.sdk.user.model.Profile;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

/**
 * 간편 로그인 (카카오 · 구글).
 *
 * 로그인에 성공하면 어느 방식이든 같은 형태(SocialAccount)로 돌려준다.
 * 앱 키는 local.properties → BuildConfig로 들어오고, 비어 있으면 "설정 필요"로 실패 처리한다.
 * 계정을 user 테이블에 저장 · 확인하는 부분은 LoginActivity가 맡는다 (이 클래스는 카카오 · 구글 로그인만)
 *
 * TODO(서버): 서버 로그인 API가 생기면 token을 서버로 보내 카카오 · 구글에 진짜인지 확인
 */
public final class SocialLogin {

    private static final String TAG = "SocialLogin";

    public static final String PROVIDER_KAKAO = "kakao";
    public static final String PROVIDER_GOOGLE = "google";

    private static boolean kakaoInitialized = false;

    private SocialLogin() {
    }

    /** 로그인 결과 (카카오 · 구글 공통) */
    public static final class SocialAccount {
        public final String provider;   // kakao / google
        public final String id;         // 카카오 회원번호 또는 구글 계정 이메일
        public final String nickname;   // 없으면 빈 문자열
        public final String email;      // 동의하지 않았으면 빈 문자열
        public final String token;      // 서버 확인용 : 카카오 액세스 토큰 / 구글 ID 토큰
        /**
         * user 테이블의 mail(기본 키)로 쓸 값.
         * 구글 · 카카오가 확인한 이메일이면 그 이메일, 카카오에서 확인된 이메일이 없으면
         * "kakao_회원번호@kakao.runcatch" (같은 카카오 계정이면 항상 같은 값)
         */
        public final String loginMail;

        SocialAccount(String provider, String id, String nickname, String email, String token,
                      String loginMail) {
            this.provider = provider;
            this.id = id;
            this.nickname = nickname == null ? "" : nickname;
            this.email = email == null ? "" : email;
            this.token = token == null ? "" : token;
            this.loginMail = loginMail;
        }

        /** 화면에 보여줄 이름 : 닉네임 → 이메일 → "러너" 순서 */
        public String displayName() {
            if (!nickname.isEmpty()) return nickname;
            if (!email.isEmpty()) return email;
            return "러너";
        }
    }

    public interface Callback {
        void onSuccess(SocialAccount account);

        /** 사용자가 로그인 창을 닫음 → 별도 안내 없이 그대로 둠 */
        void onCancel();

        void onFailure(String message);
    }

    // ===== 카카오 =====

    public static boolean isKakaoConfigured() {
        return !BuildConfig.KAKAO_NATIVE_APP_KEY.isEmpty();
    }

    /** 카카오톡이 설치돼 있으면 카카오톡으로, 없거나 실패하면 카카오계정(웹)으로 로그인 */
    public static void loginWithKakao(Activity activity, Callback callback) {
        if (!isKakaoConfigured()) {
            callback.onFailure("카카오 로그인 설정이 필요해요 (local.properties에 KAKAO_NATIVE_APP_KEY 추가)");
            return;
        }
        if (!kakaoInitialized) {
            KakaoSdk.init(activity.getApplicationContext(), BuildConfig.KAKAO_NATIVE_APP_KEY);
            kakaoInitialized = true;
        }

        UserApiClient client = UserApiClient.getInstance();

        Function2<OAuthToken, Throwable, Unit> accountCallback = (token, error) -> {
            if (error != null) {
                handleKakaoError(error, callback);
            } else if (token != null) {
                fetchKakaoProfile(token.getAccessToken(), callback);
            }
            return Unit.INSTANCE;
        };

        if (client.isKakaoTalkLoginAvailable(activity)) {
            client.loginWithKakaoTalk(activity, (token, error) -> {
                if (error != null) {
                    if (isKakaoCancel(error)) {
                        callback.onCancel();
                    } else {
                        // 카카오톡에 연결된 카카오계정이 없는 경우 등 → 카카오계정으로 다시 시도
                        Log.w(TAG, "카카오톡 로그인 실패, 카카오계정으로 재시도", error);
                        client.loginWithKakaoAccount(activity, accountCallback);
                    }
                } else if (token != null) {
                    fetchKakaoProfile(token.getAccessToken(), callback);
                }
                return Unit.INSTANCE;
            });
        } else {
            client.loginWithKakaoAccount(activity, accountCallback);
        }
    }

    private static void fetchKakaoProfile(String accessToken, Callback callback) {
        UserApiClient.getInstance().me((user, error) -> {
            if (error != null || user == null || user.getId() == null) {
                Log.e(TAG, "카카오 사용자 정보 요청 실패", error);
                callback.onFailure("카카오 사용자 정보를 가져오지 못했어요");
                return Unit.INSTANCE;
            }
            String id = String.valueOf(user.getId());
            String nickname = "";
            String email = "";
            boolean emailVerified = false;
            Account account = user.getKakaoAccount();
            if (account != null) {
                Profile profile = account.getProfile();
                if (profile != null && profile.getNickname() != null) nickname = profile.getNickname();
                if (account.getEmail() != null) email = account.getEmail();
                emailVerified = Boolean.TRUE.equals(account.isEmailVerified());
            }
            // 카카오가 확인한 이메일일 때만 이메일로 계정을 찾음 (확인 안 된 이메일로 남의 계정에 들어가는 것 방지)
            String loginMail = (emailVerified && !email.isEmpty())
                    ? email : "kakao_" + id + "@kakao.runcatch";
            callback.onSuccess(new SocialAccount(
                    PROVIDER_KAKAO, id, nickname, email, accessToken, loginMail));
            return Unit.INSTANCE;
        });
    }

    private static boolean isKakaoCancel(Throwable error) {
        return error instanceof ClientError
                && ((ClientError) error).getReason() == ClientErrorCause.Cancelled;
    }

    private static void handleKakaoError(Throwable error, Callback callback) {
        if (isKakaoCancel(error)) {
            callback.onCancel();
        } else {
            Log.e(TAG, "카카오 로그인 실패", error);
            callback.onFailure("카카오 로그인에 실패했어요");
        }
    }

    // ===== 구글 =====

    public static boolean isGoogleConfigured() {
        return !BuildConfig.GOOGLE_WEB_CLIENT_ID.isEmpty();
    }

    /** 구글 계정 선택 창(Credential Manager)을 띄워 로그인 */
    public static void loginWithGoogle(Activity activity, Callback callback) {
        if (!isGoogleConfigured()) {
            callback.onFailure("구글 로그인 설정이 필요해요 (local.properties에 GOOGLE_WEB_CLIENT_ID 추가)");
            return;
        }
        // "Google 계정으로 로그인" 버튼을 눌렀을 때 쓰는 옵션 (웹 애플리케이션 클라이언트 ID 사용)
        GetSignInWithGoogleOption option =
                new GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build();

        CredentialManager.create(activity).getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        handleGoogleCredential(response.getCredential(), callback);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            callback.onCancel();
                        } else {
                            Log.e(TAG, "구글 로그인 실패", e);
                            callback.onFailure("구글 로그인에 실패했어요");
                        }
                    }
                });
    }

    private static void handleGoogleCredential(Credential credential, Callback callback) {
        if (credential instanceof CustomCredential
                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            try {
                GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(credential.getData());
                String email = google.getId();   // 구글 계정 이메일
                // 구글 계정 이메일은 구글이 확인한 이메일
                callback.onSuccess(new SocialAccount(PROVIDER_GOOGLE, email,
                        google.getDisplayName(), email, google.getIdToken(), email));
            } catch (Exception e) {
                // createFrom()은 GoogleIdTokenParsingException을 던지지만 Kotlin 코드라 Java에서는
                // 선언된 예외로 보이지 않음 → Exception으로 잡아야 컴파일됨
                Log.e(TAG, "구글 ID 토큰을 읽지 못함", e);
                callback.onFailure("구글 로그인 정보를 읽지 못했어요");
            }
        } else {
            callback.onFailure("지원하지 않는 로그인 정보예요");
        }
    }
}

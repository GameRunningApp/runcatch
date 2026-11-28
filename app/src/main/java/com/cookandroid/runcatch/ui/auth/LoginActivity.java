package com.cookandroid.runcatch.ui.auth;

import com.cookandroid.runcatch.R;
import com.cookandroid.runcatch.core.Session;
import com.cookandroid.runcatch.core.local.RunCatchDbHelper;
import com.cookandroid.runcatch.remote.RunCatchApi;
import com.cookandroid.runcatch.ui.common.BaseActivity;
import com.cookandroid.runcatch.ui.home.MainActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;


/**
 *   화면 여백 코드는 BaseActivity.applyInputScreenInsets()로 옮김
 *   임시 유저 "aaa"에 덮어쓰던 로그인을 user 테이블 비교 + Session.login()으로 바꿈
 *   간편 로그인(카카오 · 구글) : 처음이면 닉네임을 정해 서버(POST /users) · user 테이블에 가입, 이후엔 바로 로그인
 *   비밀번호 찾기 : PasswordReset (서버 역할은 그 안에 하드코딩)
 */
public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";
    // 회원가입 화면(SignUpActivity)과 같은 닉네임 규칙
    private static final int NICKNAME_MIN_LENGTH = 2;
    private static final int NICKNAME_MAX_LENGTH = 10;

    private EditText emailEditText;
    private EditText passwordEditText;

    private final ActivityResultLauncher<Intent> signUpLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String email = result.getData().getStringExtra(SignUpActivity.EXTRA_EMAIL);
                    emailEditText.setText(email);
                    passwordEditText.setText("");
                    passwordEditText.requestFocus();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        applyInputScreenInsets(R.id.rootLayout);

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);

        Button loginButton = findViewById(R.id.loginButton);

        loginButton.setOnClickListener(v -> login());

        passwordEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                login();
                return true;
            }
            return false;
        });

        // 간편 로그인 (카카오 · 구글)
        Button kakaoLoginButton = findViewById(R.id.kakaoLoginButton);
        kakaoLoginButton.setOnClickListener(v ->
                SocialLogin.loginWithKakao(this, socialLoginCallback("카카오")));

        Button googleLoginButton = findViewById(R.id.googleLoginButton);
        googleLoginButton.setOnClickListener(v ->
                SocialLogin.loginWithGoogle(this, socialLoginCallback("구글")));

        // 비밀번호 찾기 → 바꾸고 나면 이메일을 채우고 비밀번호 입력으로
        TextView forgotPasswordText = findViewById(R.id.forgotPasswordText);
        forgotPasswordText.setOnClickListener(v ->
                PasswordReset.start(this, emailEditText.getText().toString().trim(), email -> {
                    emailEditText.setText(email);
                    passwordEditText.setText("");
                    passwordEditText.requestFocus();
                }));

        TextView signUpText = findViewById(R.id.signUpText);

        signUpText.setOnClickListener(v ->
                signUpLauncher.launch(new Intent(this, SignUpActivity.class)));
    }

    private void login() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        if (email.isEmpty()) {
            emailEditText.setError("이메일을 입력하세요");
            emailEditText.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("이메일 형식이 올바르지 않습니다");
            emailEditText.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordEditText.setError("비밀번호를 입력하세요");
            passwordEditText.requestFocus();
            return;
        }

        // 회원가입한 유저(user 테이블)와 비교. 맞으면 그 유저로 로그인
        // TODO(서버): 서버에 user 테이블과 로그인 API가 생기면 서버에서 확인
        String userId = RunCatchDbHelper.getInstance(this).findUserId(email, password);
        if (userId == null) {
            Toast.makeText(this, "이메일 또는 비밀번호가 올바르지 않습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        goHome(userId);
    }

    /** 로그인 성공 (이메일 · 카카오 · 구글 공통) → 세션 저장 후 홈으로 */
    private void goHome(String userId) {
        Session.login(this, userId);

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    // ===== 간편 로그인 (카카오 · 구글) =====

    /** 카카오 · 구글 로그인 결과 : 성공하면 계정 확인, 취소하면 그대로, 실패하면 안내 */
    private SocialLogin.Callback socialLoginCallback(String providerName) {
        return new SocialLogin.Callback() {
            @Override
            public void onSuccess(SocialLogin.SocialAccount account) {
                if (isFinishing() || isDestroyed()) return;
                onSocialAccount(account, providerName);
            }

            @Override
            public void onCancel() {
                // 사용자가 로그인 창을 닫음 → 로그인 화면 그대로
            }

            @Override
            public void onFailure(String message) {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        };
    }

    /** 이미 가입한 간편 로그인 계정이면 바로 로그인, 처음이면 닉네임을 정해 가입 */
    private void onSocialAccount(SocialLogin.SocialAccount account, String providerName) {
        String userId = RunCatchDbHelper.getInstance(this).findUserIdByMail(account.loginMail);
        if (userId != null) {
            Toast.makeText(this, userId + "님, " + providerName + " 계정으로 로그인했어요",
                    Toast.LENGTH_SHORT).show();
            goHome(userId);
            return;
        }
        showSocialNicknameDialog(account, providerName);
    }

    /** 처음 간편 로그인 : 닉네임(user_id)을 정하면 서버(POST /users) → user 테이블 순서로 가입 */
    private void showSocialNicknameDialog(SocialLogin.SocialAccount account, String providerName) {
        EditText input = new EditText(this);
        input.setHint("닉네임 (2~10자)");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        String suggested = account.nickname.trim();
        input.setText(suggested.length() > NICKNAME_MAX_LENGTH
                ? suggested.substring(0, NICKNAME_MAX_LENGTH) : suggested);

        int side = (int) (24 * getResources().getDisplayMetrics().density);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(side, 0, side, 0);
        frame.addView(input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(providerName + " 계정으로 가입")
                .setMessage("런캐치에서 쓸 닉네임을 정해 주세요")
                .setView(frame)
                .setPositiveButton("가입하기", null)
                .setNegativeButton("취소", null)
                .show();

        // 입력이 잘못됐을 때 창이 닫히지 않도록 확인 버튼을 직접 처리
        Button joinButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        joinButton.setOnClickListener(v -> {
            String nickname = input.getText().toString().trim();
            if (nickname.length() < NICKNAME_MIN_LENGTH || nickname.length() > NICKNAME_MAX_LENGTH) {
                input.setError("닉네임은 2~10자로 입력하세요");
                return;
            }
            RunCatchDbHelper db = RunCatchDbHelper.getInstance(this);
            if (db.isUserIdTaken(nickname)) {
                input.setError("이미 사용 중인 닉네임입니다");
                return;
            }

            // 회원가입 화면과 같이 서버에 먼저 등록해서 다른 폰과 닉네임 · 이메일이 겹치지 않게 함
            joinButton.setEnabled(false);
            new Thread(() -> {
                String error = registerOnServer(nickname, account.loginMail);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    joinButton.setEnabled(true);
                    if ("user_id_taken".equals(error)) {
                        input.setError("이미 사용 중인 닉네임입니다");
                        return;
                    }
                    if ("mail_taken".equals(error)) {
                        dialog.dismiss();
                        Toast.makeText(this, "이 계정은 다른 기기에서 이미 가입되어 있어요",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (error != null) {
                        Toast.makeText(this, "서버에 연결하지 못해 가입할 수 없습니다",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!db.insertSocialUser(account.loginMail, nickname)) {
                        Toast.makeText(this, "가입에 실패했습니다", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    Toast.makeText(this, nickname + "님, " + providerName + " 계정으로 가입했어요!",
                            Toast.LENGTH_SHORT).show();
                    goHome(nickname);
                });
            }).start();
        });
    }

    /** @return 성공하면 null, 실패하면 서버 detail(user_id_taken, mail_taken) 또는 "network" */
    private String registerOnServer(String nickname, String mail) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", nickname);
            body.put("mail", mail);
            RunCatchApi.post("/users", body);
            return null;
        } catch (RunCatchApi.ApiException e) {
            Log.e(TAG, "서버 회원 등록 실패: " + e.code, e);
            return e.code == 409 ? e.getMessage() : "network";
        } catch (Exception e) {
            Log.e(TAG, "서버 회원 등록 실패", e);
            return "network";
        }
    }
}

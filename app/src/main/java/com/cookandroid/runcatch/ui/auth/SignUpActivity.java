package com.cookandroid.runcatch.ui.auth;

import com.cookandroid.runcatch.R;
import com.cookandroid.runcatch.core.local.RunCatchDbHelper;
import com.cookandroid.runcatch.remote.RunCatchApi;
import com.cookandroid.runcatch.ui.common.BaseActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;


// 저장 없이 끝나던 회원가입을 서버 POST /users + 로컬 user 테이블 저장으로 바꿈

public class SignUpActivity extends BaseActivity {

    public static final String EXTRA_EMAIL = "extra_email";

    private static final int NICKNAME_MIN_LENGTH = 2;
    private static final int NICKNAME_MAX_LENGTH = 10;
    private static final int PASSWORD_MIN_LENGTH = 8;

    private EditText nicknameEditText;
    private EditText emailEditText;
    private EditText passwordEditText;
    private EditText passwordConfirmEditText;
    private CheckBox termsCheckBox;
    private Button signUpButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        applyInputScreenInsets(R.id.rootLayout);

        nicknameEditText = findViewById(R.id.nicknameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        passwordConfirmEditText = findViewById(R.id.passwordConfirmEditText);
        termsCheckBox = findViewById(R.id.termsCheckBox);

        signUpButton = findViewById(R.id.signUpButton);

        signUpButton.setOnClickListener(v -> signUp());

        passwordConfirmEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                signUp();
                return true;
            }
            return false;
        });

        TextView backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> finish());

        TextView loginText = findViewById(R.id.loginText);

        loginText.setOnClickListener(v -> finish());
    }

    private void signUp() {
        String nickname = nicknameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        String passwordConfirm = passwordConfirmEditText.getText().toString();

        if (nickname.length() < NICKNAME_MIN_LENGTH || nickname.length() > NICKNAME_MAX_LENGTH) {
            showError(nicknameEditText, "닉네임은 2~10자로 입력하세요");
            return;
        }

        if (email.isEmpty()) {
            showError(emailEditText, "이메일을 입력하세요");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError(emailEditText, "이메일 형식이 올바르지 않습니다");
            return;
        }

        if (!isValidPassword(password)) {
            showError(passwordEditText, "8자 이상, 영문과 숫자를 포함하세요");
            return;
        }

        if (!password.equals(passwordConfirm)) {
            showError(passwordConfirmEditText, "비밀번호가 일치하지 않습니다");
            return;
        }

        if (!termsCheckBox.isChecked()) {
            Toast.makeText(this, "약관에 동의해주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        // 닉네임이 user_id가 된다. 이 폰의 user 테이블에서 먼저 확인
        RunCatchDbHelper db = RunCatchDbHelper.getInstance(this);
        if (db.isMailTaken(email)) {
            showError(emailEditText, "이미 가입된 이메일입니다");
            return;
        }
        if (db.isUserIdTaken(nickname)) {
            showError(nicknameEditText, "이미 사용 중인 닉네임입니다");
            return;
        }

        // 다른 폰과 닉네임 · 이메일이 겹치면 서버 데이터가 섞이므로 서버(POST /users)에 먼저 등록
        signUpButton.setEnabled(false);
        new Thread(() -> {
            String error = registerOnServer(nickname, email);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                signUpButton.setEnabled(true);
                if (error != null) {
                    showServerError(error);
                    return;
                }
                finishSignUp(db, nickname, email, password);
            });
        }).start();
    }

    // @return 성공하면 null, 실패하면 서버 detail(user_id_taken, mail_taken) 또는 "network"
    private String registerOnServer(String nickname, String email) {
        try {
            JSONObject body = new JSONObject();
            body.put("user_id", nickname);
            body.put("mail", email);
            RunCatchApi.post("/users", body);
            return null;
        } catch (RunCatchApi.ApiException e) {
            Log.e("SignUpActivity", "서버 회원 등록 실패: " + e.code, e);
            return e.code == 409 ? e.getMessage() : "network";
        } catch (Exception e) {
            Log.e("SignUpActivity", "서버 회원 등록 실패", e);
            return "network";
        }
    }

    private void showServerError(String error) {
        if ("user_id_taken".equals(error)) {
            showError(nicknameEditText, "이미 사용 중인 닉네임입니다");
        } else if ("mail_taken".equals(error)) {
            showError(emailEditText, "이미 가입된 이메일입니다");
        } else {
            Toast.makeText(this, "서버에 연결하지 못해 회원가입할 수 없습니다", Toast.LENGTH_SHORT).show();
        }
    }

    // 서버 등록이 끝난 뒤 이 폰의 user 테이블에 저장 (비밀번호는 로컬에만 해시로 저장)
    private void finishSignUp(RunCatchDbHelper db, String nickname, String email, String password) {
        if (!db.insertUser(email, nickname, password)) {
            Toast.makeText(this, "회원가입에 실패했습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, nickname + "님, 회원가입이 완료되었습니다", Toast.LENGTH_SHORT).show();

        Intent result = new Intent();
        result.putExtra(EXTRA_EMAIL, email);
        setResult(RESULT_OK, result);
        finish();
    }

    private boolean isValidPassword(String password) {
        if (password.length() < PASSWORD_MIN_LENGTH) {
            return false;
        }

        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        return hasLetter && hasDigit;
    }

    private void showError(EditText editText, String message) {
        editText.setError(message);
        editText.requestFocus();
    }
}

package com.cookandroid.runcatch.ui.auth;

import com.cookandroid.runcatch.R;
import com.cookandroid.runcatch.core.Session;
import com.cookandroid.runcatch.core.local.RunCatchDbHelper;
import com.cookandroid.runcatch.ui.common.BaseActivity;
import com.cookandroid.runcatch.ui.home.MainActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;


/**
 *   화면 여백 코드는 BaseActivity.applyInputScreenInsets()로 옮김
 *   임시 유저 "aaa"에 덮어쓰던 로그인을 user 테이블 비교 + Session.login()으로 바꿈
 */
public class LoginActivity extends BaseActivity {

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

        Button socialLoginButton = findViewById(R.id.socialLoginButton);

        socialLoginButton.setOnClickListener(v -> showPreparing());

        TextView forgotPasswordText = findViewById(R.id.forgotPasswordText);

        forgotPasswordText.setOnClickListener(v -> showPreparing());

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
        Session.login(this, userId);

        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void showPreparing() {
        Toast.makeText(this, "준비 중입니다", Toast.LENGTH_SHORT).show();
    }
}

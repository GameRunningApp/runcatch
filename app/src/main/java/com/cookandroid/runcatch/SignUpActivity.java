package com.cookandroid.runcatch;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class SignUpActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_email";

    private static final int NICKNAME_MIN_LENGTH = 2;
    private static final int NICKNAME_MAX_LENGTH = 10;
    private static final int PASSWORD_MIN_LENGTH = 8;

    private EditText nicknameEditText;
    private EditText emailEditText;
    private EditText passwordEditText;
    private EditText passwordConfirmEditText;
    private CheckBox termsCheckBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        applySystemBarInsets();

        nicknameEditText = findViewById(R.id.nicknameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        passwordConfirmEditText = findViewById(R.id.passwordConfirmEditText);
        termsCheckBox = findViewById(R.id.termsCheckBox);

        Button signUpButton = findViewById(R.id.signUpButton);

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

    private void applySystemBarInsets() {
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
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

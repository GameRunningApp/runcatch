package com.cookandroid.runcatch.ui.auth;

import com.cookandroid.runcatch.core.local.RunCatchDbHelper;

import android.app.Activity;
import android.text.InputType;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 비밀번호 찾기 (로그인 화면의 "비밀번호를 잊으셨나요?").
 *
 * ① 메일 입력 → ② 가입된 메일인지 확인 (X: 회원가입 안내) → ③ 6자리 인증번호(난수) 생성
 * → ④ 메일 발송 → ⑤ 인증번호 입력 (X: 다시 생성) → 새 비밀번호 저장 (이전과 같은 비밀번호는 불가)
 *
 * 서버가 아직 없어서 서버 역할은 맨 아래 "임시 서버 (하드코딩)" 부분에 모았다.
 * 회원 정보는 로컬 user 테이블(RunCatchDbHelper), 비밀번호는 PasswordHash로 저장된다.
 * TODO(서버): 서버 API가 생기면 server로 시작하는 메서드 안쪽만 서버 요청으로 바꾸면 됨
 */
final class PasswordReset {

    /** 비밀번호를 바꾼 뒤 로그인 화면에 이메일을 채우도록 알려줌 */
    interface Listener {
        void onPasswordChanged(String email);
    }

    private static final String TAG = "PasswordReset";

    private static final int CODE_LENGTH = 6;
    private static final long CODE_VALID_MS = 10 * 60 * 1000L;   // 인증번호 유효시간 10분
    private static final int PASSWORD_MIN_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Activity activity;
    private final RunCatchDbHelper db;
    private final Listener listener;

    // 임시 서버가 들고 있는 값
    private String email;
    private String code;
    private long codeExpiresAt;

    private PasswordReset(Activity activity, Listener listener) {
        this.activity = activity;
        this.db = RunCatchDbHelper.getInstance(activity);
        this.listener = listener;
    }

    /** 비밀번호 찾기 시작. prefillEmail : 로그인 화면에 입력돼 있던 이메일 */
    static void start(Activity activity, String prefillEmail, Listener listener) {
        new PasswordReset(activity, listener).showEmailDialog(prefillEmail);
    }

    // ===== 화면 (대화상자 3단계) =====

    /** ① 메일 입력 */
    private void showEmailDialog(String prefillEmail) {
        EditText input = input("가입한 이메일",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setText(prefillEmail);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("비밀번호 찾기")
                .setMessage("가입한 이메일로 6자리 인증번호를 보내드려요")
                .setView(wrap(input))
                .setPositiveButton("인증번호 받기", null)
                .setNegativeButton("취소", null)
                .show();

        // 입력이 잘못됐을 때 창이 닫히지 않도록 확인 버튼을 직접 처리
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (!Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
                input.setError("이메일 형식이 올바르지 않습니다");
                return;
            }
            // ② 가입된 메일인지 확인
            if (!serverHasAccount(value)) {
                input.setError("가입되지 않은 이메일이에요. 회원가입을 먼저 해 주세요");
                return;
            }
            dialog.dismiss();
            email = value;
            showCodeDialog();
            sendNewCode();   // 메일(테스트) 창이 입력 창 위에 뜨도록 입력 창 다음에 호출
        });
    }

    /** ③ 인증번호 생성 → ④ 메일 발송 */
    private void sendNewCode() {
        code = serverCreateCode();
        codeExpiresAt = System.currentTimeMillis() + CODE_VALID_MS;
        serverSendMail(email, code);
    }

    /** ⑤ 인증번호 입력 */
    private void showCodeDialog() {
        EditText input = input("6자리 인증번호", InputType.TYPE_CLASS_NUMBER);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("인증번호 입력")
                .setMessage(email + "(으)로 보낸 인증번호를 10분 안에 입력해 주세요")
                .setView(wrap(input))
                .setPositiveButton("확인", null)
                .setNeutralButton("다시 받기", null)
                .setNegativeButton("취소", null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.length() != CODE_LENGTH) {
                input.setError("6자리 숫자를 입력하세요");
                return;
            }
            if (serverCheckCode(value)) {
                dialog.dismiss();
                showNewPasswordDialog();
                return;
            }
            // X: 틀렸거나 만료 → 인증번호 다시 생성
            input.setText("");
            input.setError("인증번호가 맞지 않거나 만료되었어요. 새 인증번호를 보냈어요");
            sendNewCode();
        });

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            input.setText("");
            sendNewCode();
        });
    }

    /** 인증 성공 → 새 비밀번호 저장 */
    private void showNewPasswordDialog() {
        EditText password = input("새 비밀번호 (8자 이상, 영문 + 숫자)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirm = input("새 비밀번호 확인",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(password);
        box.addView(confirm);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("새 비밀번호 설정")
                .setView(wrap(box))
                .setPositiveButton("변경", null)
                .setNegativeButton("취소", null)
                .setCancelable(false)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String newPassword = password.getText().toString();
            if (!isValidPassword(newPassword)) {
                password.setError("8자 이상, 영문과 숫자를 포함하세요");
                return;
            }
            if (!newPassword.equals(confirm.getText().toString())) {
                confirm.setError("비밀번호가 일치하지 않습니다");
                return;
            }
            // 이전과 같은 비밀번호면 거절 (인증은 그대로 두어 바로 다시 입력 가능)
            if (serverIsSamePassword(email, newPassword)) {
                password.setError("이전과 같은 비밀번호는 사용할 수 없어요");
                return;
            }
            if (!serverChangePassword(email, newPassword)) {
                Toast.makeText(activity, "비밀번호를 바꾸지 못했어요", Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            Toast.makeText(activity, "비밀번호가 변경되었어요. 새 비밀번호로 로그인해 주세요",
                    Toast.LENGTH_LONG).show();
            listener.onPasswordChanged(email);
            code = null;   // 다 쓴 인증번호는 지움
        });
    }

    /** 회원가입 화면(SignUpActivity)과 같은 규칙 : 8자 이상, 영문 · 숫자 포함 */
    private boolean isValidPassword(String value) {
        if (value.length() < PASSWORD_MIN_LENGTH) return false;
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : value.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }
        return hasLetter && hasDigit;
    }

    private EditText input(String hint, int inputType) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setSingleLine(true);
        return input;
    }

    /** 대화상자 안 입력칸 좌우 여백 */
    private FrameLayout wrap(View view) {
        int side = (int) (24 * activity.getResources().getDisplayMetrics().density);
        FrameLayout frame = new FrameLayout(activity);
        frame.setPadding(side, 0, side, 0);
        frame.addView(view);
        return frame;
    }

    // =====================================================================
    // 임시 서버 (하드코딩)
    // TODO(서버): 서버 API가 생기면 아래 메서드 안쪽만 서버 요청으로 바꾸면 됨
    //   - 회원 정보는 로컬 user 테이블 (RunCatchDbHelper)
    //   - 메일 발송은 할 수 없어서 인증번호를 화면(대화상자)과 Logcat에 표시
    // =====================================================================

    /** ② user 테이블에 이 메일이 있는지 */
    private boolean serverHasAccount(String mail) {
        return db.isMailTaken(mail);
    }

    /** ③ 6자리 난수 인증번호 */
    private String serverCreateCode() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    /** ④ 메일 발송 (하드코딩: 실제 메일 대신 화면과 Logcat에 표시) */
    private void serverSendMail(String mail, String newCode) {
        Log.d(TAG, "[비밀번호 찾기] " + mail + " 인증번호: " + newCode);
        new MaterialAlertDialogBuilder(activity)
                .setTitle("📧 메일 도착 (테스트)")
                .setMessage("서버 연결 전이라 메일 대신 여기에 보여드려요.\n\n인증번호 : " + newCode)
                .setPositiveButton("확인", null)
                .show();
    }

    /** ⑤ 입력한 인증번호가 맞는지, 10분이 안 지났는지 */
    private boolean serverCheckCode(String value) {
        return code != null
                && System.currentTimeMillis() <= codeExpiresAt
                && code.equals(value);
    }

    /** 새 비밀번호가 지금 비밀번호와 같은지 (지금 비밀번호로 로그인되면 같은 것) */
    private boolean serverIsSamePassword(String mail, String newPassword) {
        return db.findUserId(mail, newPassword) != null;
    }

    /** 새 비밀번호를 user 테이블에 해시로 저장 */
    private boolean serverChangePassword(String mail, String newPassword) {
        return db.updatePassword(mail, newPassword);
    }
}

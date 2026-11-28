# 간편 로그인 · 비밀번호 찾기

로그인 화면의 **카카오 · 구글 간편 로그인**과 **비밀번호 찾기**입니다.
구조 정리(PR #30)의 로컬 `user` 테이블 · `PasswordHash` · `Session` · 서버 `POST /users` 위에 만들었습니다.

## 파일 구조

| 파일 | 역할 |
|---|---|
| `app/.../ui/auth/SocialLogin.java` | 카카오 · 구글 로그인. 성공하면 두 방식 모두 같은 형태(`SocialAccount`)로 돌려줌 |
| `app/.../ui/auth/PasswordReset.java` | 비밀번호 찾기 대화상자 3단계 + 임시 서버(하드코딩) |
| `app/.../ui/auth/LoginActivity.java` | 버튼 연결, 간편 로그인 첫 가입(닉네임 입력 → `POST /users` → `user` 테이블) |
| `core/.../local/RunCatchDbHelper.java` | `insertSocialUser`, `findUserIdByMail`, `updatePassword` 추가 |
| `app/src/main/res/layout/activity_login.xml` | "간편 로그인" 버튼 → 카카오 · 구글 버튼 두 개 |
| `app/src/main/res/drawable/` | `bg_kakao_button`, `bg_google_button`, `ic_kakao_symbol` (카카오 공식 심볼) |
| `app/build.gradle.kts` | `local.properties`의 앱 키를 `BuildConfig`로 읽음, 카카오 · 구글 라이브러리 |
| `settings.gradle.kts` | 카카오 SDK 저장소 |
| `app/src/main/AndroidManifest.xml` | 카카오계정(웹) 로그인 후 앱으로 돌아오는 `AuthCodeHandlerActivity` |

## 처음 설정 (팀원마다 한 번)

### 1. 앱 키를 `local.properties`에 넣기

프로젝트 루트의 `local.properties`(GitHub에 올라가지 않는 파일)에 두 줄을 추가합니다.
값은 팀 단톡방에서 받습니다. **GitHub · 노션 등 공개된 곳에 키를 올리지 마세요.**

```properties
KAKAO_NATIVE_APP_KEY=카카오_네이티브_앱_키
GOOGLE_WEB_CLIENT_ID=구글_웹_애플리케이션_클라이언트_ID
```

- 카카오 : 카카오 디벨로퍼스 → 내 애플리케이션 → 앱 → **플랫폼 키 → 네이티브 앱 키**
- 구글 : Google Cloud 콘솔 → API 및 서비스 → 사용자 인증 정보 → **웹 애플리케이션** 클라이언트 ID
  (Android 클라이언트 ID가 아님)

키가 비어 있으면 버튼을 눌렀을 때 "설정이 필요해요" 안내가 뜹니다.

### 2. 내 노트북의 키 해시 · SHA-1 등록

노트북마다 디버그 키가 달라서, 등록하지 않으면 그 노트북에서 빌드한 앱은 간편 로그인이 실패합니다.

```bash
# 카카오 : 키 해시
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore \
  -storepass android -keypass android | openssl sha1 -binary | openssl base64

# 구글 : SHA-1 (출력의 "SHA1:" 줄)
keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore \
  -storepass android -keypass android
```

- 카카오 디벨로퍼스 → 앱 → 플랫폼 → **Android**에 키 해시 추가 (패키지명 `com.cookandroid.runcatch`)
- Google Cloud 콘솔 → **Android** OAuth 클라이언트에 SHA-1 추가 (패키지명 `com.cookandroid.runcatch`)

콘솔에는 관리자만 들어갈 수 있으니, 출력값을 담당자(김시연)에게 보내 주세요.
출시(원스토어) 전에는 출시용 키의 키 해시 · SHA-1도 등록해야 합니다.

## 간편 로그인 흐름

```
[카카오 / 구글 버튼]
   ↓ SocialLogin
   - 카카오 : 카카오톡 앱 로그인 → 안 되면 카카오계정(웹) 로그인 → me()로 회원번호 · 닉네임 · 이메일
   - 구글   : Credential Manager 계정 선택 창 → ID 토큰 · 이메일 · 이름
   ↓ LoginActivity.onSocialAccount
user 테이블에 이 계정(mail)이 있나?
   ├─ 있음 → Session.login → 홈
   └─ 없음 → 닉네임 입력 창 (2~10자, 카카오 · 구글 닉네임으로 미리 채움)
             → 서버 POST /users (닉네임 · 이메일 중복 확인)
             → user 테이블 저장 (insertSocialUser) → Session.login → 홈
```

### 계정 구분 (`user.mail`)

| 경우 | `mail`에 저장되는 값 |
|---|---|
| 구글 | 구글 계정 이메일 |
| 카카오 + 카카오가 확인한 이메일 | 그 이메일 |
| 카카오 + 확인된 이메일 없음 | `kakao_회원번호@kakao.runcatch` |

- 확인되지 않은 이메일로는 계정을 찾지 않습니다. (남의 계정에 들어가는 것 방지)
- 간편 로그인 계정은 `password`가 `NULL`이라 이메일 · 비밀번호 로그인은 되지 않습니다.
- 같은 이메일로 이메일 회원가입을 해 둔 경우, 구글(또는 확인된 카카오 이메일) 로그인은 그 계정으로 로그인됩니다.

## 비밀번호 찾기 흐름

로그인 화면 "비밀번호를 잊으셨나요?" → 대화상자 3단계

1. **이메일 입력** → `user` 테이블에 없으면 "가입되지 않은 이메일이에요" 안내
2. **6자리 인증번호** 생성 → 메일 대신 **"📧 메일 도착 (테스트)" 창**과 Logcat(`PasswordReset`)에 표시
3. **인증번호 입력** : 10분 동안 유효, 틀리거나 만료되면 새 인증번호 생성, "다시 받기" 버튼
4. **새 비밀번호** : 8자 이상 + 영문 + 숫자, 확인 칸 일치
   - **이전과 같은 비밀번호면 거절** ("이전과 같은 비밀번호는 사용할 수 없어요")
   - 저장은 `PasswordHash`로 암호화 (`updatePassword`)
5. 완료되면 로그인 화면에 이메일이 채워짐

서버가 아직 없어서 서버 역할은 `PasswordReset.java` 맨 아래 **"임시 서버 (하드코딩)"** 부분에 모았습니다.

| 메서드 | 지금 (하드코딩) | 서버가 생기면 |
|---|---|---|
| `serverHasAccount` | `user` 테이블에 이메일이 있는지 | 서버에 가입 여부 확인 |
| `serverCreateCode` | 6자리 난수 생성 | 서버가 생성 · 보관 |
| `serverSendMail` | 테스트 창 · Logcat에 표시 | 서버가 실제 메일 발송 |
| `serverCheckCode` | 앱이 기억한 번호와 비교 | 서버가 확인 |
| `serverIsSamePassword` | 새 비밀번호로 `findUserId`가 되면 같은 것 | 서버가 확인 |
| `serverChangePassword` | `updatePassword` | 서버에 저장 |

## 실행 · 테스트

1. Android Studio : **File → Sync Project with Gradle Files** → Run
   - 예전 `backend` 모듈 오류가 나면 Sync 후 다시 Run (그래도 나면 Invalidate Caches)
2. 간편 로그인 첫 가입은 서버(`POST /users`)가 필요합니다. 서버 실행은 [server/README.md](../server/README.md)를 따르고,
   에뮬레이터에서는 `adb reverse tcp:8000 tcp:8000`을 입력합니다.
3. 비밀번호 찾기는 서버 없이 동작합니다. 먼저 이메일로 회원가입한 뒤 테스트합니다.

| 테스트 | 기대 결과 |
|---|---|
| 카카오 · 구글 첫 로그인 | 닉네임 입력 창 → "○○님, 카카오 계정으로 가입했어요!" → 홈 |
| 같은 계정으로 다시 로그인 | 바로 "○○님, 카카오 계정으로 로그인했어요" → 홈 |
| 이미 쓰는 닉네임 입력 | "이미 사용 중인 닉네임입니다" |
| 서버 꺼진 상태로 첫 간편 로그인 | "서버에 연결하지 못해 가입할 수 없습니다" |
| 가입 안 된 이메일로 비밀번호 찾기 | "가입되지 않은 이메일이에요" |
| 인증번호 틀리게 입력 | 새 인증번호가 다시 뜸 |
| 새 비밀번호에 지금 비밀번호 입력 | "이전과 같은 비밀번호는 사용할 수 없어요" |
| 비밀번호 변경 후 새 비밀번호로 로그인 | 로그인 성공, 예전 비밀번호는 실패 |

## 남은 일 (TODO)

- 서버 로그인 API가 생기면 카카오 액세스 토큰 · 구글 ID 토큰(`SocialAccount.token`)을 서버로 보내 진짜인지 확인
- 서버가 생기면 `PasswordReset`의 `server…` 메서드 안쪽을 서버 요청으로 교체, 팀 메일 계정으로 실제 메일 발송
- 로그아웃 · 계정 정보는 설정 화면(윤현수)과 연결

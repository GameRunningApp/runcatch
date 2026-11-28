# 🏃 RUNCATCH 런캐치

> **달리고 · 기록하고 · 수집하세요**
> 러닝을 하면 달린 장소와 거리에 따라 캐릭터를 모으고 키우는 러닝 앱입니다.
> 비슷한 수준의 러너끼리 그룹을 만들어 함께 응원하고, 리그와 레이드로 경쟁 · 협력합니다.

2026년 국민대학교 모바일프로그래밍 02분반 **3팀** 프로젝트

---

## 주요 기능

### 1. 러닝 측정
- GPS로 거리 · 시간 · 페이스를 재고, **MapLibre 지도**에 달린 경로를 그립니다.
- **백그라운드 측정** : 화면을 끄거나 다른 앱을 써도 측정이 이어지고, 알림창에 거리 · 페이스가 보입니다.
- **잠금화면** : 휴대폰 잠금을 풀지 않아도 러닝 화면과 기록을 확인할 수 있습니다.
- 정확도가 낮거나 튀는 GPS 좌표를 걸러서 거리가 갑자기 늘지 않게 합니다.

### 2. 캐릭터 수집 · 성장
- **환경 분석** : 달린 경로가 공원 · 숲 · 강 · 호수 · 도심 중 어디를 지났는지 분석합니다.
- **캐릭터 획득** : 장소 · 거리 · 연속 러닝 · 리그 조건을 채우면 캐릭터를 얻습니다. (서버에서 판정)
  - 물가(수달 · 오리), 숲(다람쥐 · 고슴도치), 도심(고양이 · 비둘기), 산(산양 · 독수리),
    거리(토끼 · 여우 · 곰), 연속(삐약이 · 강아지), 리그(유니콘) 등
- **경험치 · 레벨** : 러닝을 마치면 대표 캐릭터가 경험치를 얻고, 결과 화면에서 레벨 진행바와 LEVEL UP을 보여줍니다.
- **도감** : 모은 캐릭터를 카테고리별로 확인합니다.
- **MY** : 보유한 캐릭터가 화면 위를 걸어다니고, 손으로 쓰다듬으면 반응합니다.

### 3. 기록
- 이번 주 거리 · 시간, 요일별 막대 그래프, 최근 기록 목록
- 러닝 그래프에서 X · Y축(거리 · 시간 · 페이스 · 속도)을 골라서 볼 수 있습니다.
- 홈 화면에 연속 러닝 일수를 보여줍니다.

### 4. 커뮤니티
- **그룹핑** : 최근 3개월 동안 3번 이상 달린 러너를 캐릭터 레벨 합과 평균 페이스로 묶습니다.
  (K-means++, 그룹당 최대 20명, 매월 1일 자동 실행)
- **피드** : 그룹원의 러닝 기록에 응원하기와 이모지 반응을 보냅니다. (휴대폰 전체 이모지 선택 가능)
- **월간 리그** : 그룹 안 순위와 월말 보상
- **레이드** : 그룹원이 함께 1,000km를 채우면 모두의 대표 캐릭터가 경험치를 받습니다.

### 5. 계정
- 이메일 회원가입 · 로그인 (비밀번호는 `PasswordHash`로 암호화해서 저장)
- **카카오 · 구글 간편 로그인**, **비밀번호 찾기** (인증번호 확인 후 새 비밀번호 설정)
  → 자세한 내용은 [docs/LOGIN_README.md](docs/LOGIN_README.md)

---

## 화면 구성

| 화면 | 설명 |
|---|---|
| 로그인 · 회원가입 | 이메일 로그인, 카카오 · 구글 간편 로그인, 비밀번호 찾기 |
| 홈 | 대표 캐릭터, 다음 캐릭터까지 진행바, 연속 러닝 일수, 최근 러닝 |
| 러닝 | 스톱워치, 거리 · 페이스, 지도 경로, 일시정지 · 종료 |
| 캐릭터 획득 → 결과 | 새 캐릭터 등장 → 기록 요약 · 경험치 · 레벨 |
| 기록 | 주간 그래프, 러닝 그래프, 최근 기록 |
| 도감 | 카테고리별 캐릭터 카드 (획득 · 잠김) |
| 커뮤니티 | 내 그룹, 피드 · 월간 리그 · 레이드 탭 |
| MY | 보유 캐릭터가 돌아다니는 공간, 쓰다듬기 반응 |

---

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 앱 | Java, XML, Android (minSdk 24 · targetSdk 37), Material Components |
| 위치 · 지도 | Google Play Services Location (Fused Location), MapLibre Android SDK |
| 앱 로컬 DB | SQLite (`core` 모듈 `RunCatchDbHelper`) |
| 간편 로그인 | Kakao SDK v2-user, AndroidX Credential Manager + Google ID |
| 기타 | androidx Emoji Picker, Foreground Service |
| 서버 | Python, FastAPI, Uvicorn |
| 서버 DB | MySQL (utf8mb4) |
| 그룹핑 · 스케줄러 | NumPy, SciPy (K-means++), APScheduler |
| 협업 | GitHub (브랜치 → PR → 리뷰 후 main 병합), Notion, KakaoTalk |

---

## 프로젝트 구조

```
runcatch/
├── app/                         # 안드로이드 앱
│   └── src/main/java/com/cookandroid/runcatch/
│       ├── ui/                  # 화면 (auth, home, running, record, collection, community, my, common)
│       ├── run/                 # 러닝 종료 처리, 경험치 계산 결과, 캐릭터 획득 요청
│       ├── service/             # 백그라운드 측정 (RunningService, RunTracker, RunningNotification)
│       ├── environment/         # 공원 · 숲 · 강 · 호수 데이터와 경로 환경 분석
│       ├── remote/              # 서버 API 호출 (RunCatchApi)
│       └── format/              # 거리 · 시간 · 페이스 표시 형식
├── core/                        # 화면과 상관없는 공통 코드
│   └── src/main/java/com/cookandroid/runcatch/core/
│       ├── domain/              # 캐릭터, 경험치 규칙, 연속 일수, 비밀번호 해시 등
│       ├── local/               # 앱 로컬 SQLite DB (RunCatchDbHelper)
│       └── Session.java         # 로그인한 유저
├── server/                      # FastAPI 서버 (자세한 내용은 server/README.md)
│   ├── routers/                 # API
│   ├── services/                # 캐릭터 판정 · 반응 · 그룹핑 · 레이드 규칙
│   └── tests/
└── docs/                        # 기능별 문서
```

---

## 실행 방법

### 1. 서버
MySQL을 준비하고 서버를 실행합니다. (처음 한 번 DB 만들기는 [server/README.md](server/README.md) 참고)

```bash
cd server
python -m venv venv
# Mac
venv/bin/python -m pip install -r requirements.txt
venv/bin/python -m uvicorn main:app --reload
# Windows
venv\Scripts\python -m pip install -r requirements.txt
venv\Scripts\python -m uvicorn main:app --reload
```

API 테스트(Swagger) : http://127.0.0.1:8000/docs

### 2. 앱
1. Android Studio로 프로젝트를 열고 **File → Sync Project with Gradle Files**
2. 간편 로그인을 쓰려면 `local.properties`에 앱 키를 넣습니다. ([docs/LOGIN_README.md](docs/LOGIN_README.md) 참고)
3. 에뮬레이터에서 노트북 서버에 접속하도록 연결합니다.
   ```bash
   adb reverse tcp:8000 tcp:8000
   ```
4. Run ▶

> 에뮬레이터에서 GPS를 테스트할 때는 Google Play가 들어 있는 에뮬레이터를 쓰고,
> Extended Controls → Location에서 GPX 경로 파일을 재생합니다.

---

## 팀원

| 이름 | 역할 | 맡은 기능 | GitHub |
|---|---|---|---|
| 조성민 | 팀장 · SM · 구조 | 앱 · 서버 구조 정리, 로컬 DB · 회원 DB 연결, 비밀번호 해시, MY 캐릭터 모션, PR 리뷰 · 병합 | [@jo-sungmin3675](https://github.com/jo-sungmin3675) |
| 김시연 | 프론트엔드 · 계정 | 스톱워치, 캐릭터 획득 화면, 경험치 · 레벨, 커뮤니티 화면 · 응원 · 이모지 반응, 간편 로그인 · 비밀번호 찾기, 설문조사 · 회의록 | [@syeon-311](https://github.com/syeon-311) |
| 윤현수 | GPS · 커뮤니티 서버 | GPS · 지도, 결과 화면, 환경 분석, 커뮤니티 그룹핑 · 참가 · 레이드, 설정 화면 | |
| 이천홍 | 러닝 측정 | 백그라운드 측정, 러닝 알림, 잠금화면 | [@leechuanhung](https://github.com/leechuanhung) |
| 테물렌 | 프론트엔드 UI | 홈 · 로그인 · 회원가입 화면, 도감, 기록 화면 · 러닝 그래프 | [@temulnnn](https://github.com/temulnnn) |
| 쿠르만벡 | 서버 | FastAPI 서버 구축, MySQL 전환, 장소 캐릭터 판정, 반응 · 응원 API, 서버 안정성 | [@Nishanovvv222](https://github.com/Nishanovvv222) |

---

## 협업 방식

- 기능마다 `feature/기능이름`, 버그는 `fix/버그이름` 브랜치를 만들어 작업합니다.
- PR을 올리면 팀장이 리뷰한 뒤 `main`에 병합합니다.
- 스크럼 회의(주 2회)와 스프린트 리뷰(교수님 피드백) · 회고로 진행 상황을 공유합니다.
- 앱 키 · 비밀번호 같은 값은 `local.properties` 등 GitHub에 올라가지 않는 파일에만 둡니다.

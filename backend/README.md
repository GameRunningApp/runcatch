# RUN CATCH Backend

FastAPI 서버. 러닝 기록, 통계, 캐릭터 데이터를 저장하고 앱에 제공합니다.
DB는 현재 SQLite(`runcatch.db`, 서버 실행 시 자동 생성)이고, 이후 MySQL로 변경 예정입니다.

## 실행 방법 (Windows)

```
python -m venv venv
venv\Scripts\python -m pip install -r requirements.txt
venv\Scripts\python -m uvicorn main:app --reload
```

Swagger(API 테스트): http://127.0.0.1:8000/docs

## 단위

- distance: 미터 (float)
- duration: 초 (float)
- started_at: 러닝 시작 시간. 오프라인으로 저장했다가 나중에 보내도 날짜가 맞도록 앱에서 보냄 (없으면 서버가 받은 시간)

## API

| Method | URL | 설명 |
|---|---|---|
| POST | /runs | 러닝 기록 저장. 레벨/경험치 계산, 새로 얻은 캐릭터를 `new_characters`로 반환 |
| GET | /runs?user_id= | 러닝 기록 목록 (최신순) |
| GET | /stats/{user_id} | 누적 거리/시간/횟수, 대표 캐릭터, 다음 캐릭터 |
| GET | /stats/{user_id}/weekly | 최근 7일 날짜별 거리 (그래프용) |
| GET | /characters | 전체 캐릭터 목록 |
| GET | /users/{user_id}/characters | 내 캐릭터 도감 (레벨, 경험치, 대표 여부) |
| PUT | /users/{user_id}/main-character/{character_id} | 대표 캐릭터 변경 |

## 캐릭터 규칙 (임시, 캐릭터 아이디어 확정되면 변경)

- 누적 거리로 캐릭터 획득: 삐약이 2km, 토끼 10km, 거북이 30km
- 처음 얻은 캐릭터가 자동으로 대표 캐릭터
- 러닝 1m = 대표 캐릭터 경험치 1, 5000마다 레벨업
- 레벨과 캐릭터는 서버에서 계산 (앱은 기록만 전송)
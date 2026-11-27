# RUN CATCH Backend

FastAPI 서버. 러닝 기록, 통계, 캐릭터 데이터를 저장하고 앱에 제공합니다.
DB는 MySQL을 사용합니다. 테이블은 서버 실행 시 자동으로 만들어집니다.

## 폴더 구조

| 파일 | 역할 |
|---|---|
| `main.py` | 앱 생성, 시작 시 초기화(테이블 생성, 월간 그룹 배정 스케줄러), 라우터 등록 |
| `config.py` | DB 접속 정보, 시간대(KST), 경험치·그룹 인원 같은 설정값 |
| `db.py` | DB 연결 (`get_db`, 자동 commit/rollback/close 하는 `db_cursor`) |
| `schema.py` | 테이블 생성, `SCHEMA_VERSION` |
| `models.py` | API 요청/응답 모델 |
| `characters.py` | 캐릭터 목록과 장소 캐릭터 조건 (앱 `RunCharacter.ALL`과 맞춤) |
| `routers/` | API (`users`, `runs`, `characters`, `reactions`, `community_grouping`, `community_raid`) |
| `services/` | 업무 규칙 (`rewards` 획득 판정·경험치, `reactions` 반응·응원 집계, `grouping` 그룹 배정, `raid` 레이드) |
| `tests/` | `grouping_algorithm_test` (DB 없이 실행), `community_raid_test` (DB + 서버 필요) |

테스트 실행 (server 폴더에서): `venv\Scripts\python -m tests.grouping_algorithm_test`

## MySQL 준비 (처음 한 번)

MySQL 8을 설치한 뒤, MySQL에 root로 접속해서 아래를 실행합니다.

```sql
CREATE DATABASE runcatch CHARACTER SET utf8mb4;
CREATE USER 'runcatch'@'localhost' IDENTIFIED BY 'runcatch1234';
GRANT ALL ON runcatch.* TO 'runcatch'@'localhost';
FLUSH PRIVILEGES;
```

접속 정보는 환경 변수로 바꿀 수 있습니다: `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`
(없으면 localhost:3306, runcatch / runcatch1234, DB 이름 runcatch)

## 실행 방법 (Windows)

```
python -m venv venv
venv\Scripts\python -m pip install -r requirements.txt
venv\Scripts\python -m uvicorn main:app --reload
```

Swagger(API 테스트): http://127.0.0.1:8000/docs

## 단위 (앱 로컬 DB `running_record`와 같음)

- running_number: 러닝 기록 번호 (서버가 부여)
- run_date: 러닝 시작 시각, epoch ms (int). 오프라인으로 저장했다가 나중에 보내도 날짜가 맞도록 앱에서 보냄 (없으면 서버가 받은 시각)
- environment: 러닝 환경. 앱 `RunningService.getFinalEnvironment()` 값 그대로 (CITY, PARK, FOREST, RIVER, LAKE). 없으면 장소 캐릭터는 판단하지 않음
- distance: 미터 (float)
- duration: 초 (float)
- pace: 초/km, avg_speed·max_speed: km/h
- GPS 경로는 서버로 보내지 않음 (앱 로컬 DB에만 저장)

테이블 구조를 바꾸면 `schema.py`의 `SCHEMA_VERSION`을 올린다. 서버 시작 시 DB 버전(`schema_version` 테이블)이 낮으면 테이블을 지우고 새로 만든다 (개발 단계).

## API

| Method | URL | 설명 |
|---|---|---|
| POST | /users | 회원 등록 `{user_id, mail}` (앱 회원가입). 닉네임 · 이메일이 겹치면 409 `user_id_taken` / `mail_taken` |
| POST | /runs | 러닝 기록 저장. 레벨/경험치 계산, 새로 얻은 캐릭터를 `new_characters`로 반환 |
| GET | /runs?user_id= | 러닝 기록 목록 (최신순) |
| GET | /stats/{user_id} | 누적 거리/시간/횟수, 대표 캐릭터, 다음 캐릭터 |
| GET | /stats/{user_id}/weekly | 최근 7일 날짜별 거리 (그래프용) |
| GET | /characters | 전체 캐릭터 목록 (character_id 순) |
| GET | /users/{user_id}/characters | 내 캐릭터 도감 (레벨, 경험치, 대표 여부) |
| PUT | /users/{user_id}/main-character/{character_id} | 대표 캐릭터 변경 |
| GET | /runs/{running_number}/reactions?user_id= | 러닝 기록의 반응(이모지별 개수, 내가 눌렀는지)과 응원 수 |
| POST | /runs/{running_number}/reactions | 반응 추가 `{user_id, emoji}`. 같은 사람이 같은 이모지는 한 번만 |
| DELETE | /runs/{running_number}/reactions?user_id=&emoji= | 반응 취소 |
| POST | /runs/{running_number}/cheer | 응원하기 `{user_id}`. 한 기록에 한 사람당 한 번 |

반응 · 응원 API는 모두 바뀐 뒤의 결과(`reactions`, `cheer_count`, `cheered_by_me`)를 돌려줘서, 앱은 응답으로 칩 숫자를 다시 그리면 됩니다.

커뮤니티 피드(`GET /community-groups/{user_id}/feed`)의 글마다 같은 형식의 `reactions`, `cheer_count`, `cheered_by_me`가 들어 있습니다 (예전의 `clap_count` 등 0 고정값은 없앰).
이모지는 휴대폰 전체 이모지를 그대로 저장하기 위해 utf8mb4 + `utf8mb4_bin`(이모지끼리 구분) 컬럼에 저장합니다.

## 캐릭터 규칙 (임시)

- 캐릭터 목록은 앱 `RunCharacter.ALL`과 같은 14종. `character_id`(정수)도 앱의 `RunCharacter.id`와 같다. 앱에서 캐릭터를 바꾸면 `characters.py`의 `CHARACTERS`도 같이 바꾼다
- `category`는 앱 `CharacterCategory` 이름 (WATER, FOREST, CITY, MOUNTAIN, DISTANCE, STREAK, LEAGUE)
- 누적 거리 캐릭터: 토끼(9) 10km, 여우(10) 20km, 곰(11) 30km (`required_distance`)
- 장소 캐릭터 (`characters.py`의 `PLACE_CONDITIONS`, 러닝 기록의 environment로 판단)
  - 수달(1): RIVER에서 한 번에 1km 이상, 다람쥐(3): PARK 1km 이상, 고양이(5): CITY 1km 이상
  - 오리(2): LAKE 러닝 3회, 비둘기(6): CITY 러닝 3회
  - 고슴도치(4): FOREST 누적 5km
- 산양·독수리(MOUNTAIN)는 앱이 아직 산을 판단하지 않아서 없음. 연속·리그 캐릭터도 아직 서버에서 판단하지 않음
- 처음 얻은 캐릭터가 자동으로 대표 캐릭터
- 러닝 1m = 대표 캐릭터 경험치 1, 5000마다 레벨업
- 레벨과 캐릭터는 서버에서 계산 (앱은 기록만 전송)
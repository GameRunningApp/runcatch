````
# RUN CATCH Backend

FastAPI 서버. 러닝 기록, 통계, 캐릭터 데이터를 저장하고 앱에 제공합니다.
DB는 MySQL을 사용합니다. 테이블은 서버 실행 시 자동으로 만들어집니다.

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
- distance: 미터 (float)
- duration: 초 (float)
- pace: 초/km, avg_speed·max_speed: km/h
- GPS 경로는 서버로 보내지 않음 (앱 로컬 DB에만 저장)

테이블 구조를 바꾸면 `main.py`의 `SCHEMA_VERSION`을 올린다. 서버 시작 시 DB 버전(`schema_version` 테이블)이 낮으면 테이블을 지우고 새로 만든다 (개발 단계).

## API

| Method | URL | 설명 |
|---|---|---|
| POST | /runs | 러닝 기록 저장. 레벨/경험치 계산, 새로 얻은 캐릭터를 `new_characters`로 반환 |
| GET | /runs?user_id= | 러닝 기록 목록 (최신순) |
| GET | /stats/{user_id} | 누적 거리/시간/횟수, 대표 캐릭터, 다음 캐릭터 |
| GET | /stats/{user_id}/weekly | 최근 7일 날짜별 거리 (그래프용) |
| GET | /characters | 전체 캐릭터 목록 (character_id 순) |
| GET | /users/{user_id}/characters | 내 캐릭터 도감 (레벨, 경험치, 대표 여부) |
| PUT | /users/{user_id}/main-character/{character_id} | 대표 캐릭터 변경 |

## 캐릭터 규칙 (임시)

- 캐릭터 목록은 앱 `RunCharacter.ALL`과 같은 14종. `character_id`(정수)도 앱의 `RunCharacter.id`와 같다. 앱에서 캐릭터를 바꾸면 `main.py`의 `CHARACTERS`도 같이 바꾼다
- `category`는 앱 `CharacterCategory` 이름 (WATER, FOREST, CITY, MOUNTAIN, DISTANCE, STREAK, LEAGUE)
- 지금 서버가 판단하는 획득 조건은 누적 거리뿐: 토끼(9) 10km, 여우(10) 20km, 곰(11) 30km (`required_distance`)
- 장소·연속·리그 캐릭터는 아직 서버에서 판단하지 않음 (`required_distance`가 null)
- 처음 얻은 캐릭터가 자동으로 대표 캐릭터
- 러닝 1m = 대표 캐릭터 경험치 1, 5000마다 레벨업
- 레벨과 캐릭터는 서버에서 계산 (앱은 기록만 전송) 

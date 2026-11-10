import os
from datetime import date, datetime, timedelta
from typing import Optional

import pymysql
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

# 접속 정보는 환경 변수로 바꿀 수 있음 (없으면 로컬 MySQL 기본값)
DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USER", "runcatch"),
    "password": os.getenv("DB_PASSWORD", "runcatch1234"),
    "database": os.getenv("DB_NAME", "runcatch"),
    "charset": "utf8mb4",
    "cursorclass": pymysql.cursors.DictCursor,
}
EXP_PER_LEVEL = 5000

# 테이블 구조를 바꾸면 올린다. DB의 버전이 더 낮으면 테이블을 지우고 새로 만든다 (개발 단계)
# 2: 캐릭터를 앱의 14종(정수 id)으로, running_record를 앱 구조로 변경
# 3: SQLite에서 MySQL로 변경
SCHEMA_VERSION = 3

app = FastAPI()


def get_db():
    return pymysql.connect(**DB_CONFIG)


# 앱 RunCharacter.ALL과 같은 목록. character_id는 앱의 RunCharacter.id
# category는 앱 CharacterCategory 이름 (WATER, FOREST, CITY, MOUNTAIN, DISTANCE, STREAK, LEAGUE)
# required_distance(m)는 누적 거리 캐릭터만 값이 있고, 나머지 조건은 아직 서버에서 판단하지 않음
CHARACTERS = [
    (1, "수달", "🦦", "WATER", "강·하천 근처 1Km 이상 러닝",
     "물가를 따라 달려보세요", "강바람을 좋아하는 러닝 메이트", None),
    (2, "오리", "🦆", "WATER", "호수 근처 러닝 3회",
     "잔잔한 호숫가를 자주 찾아보세요", "호숫가 산책로의 단골 손님", None),
    (3, "다람쥐", "🐿️", "FOREST", "공원·산책로 1Km 이상 러닝",
     "나무가 많은 곳을 달려보세요", "작지만 꾸준한 러너", None),
    (4, "고슴도치", "🦔", "FOREST", "숲길 누적 5Km 러닝",
     "숲길을 여러 번 달려보세요", "천천히 그러나 끝까지 달리는 친구", None),
    (5, "고양이", "🐈", "CITY", "도심 거리 1Km 이상 러닝",
     "도시 한복판을 달려보세요", "밤거리 러닝을 즐기는 러너", None),
    (6, "비둘기", "🕊️", "CITY", "번화가 러닝 3회",
     "사람이 많은 거리를 자주 달려보세요", "도시 어디서나 만나는 친구", None),
    (7, "산양", "🐐", "MOUNTAIN", "오르막길 1Km 이상 러닝",
     "언덕길에 도전해보세요", "오르막을 두려워하지 않는 러너", None),
    (8, "독수리", "🦅", "MOUNTAIN", "산길 누적 5Km 러닝",
     "높은 곳을 여러 번 달려보세요", "정상에서 내려다보는 걸 좋아하는 친구", None),
    (9, "토끼", "🐰", "DISTANCE", "누적 거리 10Km 달성",
     "조금만 더 달려보세요", "가볍게 뛰는 걸 좋아하는 친구", 10000),
    (10, "여우", "🦊", "DISTANCE", "누적 거리 20Km 달성",
     "꾸준히 거리를 쌓아보세요", "페이스 조절의 달인", 20000),
    (11, "곰", "🐻", "DISTANCE", "누적 거리 30Km 달성",
     "멀리 보고 달려보세요", "든든한 장거리 러너", 30000),
    (12, "삐약이", "🐥", "STREAK", "3일 연속 러닝",
     "며칠 연속으로 달려보세요", "처음 만나는 러닝 동료", None),
    (13, "강아지", "🐶", "STREAK", "7일 연속 러닝",
     "일주일 동안 하루도 빠짐없이 달려보세요", "언제나 함께 달려주는 친구", None),
    (14, "유니콘", "🦄", "LEAGUE", "월간 리그 1위",
     "이번 달 리그 정상에 도전해보세요", "리그 1위에게만 나타나는 전설의 러너", None),
]


conn = get_db()
with conn.cursor() as cur:
    # MySQL에는 SQLite의 PRAGMA user_version이 없어서 버전을 테이블에 저장
    cur.execute("CREATE TABLE IF NOT EXISTS schema_version (version INT NOT NULL)")
    cur.execute("SELECT version FROM schema_version")
    row = cur.fetchone()
    if row is None or row["version"] < SCHEMA_VERSION:
        cur.execute("DROP TABLE IF EXISTS user_character")
        cur.execute("DROP TABLE IF EXISTS `character`")
        cur.execute("DROP TABLE IF EXISTS running_record")
        cur.execute("DELETE FROM schema_version")
        cur.execute("INSERT INTO schema_version (version) VALUES (%s)", (SCHEMA_VERSION,))
    # 앱 running_record와 같은 구조 (GPS 경로는 받지 않음)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS running_record (
            running_number INT          PRIMARY KEY AUTO_INCREMENT,
            user_id        VARCHAR(100) NOT NULL,
            run_date       BIGINT       NOT NULL,  -- 러닝 시작 시각 (epoch ms)
            distance       DOUBLE,                 -- m
            duration       DOUBLE,                 -- 초
            pace           DOUBLE,                 -- 초/km
            avg_speed      DOUBLE,                 -- km/h
            max_speed      DOUBLE                  -- km/h
        )
    """)
    # character, condition은 MySQL 예약어라서 ``로 감쌈
    cur.execute("""
        CREATE TABLE IF NOT EXISTS `character` (
            character_id      INT          PRIMARY KEY,
            name              VARCHAR(50)  NOT NULL,
            emoji             VARCHAR(20)  NOT NULL,
            category          VARCHAR(20)  NOT NULL,
            `condition`       VARCHAR(100) NOT NULL,
            hint              VARCHAR(200) NOT NULL,
            description       VARCHAR(200) NOT NULL,
            required_distance INT,
            image_url         VARCHAR(500)
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS user_character (
            user_id      VARCHAR(100) NOT NULL,
            character_id INT          NOT NULL,
            level        INT          NOT NULL DEFAULT 1,
            exp          INT          NOT NULL DEFAULT 0,
            is_main      BOOLEAN      NOT NULL DEFAULT FALSE,
            acquired_at  DATETIME     NOT NULL,
            PRIMARY KEY (user_id, character_id),
            FOREIGN KEY (character_id) REFERENCES `character` (character_id)
        )
    """)
    # 캐릭터 정보를 고치면 서버 재시작 때 반영되도록 UPDATE
    # (REPLACE는 행을 지웠다 다시 넣어서 user_character 외래 키와 부딪힘)
    cur.executemany(
        "INSERT INTO `character` "
        "(character_id, name, emoji, category, `condition`, hint, description, required_distance) "
        "VALUES (%s, %s, %s, %s, %s, %s, %s, %s) "
        "ON DUPLICATE KEY UPDATE name = VALUES(name), emoji = VALUES(emoji), "
        "category = VALUES(category), `condition` = VALUES(`condition`), hint = VALUES(hint), "
        "description = VALUES(description), required_distance = VALUES(required_distance)",
        CHARACTERS,
    )
conn.commit()
conn.close()


class RunCreate(BaseModel):
    user_id: str
    run_date: Optional[int] = None  # 러닝 시작 시각 (epoch ms). 없으면 서버가 받은 시각
    distance: float = Field(ge=0)
    duration: float = Field(ge=0)
    pace: Optional[float] = None
    avg_speed: Optional[float] = None
    max_speed: Optional[float] = None


class Character(BaseModel):
    character_id: int
    name: str
    emoji: str
    category: str
    condition: str
    hint: str
    description: str
    required_distance: Optional[int] = None
    image_url: Optional[str] = None


class OwnedCharacter(Character):
    level: int
    exp: int
    is_main: bool
    acquired_at: datetime


class Run(RunCreate):
    running_number: int
    run_date: int
    new_characters: list[Character] = []


class Stats(BaseModel):
    user_id: str
    run_count: int
    total_distance: float
    total_duration: float
    main_character: Optional[OwnedCharacter] = None
    next_character: Optional[Character] = None


class DayDistance(BaseModel):
    day: date
    distance: float


def get_total_distance(cur, user_id):
    cur.execute(
        "SELECT COALESCE(SUM(distance), 0) AS total FROM running_record WHERE user_id = %s",
        (user_id,),
    )
    return cur.fetchone()["total"]


def get_owned_characters(cur, user_id):
    cur.execute(
        "SELECT c.*, uc.level, uc.exp, uc.is_main, uc.acquired_at "
        "FROM user_character uc JOIN `character` c ON c.character_id = uc.character_id "
        "WHERE uc.user_id = %s ORDER BY uc.acquired_at",
        (user_id,),
    )
    return [OwnedCharacter(**row) for row in cur.fetchall()]


def unlock_characters(cur, user_id):
    # TODO: 지금은 누적 거리 캐릭터(required_distance 있음)만 판단. 장소·연속·리그는 추후
    total = get_total_distance(cur, user_id)
    cur.execute(
        "SELECT * FROM `character` WHERE required_distance IS NOT NULL "
        "AND required_distance <= %s AND character_id NOT IN "
        "(SELECT character_id FROM user_character WHERE user_id = %s) "
        "ORDER BY required_distance",
        (total, user_id),
    )
    rows = cur.fetchall()
    cur.execute(
        "SELECT 1 FROM user_character WHERE user_id = %s AND is_main = TRUE",
        (user_id,),
    )
    has_main = cur.fetchone() is not None
    now = datetime.now().replace(microsecond=0)
    for row in rows:
        cur.execute(
            "INSERT INTO user_character (user_id, character_id, is_main, acquired_at) "
            "VALUES (%s, %s, %s, %s)",
            (user_id, row["character_id"], not has_main, now),
        )
        has_main = True
    return [Character(**row) for row in rows]


def add_exp_to_main(cur, user_id, distance):
    cur.execute(
        "SELECT character_id, exp FROM user_character WHERE user_id = %s AND is_main = TRUE",
        (user_id,),
    )
    row = cur.fetchone()
    if row is None:
        return
    exp = row["exp"] + int(distance)
    level = exp // EXP_PER_LEVEL + 1
    cur.execute(
        "UPDATE user_character SET exp = %s, level = %s WHERE user_id = %s AND character_id = %s",
        (exp, level, user_id, row["character_id"]),
    )


@app.get("/")
def home():
    return {"message": "RUN CATCH backend is working!"}


@app.post("/runs", response_model=Run, status_code=201)
def create_run(run: RunCreate):
    run_date = run.run_date or int(datetime.now().timestamp() * 1000)
    conn = get_db()
    with conn.cursor() as cur:
        cur.execute(
            "INSERT INTO running_record (user_id, run_date, distance, duration, pace, "
            "avg_speed, max_speed) VALUES (%s, %s, %s, %s, %s, %s, %s)",
            (
                run.user_id,
                run_date,
                run.distance,
                run.duration,
                run.pace,
                run.avg_speed,
                run.max_speed,
            ),
        )
        running_number = cur.lastrowid
        add_exp_to_main(cur, run.user_id, run.distance)
        new_characters = unlock_characters(cur, run.user_id)
    conn.commit()
    conn.close()
    run_data = run.model_dump()
    run_data["run_date"] = run_date
    return Run(
        running_number=running_number,
        new_characters=new_characters,
        **run_data,
    )


@app.get("/runs", response_model=list[Run])
def get_runs(user_id: str):
    conn = get_db()
    with conn.cursor() as cur:
        cur.execute(
            "SELECT * FROM running_record WHERE user_id = %s ORDER BY run_date DESC",
            (user_id,),
        )
        rows = cur.fetchall()
    conn.close()
    return rows


@app.get("/stats/{user_id}", response_model=Stats)
def get_stats(user_id: str):
    conn = get_db()
    with conn.cursor() as cur:
        cur.execute(
            "SELECT COUNT(*) AS run_count, "
            "COALESCE(SUM(distance), 0) AS total_distance, "
            "COALESCE(SUM(duration), 0) AS total_duration "
            "FROM running_record WHERE user_id = %s",
            (user_id,),
        )
        row = cur.fetchone()
        unlock_characters(cur, user_id)
        conn.commit()
        owned = get_owned_characters(cur, user_id)
        main_character = next((c for c in owned if c.is_main), None)
        cur.execute(
            "SELECT * FROM `character` WHERE required_distance > %s "
            "ORDER BY required_distance LIMIT 1",
            (row["total_distance"],),
        )
        next_row = cur.fetchone()
    conn.close()
    return Stats(
        user_id=user_id,
        main_character=main_character,
        next_character=Character(**next_row) if next_row else None,
        **row,
    )


@app.get("/stats/{user_id}/weekly", response_model=list[DayDistance])
def get_weekly_stats(user_id: str):
    today = date.today()
    week_ago = today - timedelta(days=6)
    conn = get_db()
    week_ago_ms = int(datetime.combine(week_ago, datetime.min.time()).timestamp() * 1000)
    # run_date(epoch ms)를 MySQL 세션 시간대 기준 날짜로 묶음
    with conn.cursor() as cur:
        cur.execute(
            "SELECT DATE(FROM_UNIXTIME(run_date / 1000)) AS day, "
            "SUM(distance) AS distance "
            "FROM running_record "
            "WHERE user_id = %s AND run_date >= %s "
            "GROUP BY day",
            (user_id, week_ago_ms),
        )
        rows = cur.fetchall()
    conn.close()
    by_day = {row["day"]: row["distance"] for row in rows}
    result = []
    for i in range(7):
        day = week_ago + timedelta(days=i)
        result.append(DayDistance(day=day, distance=by_day.get(day, 0)))
    return result


@app.get("/characters", response_model=list[Character])
def get_characters():
    conn = get_db()
    with conn.cursor() as cur:
        cur.execute("SELECT * FROM `character` ORDER BY character_id")
        rows = cur.fetchall()
    conn.close()
    return [Character(**row) for row in rows]


@app.get("/users/{user_id}/characters", response_model=list[OwnedCharacter])
def get_user_characters(user_id: str):
    conn = get_db()
    with conn.cursor() as cur:
        owned = get_owned_characters(cur, user_id)
    conn.close()
    return owned


@app.put("/users/{user_id}/main-character/{character_id}", response_model=OwnedCharacter)
def set_main_character(user_id: str, character_id: int):
    conn = get_db()
    with conn.cursor() as cur:
        cur.execute(
            "SELECT 1 FROM user_character WHERE user_id = %s AND character_id = %s",
            (user_id, character_id),
        )
        if cur.fetchone() is None:
            conn.close()
            raise HTTPException(status_code=404, detail="Character not owned by user")
        cur.execute("UPDATE user_character SET is_main = FALSE WHERE user_id = %s", (user_id,))
        cur.execute(
            "UPDATE user_character SET is_main = TRUE WHERE user_id = %s AND character_id = %s",
            (user_id, character_id),
        )
        conn.commit()
        result = next(c for c in get_owned_characters(cur, user_id) if c.is_main)
    conn.close()
    return result
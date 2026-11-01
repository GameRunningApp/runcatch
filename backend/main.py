import sqlite3
from datetime import date, datetime, timedelta
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

DB_PATH = "runcatch.db"
EXP_PER_LEVEL = 5000

app = FastAPI()


def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


conn = get_db()
conn.execute("""
    CREATE TABLE IF NOT EXISTS running_record (
        run_id     INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id    TEXT    NOT NULL,
        distance   REAL    NOT NULL,
        duration   REAL    NOT NULL,
        pace       REAL,
        avg_speed  REAL,
        max_speed  REAL,
        started_at TEXT    NOT NULL,
        created_at TEXT    NOT NULL
    )
""")
conn.execute("""
    CREATE TABLE IF NOT EXISTS character (
        character_id      TEXT    PRIMARY KEY,
        name              TEXT    NOT NULL,
        emoji             TEXT    NOT NULL,
        description       TEXT    NOT NULL,
        required_distance INTEGER NOT NULL,
        image_url         TEXT
    )
""")
conn.execute("""
    CREATE TABLE IF NOT EXISTS user_character (
        user_id      TEXT    NOT NULL,
        character_id TEXT    NOT NULL,
        level        INTEGER NOT NULL DEFAULT 1,
        exp          INTEGER NOT NULL DEFAULT 0,
        is_main      INTEGER NOT NULL DEFAULT 0,
        acquired_at  TEXT    NOT NULL,
        PRIMARY KEY (user_id, character_id),
        FOREIGN KEY (character_id) REFERENCES character (character_id)
    )
""")
conn.executemany(
    "INSERT OR IGNORE INTO character "
    "(character_id, name, emoji, description, required_distance) "
    "VALUES (?, ?, ?, ?, ?)",
    [
        ("chick", "삐약이", "🐥", "처음 만나는 러닝 동료", 2000),
        ("rabbit", "토끼", "🐰", "빠른 발을 가진 친구", 10000),
        ("turtle", "거북이", "🐢", "꾸준함의 상징", 30000),
    ],
)
conn.commit()
conn.close()


class RunCreate(BaseModel):
    user_id: str
    distance: float = Field(ge=0)
    duration: float = Field(ge=0)
    pace: Optional[float] = None
    avg_speed: Optional[float] = None
    max_speed: Optional[float] = None
    started_at: Optional[datetime] = None


class Character(BaseModel):
    character_id: str
    name: str
    emoji: str
    description: str
    required_distance: int
    image_url: Optional[str] = None


class OwnedCharacter(Character):
    level: int
    exp: int
    is_main: bool
    acquired_at: datetime


class Run(RunCreate):
    run_id: int
    started_at: datetime
    created_at: datetime
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


def get_total_distance(conn, user_id):
    row = conn.execute(
        "SELECT COALESCE(SUM(distance), 0) AS total FROM running_record WHERE user_id = ?",
        (user_id,),
    ).fetchone()
    return row["total"]


def get_owned_characters(conn, user_id):
    rows = conn.execute(
        "SELECT c.*, uc.level, uc.exp, uc.is_main, uc.acquired_at "
        "FROM user_character uc JOIN character c ON c.character_id = uc.character_id "
        "WHERE uc.user_id = ? ORDER BY uc.acquired_at",
        (user_id,),
    ).fetchall()
    return [OwnedCharacter(**dict(row)) for row in rows]


def unlock_characters(conn, user_id):
    total = get_total_distance(conn, user_id)
    rows = conn.execute(
        "SELECT * FROM character WHERE required_distance <= ? AND character_id NOT IN "
        "(SELECT character_id FROM user_character WHERE user_id = ?) "
        "ORDER BY required_distance",
        (total, user_id),
    ).fetchall()
    has_main = conn.execute(
        "SELECT 1 FROM user_character WHERE user_id = ? AND is_main = 1",
        (user_id,),
    ).fetchone()
    now = datetime.now().isoformat()
    for row in rows:
        is_main = 0 if has_main else 1
        conn.execute(
            "INSERT INTO user_character (user_id, character_id, is_main, acquired_at) "
            "VALUES (?, ?, ?, ?)",
            (user_id, row["character_id"], is_main, now),
        )
        has_main = True
    return [Character(**dict(row)) for row in rows]


def add_exp_to_main(conn, user_id, distance):
    row = conn.execute(
        "SELECT character_id, exp FROM user_character WHERE user_id = ? AND is_main = 1",
        (user_id,),
    ).fetchone()
    if row is None:
        return
    exp = row["exp"] + int(distance)
    level = exp // EXP_PER_LEVEL + 1
    conn.execute(
        "UPDATE user_character SET exp = ?, level = ? WHERE user_id = ? AND character_id = ?",
        (exp, level, user_id, row["character_id"]),
    )


@app.get("/")
def home():
    return {"message": "RUN CATCH backend is working!"}


@app.post("/runs", response_model=Run, status_code=201)
def create_run(run: RunCreate):
    created_at = datetime.now()
    started_at = run.started_at or created_at
    conn = get_db()
    cursor = conn.execute(
        "INSERT INTO running_record (user_id, distance, duration, pace, avg_speed, "
        "max_speed, started_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (
            run.user_id,
            run.distance,
            run.duration,
            run.pace,
            run.avg_speed,
            run.max_speed,
            started_at.isoformat(),
            created_at.isoformat(),
        ),
    )
    run_id = cursor.lastrowid
    add_exp_to_main(conn, run.user_id, run.distance)
    new_characters = unlock_characters(conn, run.user_id)
    conn.commit()
    conn.close()
    run_data = run.model_dump()
    run_data["started_at"] = started_at
    return Run(
        run_id=run_id,
        created_at=created_at,
        new_characters=new_characters,
        **run_data,
    )


@app.get("/runs", response_model=list[Run])
def get_runs(user_id: str):
    conn = get_db()
    rows = conn.execute(
        "SELECT * FROM running_record WHERE user_id = ? ORDER BY started_at DESC",
        (user_id,),
    ).fetchall()
    conn.close()
    return [dict(row) for row in rows]


@app.get("/stats/{user_id}", response_model=Stats)
def get_stats(user_id: str):
    conn = get_db()
    row = conn.execute(
        "SELECT COUNT(*) AS run_count, "
        "COALESCE(SUM(distance), 0) AS total_distance, "
        "COALESCE(SUM(duration), 0) AS total_duration "
        "FROM running_record WHERE user_id = ?",
        (user_id,),
    ).fetchone()
    unlock_characters(conn, user_id)
    conn.commit()
    owned = get_owned_characters(conn, user_id)
    main_character = next((c for c in owned if c.is_main), None)
    next_row = conn.execute(
        "SELECT * FROM character WHERE required_distance > ? "
        "ORDER BY required_distance LIMIT 1",
        (row["total_distance"],),
    ).fetchone()
    conn.close()
    return Stats(
        user_id=user_id,
        main_character=main_character,
        next_character=Character(**dict(next_row)) if next_row else None,
        **dict(row),
    )


@app.get("/stats/{user_id}/weekly", response_model=list[DayDistance])
def get_weekly_stats(user_id: str):
    today = date.today()
    week_ago = today - timedelta(days=6)
    conn = get_db()
    rows = conn.execute(
        "SELECT substr(started_at, 1, 10) AS day, SUM(distance) AS distance "
        "FROM running_record "
        "WHERE user_id = ? AND substr(started_at, 1, 10) >= ? "
        "GROUP BY day",
        (user_id, week_ago.isoformat()),
    ).fetchall()
    conn.close()
    by_day = {row["day"]: row["distance"] for row in rows}
    result = []
    for i in range(7):
        day = week_ago + timedelta(days=i)
        result.append(DayDistance(day=day, distance=by_day.get(day.isoformat(), 0)))
    return result


@app.get("/characters", response_model=list[Character])
def get_characters():
    conn = get_db()
    rows = conn.execute("SELECT * FROM character ORDER BY required_distance").fetchall()
    conn.close()
    return [Character(**dict(row)) for row in rows]


@app.get("/users/{user_id}/characters", response_model=list[OwnedCharacter])
def get_user_characters(user_id: str):
    conn = get_db()
    owned = get_owned_characters(conn, user_id)
    conn.close()
    return owned


@app.put("/users/{user_id}/main-character/{character_id}", response_model=OwnedCharacter)
def set_main_character(user_id: str, character_id: str):
    conn = get_db()
    owned = conn.execute(
        "SELECT 1 FROM user_character WHERE user_id = ? AND character_id = ?",
        (user_id, character_id),
    ).fetchone()
    if owned is None:
        conn.close()
        raise HTTPException(status_code=404, detail="Character not owned by user")
    conn.execute("UPDATE user_character SET is_main = 0 WHERE user_id = ?", (user_id,))
    conn.execute(
        "UPDATE user_character SET is_main = 1 WHERE user_id = ? AND character_id = ?",
        (user_id, character_id),
    )
    conn.commit()
    result = next(c for c in get_owned_characters(conn, user_id) if c.is_main)
    conn.close()
    return result
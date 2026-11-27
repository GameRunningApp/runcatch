# main.py의 /runs, /stats API를 옮겨 옴
#   GET /stats가 캐릭터를 지급하던 것을 없앰 (조회만. 지급은 POST /runs에서만)
"""러닝 기록과 통계 API."""
from datetime import datetime, time, timedelta

from fastapi import APIRouter

from config import KST
from db import db_cursor
from models import Character, DayDistance, Run, RunCreate, Stats
from services.rewards import add_exp_to_main, get_owned_characters, unlock_characters

router = APIRouter()


@router.post("/runs", response_model=Run, status_code=201)
def create_run(run: RunCreate):
    run_date = run.run_date or int(datetime.now(KST).timestamp() * 1000)
    with db_cursor() as cur:
        cur.execute(
            "INSERT INTO running_record (user_id, run_date, environment, distance, duration, "
            "pace, avg_speed, max_speed) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)",
            (
                run.user_id,
                run_date,
                run.environment,
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
    run_data = run.model_dump()
    run_data["run_date"] = run_date
    return Run(
        running_number=running_number,
        new_characters=new_characters,
        **run_data,
    )


@router.get("/runs", response_model=list[Run])
def get_runs(user_id: str):
    with db_cursor() as cur:
        cur.execute(
            "SELECT * FROM running_record WHERE user_id = %s ORDER BY run_date DESC",
            (user_id,),
        )
        return cur.fetchall()


@router.get("/stats/{user_id}", response_model=Stats)
def get_stats(user_id: str):
    # 조회만 한다. 캐릭터 지급은 POST /runs에서만 (예전에는 여기서도 지급했음)
    with db_cursor() as cur:
        cur.execute(
            "SELECT COUNT(*) AS run_count, "
            "COALESCE(SUM(distance), 0) AS total_distance, "
            "COALESCE(SUM(duration), 0) AS total_duration "
            "FROM running_record WHERE user_id = %s",
            (user_id,),
        )
        row = cur.fetchone()
        owned = get_owned_characters(cur, user_id)
        main_character = next((c for c in owned if c.is_main), None)
        cur.execute(
            "SELECT * FROM `character` WHERE required_distance > %s "
            "ORDER BY required_distance LIMIT 1",
            (row["total_distance"],),
        )
        next_row = cur.fetchone()
    return Stats(
        user_id=user_id,
        main_character=main_character,
        next_character=Character(**next_row) if next_row else None,
        **row,
    )


@router.get("/stats/{user_id}/weekly", response_model=list[DayDistance])
def get_weekly_stats(user_id: str):
    today = datetime.now(KST).date()
    week_ago = today - timedelta(days=6)
    week_ago_ms = int(datetime.combine(week_ago, time.min, tzinfo=KST).timestamp() * 1000)
    with db_cursor() as cur:
        cur.execute(
            "SELECT run_date, distance FROM running_record "
            "WHERE user_id = %s AND run_date >= %s",
            (user_id, week_ago_ms),
        )
        rows = cur.fetchall()
    # 날짜는 MySQL 시간대가 아니라 한국 시간 기준으로 묶음
    by_day = {}
    for row in rows:
        day = datetime.fromtimestamp(row["run_date"] / 1000, KST).date()
        by_day[day] = by_day.get(day, 0) + (row["distance"] or 0)
    result = []
    for i in range(7):
        day = week_ago + timedelta(days=i)
        result.append(DayDistance(day=day, distance=by_day.get(day, 0)))
    return result

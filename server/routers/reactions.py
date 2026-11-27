# [구조변경] 새 파일. main.py의 반응 · 응원 API를 옮겨 옴 (try/finally 대신 db_cursor)
#   집계 함수 get_run_reactions()는 services/reactions.py로 옮김
"""커뮤니티 피드 러닝 기록의 반응(이모지)과 응원 API."""
from datetime import datetime

from fastapi import APIRouter, HTTPException

from db import db_cursor
from models import CheerCreate, ReactionCreate, RunReactions
from services.reactions import get_run_reactions

router = APIRouter()


def check_run_exists(cur, running_number):
    cur.execute(
        "SELECT 1 FROM running_record WHERE running_number = %s", (running_number,)
    )
    if cur.fetchone() is None:
        raise HTTPException(status_code=404, detail="Run not found")


@router.get("/runs/{running_number}/reactions", response_model=RunReactions)
def read_run_reactions(running_number: int, user_id: str = ""):
    with db_cursor() as cur:
        check_run_exists(cur, running_number)
        return get_run_reactions(cur, running_number, user_id)


@router.post("/runs/{running_number}/reactions", response_model=RunReactions)
def add_run_reaction(running_number: int, reaction: ReactionCreate):
    with db_cursor() as cur:
        check_run_exists(cur, running_number)
        # 이미 누른 이모지면 그대로 둠 (두 번 눌러도 +1만)
        cur.execute(
            "INSERT IGNORE INTO run_reaction (running_number, user_id, emoji, created_at) "
            "VALUES (%s, %s, %s, %s)",
            (running_number, reaction.user_id, reaction.emoji,
             datetime.now().replace(microsecond=0)),
        )
        return get_run_reactions(cur, running_number, reaction.user_id)


@router.delete("/runs/{running_number}/reactions", response_model=RunReactions)
def remove_run_reaction(running_number: int, user_id: str, emoji: str):
    with db_cursor() as cur:
        check_run_exists(cur, running_number)
        cur.execute(
            "DELETE FROM run_reaction "
            "WHERE running_number = %s AND user_id = %s AND emoji = %s",
            (running_number, user_id, emoji),
        )
        return get_run_reactions(cur, running_number, user_id)


@router.post("/runs/{running_number}/cheer", response_model=RunReactions)
def cheer_run(running_number: int, cheer: CheerCreate):
    with db_cursor() as cur:
        check_run_exists(cur, running_number)
        # 응원은 한 번만 (이미 했으면 그대로 둠)
        cur.execute(
            "INSERT IGNORE INTO run_cheer (running_number, user_id, created_at) "
            "VALUES (%s, %s, %s)",
            (running_number, cheer.user_id, datetime.now().replace(microsecond=0)),
        )
        return get_run_reactions(cur, running_number, cheer.user_id)

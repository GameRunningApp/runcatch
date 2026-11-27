# [구조변경] 새 파일. main.py의 /characters, /users/{id}/characters, 대표 캐릭터 API를 옮겨 옴
"""캐릭터 목록, 보유 캐릭터, 대표 캐릭터 API."""
from fastapi import APIRouter, HTTPException

from db import db_cursor
from models import Character, OwnedCharacter
from services.rewards import get_owned_characters

router = APIRouter()


@router.get("/characters", response_model=list[Character])
def get_characters():
    with db_cursor() as cur:
        cur.execute("SELECT * FROM `character` ORDER BY character_id")
        return [Character(**row) for row in cur.fetchall()]


@router.get("/users/{user_id}/characters", response_model=list[OwnedCharacter])
def get_user_characters(user_id: str):
    with db_cursor() as cur:
        return get_owned_characters(cur, user_id)


@router.put("/users/{user_id}/main-character/{character_id}", response_model=OwnedCharacter)
def set_main_character(user_id: str, character_id: int):
    with db_cursor() as cur:
        cur.execute(
            "SELECT 1 FROM user_character WHERE user_id = %s AND character_id = %s",
            (user_id, character_id),
        )
        if cur.fetchone() is None:
            raise HTTPException(status_code=404, detail="Character not owned by user")
        cur.execute("UPDATE user_character SET is_main = FALSE WHERE user_id = %s", (user_id,))
        cur.execute(
            "UPDATE user_character SET is_main = TRUE WHERE user_id = %s AND character_id = %s",
            (user_id, character_id),
        )
        return next(c for c in get_owned_characters(cur, user_id) if c.is_main)

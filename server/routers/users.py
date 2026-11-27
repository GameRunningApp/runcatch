# [구조변경] 새 파일 (DB 연결 작업). 여러 폰에서 같은 닉네임 · 이메일로 가입하지 못하게 하는 회원 등록 API
"""회원 등록 API. 닉네임(user_id) · 이메일이 서버 전체에서 겹치지 않게 한다."""
import pymysql
from fastapi import APIRouter, HTTPException

from config import now_kst
from db import db_cursor
from models import UserCreate

router = APIRouter()

# 앱 SignUpActivity가 이 detail 값으로 어느 칸이 중복인지 구분한다
USER_ID_TAKEN = "user_id_taken"
MAIL_TAKEN = "mail_taken"


@router.post("/users", status_code=201)
def create_user(user: UserCreate):
    try:
        with db_cursor() as cur:
            cur.execute(
                "SELECT user_id FROM `user` WHERE user_id = %s OR mail = %s",
                (user.user_id, user.mail),
            )
            rows = cur.fetchall()
            if any(row["user_id"] == user.user_id for row in rows):
                raise HTTPException(status_code=409, detail=USER_ID_TAKEN)
            if rows:
                raise HTTPException(status_code=409, detail=MAIL_TAKEN)
            cur.execute(
                "INSERT INTO `user` (user_id, mail, created_at) VALUES (%s, %s, %s)",
                (user.user_id, user.mail, now_kst()),
            )
    except pymysql.err.IntegrityError as error:
        # 위 확인과 INSERT 사이에 다른 요청이 같은 값으로 먼저 가입한 경우 (키 이름으로 구분)
        detail = MAIL_TAKEN if "mail" in str(error) else USER_ID_TAKEN
        raise HTTPException(status_code=409, detail=detail) from error
    return {"user_id": user.user_id}

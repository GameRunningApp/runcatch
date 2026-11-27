# [구조변경] 새 파일. main.py의 get_db()와 community_grouping.py의 get_db_connection()을 합침
#   db_cursor()를 추가해 오류가 나도 연결이 닫히도록 함 (예전에는 오류 시 연결이 남았음)
"""DB 연결. 서버의 모든 모듈은 여기서만 연결을 만든다."""
from contextlib import contextmanager

import pymysql

from config import DB_CONFIG


def get_db():
    return pymysql.connect(**DB_CONFIG)


@contextmanager
def db_cursor():
    """성공하면 commit, 예외가 나면 rollback. 어느 경우든 연결을 닫는다."""
    conn = get_db()
    try:
        with conn.cursor() as cur:
            yield cur
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

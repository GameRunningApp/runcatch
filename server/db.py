# main.py의 get_db()와 community_grouping.py의 get_db_connection()을 합침
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

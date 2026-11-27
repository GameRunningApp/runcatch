# main.py의 캐릭터 획득 판정(unlock_characters) · 경험치(add_exp_to_main)를 옮겨 옴
"""캐릭터 조회, 획득 판정, 경험치 지급. 지급(unlock_characters, add_exp_to_main)은 POST /runs에서만 호출한다."""
from characters import PLACE_CONDITIONS
from config import EXP_PER_LEVEL, now_kst
from models import Character, OwnedCharacter


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


def place_condition_met(cur, user_id, environment, kind, value):
    if kind == "single":
        cur.execute(
            "SELECT COUNT(*) AS n FROM running_record "
            "WHERE user_id = %s AND environment = %s AND distance >= %s",
            (user_id, environment, value),
        )
        return cur.fetchone()["n"] > 0
    if kind == "count":
        cur.execute(
            "SELECT COUNT(*) AS n FROM running_record WHERE user_id = %s AND environment = %s",
            (user_id, environment),
        )
        return cur.fetchone()["n"] >= value
    cur.execute(
        "SELECT COALESCE(SUM(distance), 0) AS total FROM running_record "
        "WHERE user_id = %s AND environment = %s",
        (user_id, environment),
    )
    return cur.fetchone()["total"] >= value


def unlock_characters(cur, user_id):
    # TODO: 연속·리그·산 캐릭터는 추후
    cur.execute("SELECT character_id FROM user_character WHERE user_id = %s", (user_id,))
    owned = {row["character_id"] for row in cur.fetchall()}
    total = get_total_distance(cur, user_id)
    cur.execute("SELECT * FROM `character` ORDER BY character_id")
    rows = []
    for row in cur.fetchall():
        character_id = row["character_id"]
        if character_id in owned:
            continue
        if row["required_distance"] is not None:
            unlocked = total >= row["required_distance"]
        elif character_id in PLACE_CONDITIONS:
            unlocked = place_condition_met(cur, user_id, *PLACE_CONDITIONS[character_id])
        else:
            unlocked = False
        if unlocked:
            rows.append(row)
    cur.execute(
        "SELECT 1 FROM user_character WHERE user_id = %s AND is_main = TRUE",
        (user_id,),
    )
    has_main = cur.fetchone() is not None
    now = now_kst()
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

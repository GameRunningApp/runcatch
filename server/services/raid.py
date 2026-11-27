# [구조변경] 예전 위치: backend/community_raid.py. DB 연결은 db.get_db(), 시간대는 config.KST 사용

from datetime import date, datetime

from config import EXP_PER_LEVEL, KST
from db import get_db


# 레이드 목표 거리와 보상 XP
RAID_TARGET_DISTANCE = 1_000_000  # 1,000km, 단위 m
RAID_REWARD_EXP = 200


def ensure_raid_table():
    """레이드 테이블이 없으면 생성한다."""
    conn = get_db()

    try:
        with conn.cursor() as cur:
            cur.execute("""
                CREATE TABLE IF NOT EXISTS community_raid (
                    raid_id BIGINT NOT NULL AUTO_INCREMENT,
                    group_id BIGINT NOT NULL,
                    group_month DATE NOT NULL,
                    target_distance BIGINT NOT NULL DEFAULT 1000000,
                    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
                    completed_at DATETIME NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (raid_id),
                    UNIQUE KEY uq_community_raid_group_month
                        (group_id, group_month),
                    CONSTRAINT fk_community_raid_group_month
                        FOREIGN KEY (group_id, group_month)
                        REFERENCES community_group (group_id, group_month)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """)
        conn.commit()
    finally:
        conn.close()


def get_raid_status(user_id: str):
    """사용자의 이번 달 그룹 레이드 상태를 조회하고 달성 시 보상을 지급한다."""

    ensure_raid_table()

    now = datetime.now(KST)
    group_month = date(now.year, now.month, 1)

    # KST 기준 이번 달 시작과 다음 달 시작을 epoch ms로 변환
    start_dt = datetime(
        now.year, now.month, 1, tzinfo=KST
    )

    if now.month == 12:
        next_dt = datetime(now.year + 1, 1, 1, tzinfo=KST)
    else:
        next_dt = datetime(now.year, now.month + 1, 1, tzinfo=KST)

    start_ms = int(start_dt.timestamp() * 1000)
    next_ms = int(next_dt.timestamp() * 1000)

    conn = get_db()

    try:
        with conn.cursor() as cur:
            # 사용자의 이번 달 소속 그룹 조회
            cur.execute(
                """
                SELECT
                    cg.group_id,
                    cg.group_number,
                    cg.member_count,
                    cgm.group_month
                FROM community_group_member cgm
                JOIN community_group cg
                  ON cg.group_id = cgm.group_id
                 AND cg.group_month = cgm.group_month
                WHERE cgm.user_id = %s
                  AND cgm.group_month = %s
                LIMIT 1
                """,
                (user_id, group_month),
            )

            group = cur.fetchone()

            if group is None:
                return {"is_joined": False}

            group_id = group["group_id"]

            # 해당 그룹·월의 레이드가 없으면 생성
            cur.execute(
                """
                INSERT IGNORE INTO community_raid
                    (group_id, group_month, target_distance)
                VALUES (%s, %s, %s)
                """,
                (group_id, group_month, RAID_TARGET_DISTANCE),
            )

            # 동시 요청 시 중복 보상을 막기 위해 레이드 행 잠금
            cur.execute(
                """
                SELECT *
                FROM community_raid
                WHERE group_id = %s
                  AND group_month = %s
                FOR UPDATE
                """,
                (group_id, group_month),
            )

            raid = cur.fetchone()

            # 이번 달 그룹원들의 러닝 거리 합산
            cur.execute(
                """
                SELECT COALESCE(SUM(rr.distance), 0) AS total_distance
                FROM running_record rr
                JOIN community_group_member gm
                  ON gm.user_id = rr.user_id
                WHERE gm.group_id = %s
                  AND gm.group_month = %s
                  AND rr.run_date >= %s
                  AND rr.run_date < %s
                """,
                (group_id, group_month, start_ms, next_ms),
            )

            current_distance = float(
                cur.fetchone()["total_distance"] or 0
            )

            # 현재 사용자의 이번 달 개인 러닝 거리 합산
            cur.execute(
                """
                SELECT COALESCE(SUM(distance), 0) AS my_distance
                FROM running_record
                WHERE user_id = %s
                  AND run_date >= %s
                  AND run_date < %s
                """,
                (user_id, start_ms, next_ms),
            )

            my_distance = float(
                cur.fetchone()["my_distance"] or 0
            )

            just_completed = False

            # 목표 달성 전이고 목표 거리에 도달했다면 보상 지급
            if (
                not raid["is_completed"]
                and current_distance >= raid["target_distance"]
            ):
                cur.execute(
                    """
                    SELECT user_id
                    FROM community_group_member
                    WHERE group_id = %s
                      AND group_month = %s
                    ORDER BY user_id
                    """,
                    (group_id, group_month),
                )

                members = cur.fetchall()

                for member in members:
                    member_id = member["user_id"]

                    # 각 그룹원의 메인 캐릭터 조회 및 잠금
                    cur.execute(
                        """
                        SELECT character_id, exp
                        FROM user_character
                        WHERE user_id = %s
                          AND is_main = TRUE
                        FOR UPDATE
                        """,
                        (member_id,),
                    )

                    character = cur.fetchone()

                    # 메인 캐릭터가 없는 경우 기존 XP 지급 방식과 동일하게 건너뜀
                    if character is None:
                        continue

                    new_exp = int(character["exp"]) + RAID_REWARD_EXP
                    new_level = new_exp // EXP_PER_LEVEL + 1

                    cur.execute(
                        """
                        UPDATE user_character
                        SET exp = %s,
                            level = %s
                        WHERE user_id = %s
                          AND character_id = %s
                        """,
                        (
                            new_exp,
                            new_level,
                            member_id,
                            character["character_id"],
                        ),
                    )

                # 보상 지급과 달성 상태 변경을 같은 트랜잭션으로 처리
                completed_at = datetime.now(KST).replace(tzinfo=None)

                cur.execute(
                    """
                    UPDATE community_raid
                    SET is_completed = TRUE,
                        completed_at = %s
                    WHERE raid_id = %s
                    """,
                    (completed_at, raid["raid_id"]),
                )

                raid["is_completed"] = True
                raid["completed_at"] = completed_at
                just_completed = True

            conn.commit()

            target_distance = int(raid["target_distance"])

            return {
                "is_joined": True,
                "group_id": group_id,
                "group_number": group["group_number"],
                "group_month": group_month,
                "member_count": group["member_count"],
                "target_distance": target_distance,
                "current_distance": current_distance,
                "my_distance": my_distance,
                "progress_percent": min(
                    round(current_distance / target_distance * 100, 1),
                    100.0,
                ),
                "is_completed": bool(raid["is_completed"]),
                "completed_at": raid["completed_at"],
                "reward_exp": RAID_REWARD_EXP,
                "just_completed": just_completed,
            }

    except Exception:
        conn.rollback()
        raise

    finally:
        conn.close()

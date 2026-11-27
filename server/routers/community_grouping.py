#   DB 연결은 db.get_db(), 시간대는 config.KST 사용. 피드 반응 개수 0 고정을 실제 집계로 바꿈
from datetime import date, datetime, timedelta
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from config import KST
from db import get_db
from services.reactions import get_run_reactions
from services.grouping import (
    run_monthly_grouping,
    join_monthly_group,
    get_midmonth_range,
)

router = APIRouter()


class GroupingRequest(BaseModel):
    group_month: date


class GroupJoinRequest(BaseModel):
    user_id: str


@router.post("/admin/community-groups/grouping")
def admin_run_grouping(request: GroupingRequest):
    try:
        result = run_monthly_grouping(
            request.group_month,
            "ADMIN"
        )
        return result

    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error))

    except Exception as error:
        raise HTTPException(status_code=500, detail=str(error))


@router.post("/community-groups/join")
def join_community_group(request: GroupJoinRequest):
    try:
        result = join_monthly_group(request.user_id)
        return result

    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error))

    except Exception as error:
        raise HTTPException(status_code=500, detail=str(error))


@router.get("/community-groups/{user_id}")
def get_user_community_group(user_id: str):
    connection = None

    try:
        connection = get_db()

        now = datetime.now(KST)
        group_month = now.date().replace(day=1)

        with connection.cursor() as cursor:

            # 1. 사용자가 이번 달 그룹에 가입되어 있는지 확인
            cursor.execute(
                """
                SELECT
                    g.group_id,
                    g.group_month,
                    g.group_number,
                    g.member_count
                FROM community_group_member AS m
                JOIN community_group AS g
                    ON m.group_id = g.group_id
                   AND m.group_month = g.group_month
                WHERE m.user_id = %s
                  AND m.group_month = %s
                LIMIT 1
                """,
                (user_id, group_month)
            )

            group = cursor.fetchone()

            if not group:
                return {"is_joined": False}

            # 2. 그룹에 속한 사용자 목록 조회
            cursor.execute(
                """
                SELECT user_id
                FROM community_group_member
                WHERE group_id = %s
                  AND group_month = %s
                """,
                (group["group_id"], group_month)
            )

            members = cursor.fetchall()
            user_ids = [member["user_id"] for member in members]

            # 3. 최근 3개월의 시작 및 종료 시각 계산
            start_ms, end_ms = get_midmonth_range(now)

            # 4. 그룹원별 최근 3개월 평균 페이스 계산
            placeholders = ", ".join(["%s"] * len(user_ids))

            cursor.execute(
                f"""
                SELECT
                    user_id,
                    COUNT(*) AS run_count,
                    SUM(duration) / (SUM(distance) / 1000)
                        AS average_pace
                FROM running_record
                WHERE user_id IN ({placeholders})
                  AND run_date >= %s
                  AND run_date <= %s
                  AND distance > 0
                  AND duration > 0
                GROUP BY user_id
                HAVING COUNT(*) >= 3
                """,
                tuple(user_ids) + (start_ms, end_ms)
            )

            pace_results = cursor.fetchall()

            # 유효한 러닝 기록이 있는 그룹원만 페이스 범위에 반영
            paces = [
                float(row["average_pace"])
                for row in pace_results
                if row["average_pace"] is not None
            ]

            pace_min_sec = min(paces) if paces else None
            pace_max_sec = max(paces) if paces else None

            # 5. 그룹 번호에 따라 캐릭터 이모지 순환 매핑
            cursor.execute(
                """
                SELECT character_id, name, emoji
                FROM `character`
                ORDER BY character_id
                """
            )

            characters = cursor.fetchall()

            character_id = None
            character_name = None
            group_emoji = "🐥"

            if characters:
                character_index = (
                    group["group_number"] - 1
                ) % len(characters)

                character = characters[character_index]

                character_id = character["character_id"]
                character_name = character["name"]
                group_emoji = character["emoji"]

            # 6. 그룹 정보 반환
            return {
                "is_joined": True,
                "group_id": group["group_id"],
                "group_month": str(group["group_month"]),
                "group_number": group["group_number"],
                "group_name": f'그룹 {group["group_number"]}',
                "group_emoji": group_emoji,
                "character_id": character_id,
                "character_name": character_name,
                "pace_min_sec": pace_min_sec,
                "pace_max_sec": pace_max_sec,
                "member_count": group["member_count"],
            }

    except Exception as error:
        raise HTTPException(
            status_code=500,
            detail=str(error)
        )

    finally:
        if connection:
            connection.close()

def get_current_month_timestamp_range(now):
    """현재 월의 시작 시각과 다음 달 시작 시각을 Unix ms로 반환한다."""
    month_start = datetime(
        now.year,
        now.month,
        1,
        tzinfo=KST,
    )

    if now.month == 12:
        next_month = datetime(
            now.year + 1,
            1,
            1,
            tzinfo=KST,
        )
    else:
        next_month = datetime(
            now.year,
            now.month + 1,
            1,
            tzinfo=KST,
        )

    start_ms = int(month_start.timestamp() * 1000)
    end_ms = int(next_month.timestamp() * 1000)

    return start_ms, end_ms

def get_current_group(cursor, user_id, group_month):
    """사용자의 이번 달 그룹 정보를 조회한다."""
    cursor.execute(
        """
        SELECT
            g.group_id,
            g.group_month,
            g.group_number,
            g.member_count
        FROM community_group_member AS m
        JOIN community_group AS g
            ON m.group_id = g.group_id
           AND m.group_month = g.group_month
        WHERE m.user_id = %s
          AND m.group_month = %s
        LIMIT 1
        """,
        (user_id, group_month),
    )

    return cursor.fetchone()

@router.get("/community-groups/{user_id}/feed")
def get_community_feed(user_id: str):
    """같은 그룹에 속한 사용자들의 이번 달 러닝 피드를 조회한다."""
    connection = None

    try:
        connection = get_db()

        now = datetime.now(KST)
        group_month = now.date().replace(day=1)
        start_ms, end_ms = get_current_month_timestamp_range(now)

        with connection.cursor() as cursor:
            # 1. 사용자 그룹 확인
            group = get_current_group(
                cursor,
                user_id,
                group_month,
            )

            if not group:
                return {
                    "is_joined": False,
                    "posts": [],
                }

            # 2. 그룹원 목록 조회
            cursor.execute(
                """
                SELECT user_id
                FROM community_group_member
                WHERE group_id = %s
                  AND group_month = %s
                """,
                (group["group_id"], group_month),
            )

            members = cursor.fetchall()
            user_ids = [member["user_id"] for member in members]

            if not user_ids:
                return {
                    "is_joined": True,
                    "posts": [],
                }

            placeholders = ", ".join(["%s"] * len(user_ids))

            # 3. 그룹원들의 이번 달 러닝 기록 조회
            cursor.execute(
                f"""
                SELECT
                    r.running_number,
                    r.user_id,
                    r.run_date,
                    r.distance,
                    r.duration,
                    r.pace,
                    COALESCE(c.emoji, '🐥') AS character_emoji,
                    COALESCE(uc.level, 1) AS level
                FROM running_record AS r
                LEFT JOIN user_character AS uc
                    ON r.user_id = uc.user_id
                   AND uc.is_main = 1
                LEFT JOIN `character` AS c
                    ON uc.character_id = c.character_id
                WHERE r.user_id IN ({placeholders})
                  AND r.run_date >= %s
                  AND r.run_date < %s
                  AND r.distance > 0
                  AND r.duration > 0
                ORDER BY r.run_date DESC, r.running_number DESC
                LIMIT 100
                """,
                tuple(user_ids) + (start_ms, end_ms),
            )

            rows = cursor.fetchall()

            posts = []

            for row in rows:
                # shortcut: 글마다 반응을 따로 조회 (최대 100건 × 쿼리 2개), 피드가 느려지면 IN으로 한 번에 묶기
                reactions = get_run_reactions(cursor, row["running_number"], user_id)
                posts.append({
                    "running_number": row["running_number"],
                    "user_id": row["user_id"],
                    "nickname": row["user_id"],
                    "character_emoji": row["character_emoji"],
                    "level": int(row["level"]),
                    "run_date": int(row["run_date"]),
                    "distance_km": round(
                        float(row["distance"]) / 1000,
                        2,
                    ),
                    "duration_sec": round(
                        float(row["duration"]),
                        2,
                    ),
                    "pace_sec": round(
                        float(row["pace"]),
                        2,
                    ),
                    # 이모지별 개수와 user_id가 눌렀는지 (run_reaction, run_cheer 테이블)
                    "reactions": [r.model_dump() for r in reactions.reactions],
                    "cheer_count": reactions.cheer_count,
                    "cheered_by_me": reactions.cheered_by_me,
                })

            return {
                "is_joined": True,
                "group_id": group["group_id"],
                "group_number": group["group_number"],
                "posts": posts,
            }

    except Exception as error:
        raise HTTPException(
            status_code=500,
            detail=str(error),
        )

    finally:
        if connection:
            connection.close()

@router.get("/community-groups/{user_id}/league")
def get_community_league(user_id: str):
    """같은 그룹원들의 이번 달 누적 러닝 거리로 월간 랭킹을 조회한다."""
    connection = None

    try:
        connection = get_db()

        now = datetime.now(KST)
        group_month = now.date().replace(day=1)
        start_ms, end_ms = get_current_month_timestamp_range(now)

        with connection.cursor() as cursor:
            # 1. 사용자 그룹 확인
            group = get_current_group(
                cursor,
                user_id,
                group_month,
            )

            if not group:
                return {
                    "is_joined": False,
                    "rankings": [],
                }

            # 2. 그룹원별 이번 달 누적 거리 조회
            cursor.execute(
                """
                SELECT
                    m.user_id,
                    COALESCE(uc.level, 1) AS level,
                    COALESCE(c.emoji, '🐥') AS character_emoji,
                    COALESCE(SUM(r.distance), 0) / 1000
                        AS total_distance_km
                FROM community_group_member AS m
                LEFT JOIN user_character AS uc
                    ON m.user_id = uc.user_id
                   AND uc.is_main = 1
                LEFT JOIN `character` AS c
                    ON uc.character_id = c.character_id
                LEFT JOIN running_record AS r
                    ON m.user_id = r.user_id
                   AND r.run_date >= %s
                   AND r.run_date < %s
                   AND r.distance > 0
                WHERE m.group_id = %s
                  AND m.group_month = %s
                GROUP BY
                    m.user_id,
                    uc.level,
                    c.emoji
                ORDER BY
                    total_distance_km DESC,
                    m.user_id ASC
                """,
                (
                    start_ms,
                    end_ms,
                    group["group_id"],
                    group_month,
                ),
            )

            rows = cursor.fetchall()

            rankings = []

            for index, row in enumerate(rows):
                distance_km = float(
                    row["total_distance_km"] or 0
                )

                rankings.append({
                    "rank": index + 1,
                    "user_id": row["user_id"],
                    "nickname": row["user_id"],
                    "character_emoji": row["character_emoji"],
                    "level": int(row["level"]),
                    "distance_km": round(distance_km, 2),
                    "is_me": row["user_id"] == user_id,
                    "streak_days": 0,
                })

            return {
                "is_joined": True,
                "group_id": group["group_id"],
                "group_number": group["group_number"],
                "rankings": rankings,
            }

    except Exception as error:
        raise HTTPException(
            status_code=500,
            detail=str(error),
        )

    finally:
        if connection:
            connection.close()


from datetime import date, datetime
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from community_grouping import (
    run_monthly_grouping,
    join_monthly_group,
    get_db_connection,
    get_midmonth_range,
    KST,
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
        connection = get_db_connection()

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

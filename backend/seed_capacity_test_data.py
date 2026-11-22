
from datetime import datetime
from zoneinfo import ZoneInfo
import pymysql

KST = ZoneInfo("Asia/Seoul")
GROUP_MONTH = datetime(2026, 11, 1).date()


def get_db_connection():
    return pymysql.connect(
        host="localhost",
        port=3306,
        user="runcatch",
        password="runcatch1234",
        database="runcatch",
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=False,
    )


def to_timestamp(year, month, day):
    dt = datetime(year, month, day, 8, 0, tzinfo=KST)
    return int(dt.timestamp() * 1000)


def main():
    conn = get_db_connection()

    try:
        with conn.cursor() as cur:
            # 1. 기존 그룹 확인
            cur.execute(
                """
                SELECT group_id, group_number, member_count
                FROM community_group
                WHERE group_month = %s
                ORDER BY group_number
                FOR UPDATE
                """,
                (GROUP_MONTH,),
            )
            groups = cur.fetchall()

            if len(groups) != 2:
                raise RuntimeError(
                    "11월 그룹이 정확히 2개가 아닙니다."
                )

            for group in groups:
                cur.execute(
                    """
                    SELECT COUNT(*) AS actual_count
                    FROM community_group_member
                    WHERE group_id = %s
                      AND group_month = %s
                    """,
                    (group["group_id"], GROUP_MONTH),
                )
                actual = cur.fetchone()["actual_count"]

                if actual != group["member_count"]:
                    raise RuntimeError(
                        f"{group['group_number']}그룹의 "
                        "member_count와 실제 인원이 다릅니다."
                    )

                expected_count = (
                    20 if group["group_number"] == 1 else 14
                )

                if actual != expected_count:
                    raise RuntimeError(
                        f"{group['group_number']}그룹이 "
                        f"{expected_count}명이 아닙니다: {actual}명"
                    )

            # 2. 캐릭터 ID 조회
            cur.execute(
                """
                SELECT character_id
                FROM `character`
                ORDER BY character_id
                """
            )
            characters = cur.fetchall()

            if not characters:
                raise RuntimeError("캐릭터 데이터가 없습니다.")

            character_ids = [
                row["character_id"] for row in characters
            ]

            # 3. 두 번째 그룹에 테스트 사용자 6명 추가
            now = datetime.now(KST).replace(tzinfo=None)

            for group_index, group in enumerate(groups[1:]):
                for i in range(1, 7):
                    user_id = (
                        f"capacity_g{group['group_number']}_user{i:02d}"
                    )

                    # 중복 러닝 기록 검사
                    cur.execute(
                        """
                        SELECT COUNT(*) AS cnt
                        FROM running_record
                        WHERE user_id = %s
                        """,
                        (user_id,),
                    )

                    if cur.fetchone()["cnt"] > 0:
                        raise RuntimeError(
                            f"이미 존재하는 테스트 사용자: {user_id}"
                        )

                    # 중복 캐릭터 데이터 검사
                    cur.execute(
                        """
                        SELECT COUNT(*) AS cnt
                        FROM user_character
                        WHERE user_id = %s
                        """,
                        (user_id,),
                    )

                    if cur.fetchone()["cnt"] > 0:
                        raise RuntimeError(
                            f"이미 존재하는 캐릭터 데이터: {user_id}"
                        )

                    level = 5 + group_index * 10 + i
                    pace = 500.0 + group_index * 30 + i * 5

                    # 최근 3개월 유효 러닝 기록 3개
                    for year, month, day in [
                        (2026, 8, 12),
                        (2026, 9, 12),
                        (2026, 10, 12),
                    ]:
                        distance = 5000.0
                        duration = distance / 1000 * pace

                        cur.execute(
                            """
                            INSERT INTO running_record (
                                user_id,
                                run_date,
                                distance,
                                duration,
                                pace,
                                avg_speed,
                                max_speed,
                                environment
                            )
                            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                            """,
                            (
                                user_id,
                                to_timestamp(year, month, day),
                                distance,
                                duration,
                                pace,
                                3600 / pace,
                                12.0,
                                "CITY",
                            ),
                        )

                    # 캐릭터 보유 정보
                    character_id = character_ids[
                        (group_index * 7 + i - 1)
                        % len(character_ids)
                    ]

                    cur.execute(
                        """
                        INSERT INTO user_character (
                            user_id,
                            character_id,
                            level,
                            exp,
                            is_main,
                            acquired_at
                        )
                        VALUES (%s, %s, %s, %s, %s, %s)
                        """,
                        (
                            user_id,
                            character_id,
                            level,
                            0,
                            1,
                            now,
                        ),
                    )

                    # 테스트용 그룹 멤버 등록
                    cur.execute(
                        """
                        INSERT INTO community_group_member (
                            group_id,
                            user_id,
                            group_month,
                            assigned_at
                        )
                        VALUES (%s, %s, %s, %s)
                        """,
                        (
                            group["group_id"],
                            user_id,
                            GROUP_MONTH,
                            now,
                        ),
                    )

                # 그룹 인원 14명 -> 20명
                cur.execute(
                    """
                    UPDATE community_group
                    SET member_count = 20
                    WHERE group_id = %s
                      AND group_month = %s
                      AND member_count = 14
                    """,
                    (group["group_id"], GROUP_MONTH),
                )

                if cur.rowcount != 1:
                    raise RuntimeError(
                        f"{group['group_number']}그룹 인원 갱신 실패"
                    )

        conn.commit()

        print("정원 테스트 데이터 생성 성공!")
        print("1그룹: 20명")
        print("2그룹: 20명")

    except Exception:
        conn.rollback()
        raise

    finally:
        conn.close()


if __name__ == "__main__":
    main()

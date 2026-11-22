
from datetime import datetime, time
from zoneinfo import ZoneInfo
import pymysql

KST = ZoneInfo("Asia/Seoul")


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
    dt = datetime.combine(
        datetime(year, month, day).date(),
        time(8, 0),
        tzinfo=KST,
    )
    return int(dt.timestamp() * 1000)


def main():
    conn = get_db_connection()

    try:
        with conn.cursor() as cursor:
            cursor.execute(
                "SELECT character_id FROM `character` ORDER BY character_id"
            )
            characters = cursor.fetchall()

            if not characters:
                raise RuntimeError("등록된 캐릭터가 없습니다.")

            character_ids = [row["character_id"] for row in characters]

            users = []

            # 일반 그룹핑 대상자 25명
            for i in range(1, 21):
                level = i
                if i <= 9:
                    pace = 470 + i * 10
                else:
                    pace = 200 + i * 10

                users.append({
                    "user_id": f"join_test_user{i:02d}",
                    "level": level,
                    "pace": pace,
                    "dates": [
                        (2026, 8, 10),
                        (2026, 9, 10),
                        (2026, 10, 10),
                    ],
                })

            # 기존 API 테스트 사용자 5명
            for i in range(1, 6):
                users.append({
                    "user_id": f"api_test_user{i}",
                    "level": i * 2,
                    "pace": 480 + i * 10,
                    "dates": [
                        (2026, 8, 15),
                        (2026, 9, 15),
                        (2026, 10, 15),
                    ],
                })

            # 월중 참가 신청 전용 사용자
            users.append({
                "user_id": "midmonth_applicant",
                "level": 19,
                "pace": 340,
                "dates": [
                    (2026, 11, 5),
                    (2026, 11, 10),
                    (2026, 11, 15),
                ],
            })

            for index, user in enumerate(users):
                user_id = user["user_id"]
                character_id = character_ids[index % len(character_ids)]

                for year, month, day in user["dates"]:
                    distance = 5000.0
                    pace = float(user["pace"])
                    duration = distance / 1000 * pace

                    cursor.execute(
                        """
                        INSERT INTO running_record
                        (user_id, run_date, distance, duration, pace,
                         avg_speed, max_speed, environment)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                        """,
                        (
                            user_id,
                            to_timestamp(year, month, day),
                            distance,
                            duration,
                            pace,
                            3600 / pace,
                            3600 / pace * 1.2,
                            "CITY",
                        ),
                    )

                cursor.execute(
                    """
                    INSERT INTO user_character
                    (user_id, character_id, level, exp, is_main, acquired_at)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    """,
                    (
                        user_id,
                        character_id,
                        user["level"],
                        0,
                        1,
                        datetime.now(KST).replace(tzinfo=None),
                    ),
                )

        conn.commit()

        print("테스트 데이터 생성 완료")
        print(f"일반 그룹핑 대상자: 25명")
        print("월중 참가 신청자: 1명")
        print(f"추가된 러닝 기록: {len(users) * 3}개")
        print(f"등록된 캐릭터: {len(users)}개")

    except Exception:
        conn.rollback()
        raise

    finally:
        conn.close()


if __name__ == "__main__":
    main()

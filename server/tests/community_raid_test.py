# [구조변경] 예전 위치: backend/community_raid_test.py. DB 접속 정보 · 시간대는 config에서 가져옴

import argparse
import json
import calendar
from datetime import datetime, date
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

import pymysql

from config import DB_CONFIG, KST




BASE_URL = "http://127.0.0.1:8000"

JOINED_USER = "community_joined_test"
UNJOINED_USER = "community_unjoined_test"

GROUP_USERS = [
    JOINED_USER
] + [
    f"community_test_{i:02d}"
    for i in range(1, 15)
]

ALL_USERS = GROUP_USERS + [UNJOINED_USER]

TARGET_DISTANCE = 1_000_000
FINAL_DISTANCE = 10_000


def connect():
    return pymysql.connect(**DB_CONFIG)


def current_group_month():
    now = datetime.now(KST)
    return date(now.year, now.month, 1)


def previous_month(group_month, offset):
    year = group_month.year
    month = group_month.month - offset

    while month <= 0:
        month += 12
        year -= 1

    return date(year, month, 1)


def timestamp_ms(day):
    dt = datetime(
        day.year,
        day.month,
        day.day,
        12, 0, 0,
        tzinfo=KST
    )
    return int(dt.timestamp() * 1000)


def month_range_ms(group_month):
    next_year = group_month.year
    next_month = group_month.month + 1

    if next_month == 13:
        next_month = 1
        next_year += 1

    start = timestamp_ms(group_month)
    end = timestamp_ms(date(next_year, next_month, 1))

    return start, end


def insert_run(cur, user_id, run_day, distance):
    duration = distance / 1000.0 * 360

    cur.execute(
        """
        INSERT INTO running_record
        (
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
            timestamp_ms(run_day),
            distance,
            duration,
            360,
            10,
            12,
            "CITY",
        )
    )


def clear_local_data():
    print("[1/5] 기존 로컬 데이터 초기화")

    db = connect()

    try:
        with db.cursor() as cur:
            # FK 참조 관계에 맞춰 자식 테이블부터 삭제
            cur.execute("DELETE FROM run_cheer")
            cur.execute("DELETE FROM run_reaction")
            cur.execute("DELETE FROM community_raid")
            cur.execute("DELETE FROM community_group_member")
            cur.execute("DELETE FROM community_group")
            cur.execute("DELETE FROM community_grouping_history")
            cur.execute("DELETE FROM running_record")
            cur.execute("DELETE FROM user_character")

        db.commit()
        print("기존 데이터 삭제 완료")

    except Exception:
        db.rollback()
        raise

    finally:
        db.close()


def create_test_characters():
    print("[2/5] 테스트 계정 및 캐릭터 생성")

    db = connect()

    try:
        with db.cursor() as cur:
            for index, user_id in enumerate(ALL_USERS):
                character_id = (index % 14) + 1

                cur.execute(
                    """
                    INSERT INTO user_character
                    (
                        user_id,
                        character_id,
                        level,
                        exp,
                        is_main,
                        acquired_at
                    )
                    VALUES (%s, %s, 1, 0, 1, NOW())
                    """,
                    (user_id, character_id)
                )

        db.commit()
        print(f"테스트 계정 {len(ALL_USERS)}개 생성 완료")

    except Exception:
        db.rollback()
        raise

    finally:
        db.close()


def create_grouping_runs(group_month):
    """
    그룹핑 대상 15명에게 직전 3개월간
    유효한 러닝 기록 3개씩 생성한다.

    community_unjoined_test는 아직 기록을 만들지 않는다.
    """

    print("[3/5] 그룹핑용 러닝 기록 생성")

    db = connect()

    try:
        with db.cursor() as cur:
            for user_id in GROUP_USERS:
                for offset in (3, 2, 1):
                    run_month = previous_month(group_month, offset)

                    insert_run(
                        cur,
                        user_id,
                        date(run_month.year, run_month.month, 10),
                        5000
                    )

            # 그룹핑 대상 15명의 이번 달 거리 합계:
            # 57,000m + 58,000m + ... + 71,000m
            # = 960,000m
            first_run_day = date(
                group_month.year,
                group_month.month,
                5
            )

            second_run_day = date(
                group_month.year,
                group_month.month,
                20
            )

            for index, user_id in enumerate(GROUP_USERS):
                total_distance = 57_000 + index * 1_000

                first_distance = int(total_distance * 0.48)
                second_distance = total_distance - first_distance

                insert_run(
                    cur,
                    user_id,
                    first_run_day,
                    first_distance
                )

                insert_run(
                    cur,
                    user_id,
                    second_run_day,
                    second_distance
                )

        db.commit()
        print("그룹핑 대상 15명의 기록 생성 완료")

    except Exception:
        db.rollback()
        raise

    finally:
        db.close()


def run_grouping_api(group_month):
    print("[4/5] 월간 그룹핑 실행")

    url = BASE_URL + "/admin/community-groups/grouping"

    payload = json.dumps({
        "group_month": group_month.isoformat()
    }).encode("utf-8")

    request = Request(
        url,
        data=payload,
        headers={
            "Content-Type": "application/json"
        },
        method="POST"
    )

    try:
        with urlopen(request, timeout=30) as response:
            result = json.loads(
                response.read().decode("utf-8")
            )

    except HTTPError as e:
        print("그룹핑 API 오류:", e.code)
        print(e.read().decode("utf-8"))
        raise

    except URLError as e:
        raise RuntimeError(
            "백엔드 서버에 연결할 수 없습니다. "
            "uvicorn 실행 상태를 확인하세요."
        ) from e

    print("그룹핑 결과:")
    print(json.dumps(result, ensure_ascii=False, indent=2))

    if result.get("status") != "SUCCESS":
        raise RuntimeError("그룹핑이 성공하지 않았습니다.")

    if result.get("group_count") != 1:
        raise RuntimeError(
            "예상한 그룹 수는 1개입니다. "
            "실제 그룹 수를 확인하세요."
        )


def create_unjoined_runs(group_month):
    """
    그룹핑이 끝난 후 미가입 계정의 러닝 기록 생성.
    따라서 자동 그룹핑에는 포함되지 않는다.
    앱에서 직접 기존 그룹에 가입하도록 한다.
    """

    print("[5/5] 미가입 테스트 계정 기록 생성")

    db = connect()

    try:
        with db.cursor() as cur:
            for offset in (3, 2, 1):
                run_month = previous_month(group_month, offset)

                insert_run(
                    cur,
                    UNJOINED_USER,
                    date(run_month.year, run_month.month, 10),
                    5000
                )

            insert_run(
                cur,
                UNJOINED_USER,
                date(group_month.year, group_month.month, 5),
                14_000
            )

            insert_run(
                cur,
                UNJOINED_USER,
                date(group_month.year, group_month.month, 20),
                16_000
            )

        db.commit()
        print("미가입 계정 기록 생성 완료")

    except Exception:
        db.rollback()
        raise

    finally:
        db.close()


def verify_setup(group_month):
    db = connect()

    try:
        start_ms, end_ms = month_range_ms(group_month)

        with db.cursor() as cur:
            cur.execute(
                """
                SELECT group_id, user_id
                FROM community_group_member
                WHERE group_month = %s
                ORDER BY group_id, user_id
                """,
                (group_month,)
            )

            members = cur.fetchall()

            cur.execute(
                """
                SELECT group_id
                FROM community_group_member
                WHERE user_id = %s AND group_month = %s
                """,
                (JOINED_USER, group_month)
            )

            joined = cur.fetchone()

            cur.execute(
                """
                SELECT COUNT(*) AS cnt
                FROM community_group_member
                WHERE user_id = %s AND group_month = %s
                """,
                (UNJOINED_USER, group_month)
            )

            unjoined_count = cur.fetchone()["cnt"]

            if not joined:
                raise RuntimeError(
                    "community_joined_test가 그룹에 없습니다."
                )

            if unjoined_count != 0:
                raise RuntimeError(
                    "미가입 테스트 계정이 이미 그룹에 가입되어 있습니다."
                )

            group_id = joined["group_id"]

            cur.execute(
                """
                SELECT COALESCE(SUM(rr.distance), 0) AS total
                FROM community_group_member gm
                JOIN running_record rr
                  ON rr.user_id = gm.user_id
                WHERE gm.group_id = %s
                  AND gm.group_month = %s
                  AND rr.run_date >= %s
                  AND rr.run_date < %s
                """,
                (group_id, group_month, start_ms, end_ms)
            )

            total = cur.fetchone()["total"]

            print("\n===== 그룹핑 결과 확인 =====")
            print("그룹 ID:", group_id)
            print("현재 그룹 인원:", len(members))
            print("미가입 계정:", UNJOINED_USER)
            print("미가입 여부:", unjoined_count == 0)
            print("현재 그룹 거리:", int(total), "m")

            if len(members) != 15:
                raise RuntimeError(
                    "초기 그룹 인원이 15명이 아닙니다."
                )

            if int(total) != 960_000:
                raise RuntimeError(
                    f"예상 거리 960000m와 다릅니다: {total}"
                )

    finally:
        db.close()


def setup():
    group_month = current_group_month()

    print("테스트 월:", group_month)

    clear_local_data()
    create_test_characters()
    create_grouping_runs(group_month)
    run_grouping_api(group_month)
    create_unjoined_runs(group_month)
    verify_setup(group_month)

    print("\n초기 설정 완료")
    print("이제 Android 앱에서 그룹 참여 테스트를 진행하세요.")


def add_final_run():
    group_month = current_group_month()
    start_ms, end_ms = month_range_ms(group_month)

    db = connect()

    try:
        with db.cursor() as cur:
            cur.execute(
                """
                SELECT group_id
                FROM community_group_member
                WHERE user_id = %s AND group_month = %s
                """,
                (JOINED_USER, group_month)
            )

            member = cur.fetchone()

            if not member:
                raise RuntimeError(
                    "community_joined_test가 그룹에 없습니다."
                )

            group_id = member["group_id"]

            cur.execute(
                """
                SELECT COUNT(*) AS cnt
                FROM community_group_member
                WHERE user_id = %s AND group_month = %s
                """,
                (UNJOINED_USER, group_month)
            )

            if cur.fetchone()["cnt"] != 1:
                raise RuntimeError(
                    "먼저 앱에서 community_unjoined_test의 "
                    "그룹 참여를 완료하세요."
                )

            cur.execute(
                """
                SELECT COALESCE(SUM(rr.distance), 0) AS total
                FROM community_group_member gm
                JOIN running_record rr
                  ON rr.user_id = gm.user_id
                WHERE gm.group_id = %s
                  AND gm.group_month = %s
                  AND rr.run_date >= %s
                  AND rr.run_date < %s
                """,
                (group_id, group_month, start_ms, end_ms)
            )

            total = int(cur.fetchone()["total"])

            if total != 990_000:
                raise RuntimeError(
                    f"그룹 거리가 990000m가 아닙니다: {total}m"
                )

            cur.execute(
                """
                SELECT is_completed
                FROM community_raid
                WHERE group_id = %s AND group_month = %s
                """,
                (group_id, group_month)
            )

            raid = cur.fetchone()

            if raid is None:
                raise RuntimeError(
                    "아직 앱에서 레이드 조회를 실행하지 않았습니다. "
                    "앱에서 그룹 참여 후 레이드 정보를 먼저 조회하세요."
                )

            if raid["is_completed"]:
                raise RuntimeError(
                    "레이드가 이미 완료된 상태입니다."
                )

            cur.execute(
                """
                INSERT INTO running_record
                (
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
                    JOINED_USER,
                    int(datetime.now(KST).timestamp() * 1000),
                    FINAL_DISTANCE,
                    FINAL_DISTANCE / 1000 * 360,
                    360,
                    10,
                    12,
                    "CITY"
                )
            )

            running_number = cur.lastrowid

        db.commit()

        print("\n마지막 러닝 기록 추가 완료")
        print("running_number:", running_number)
        print("추가 거리:", FINAL_DISTANCE, "m")
        print("최종 그룹 거리:", total + FINAL_DISTANCE, "m")
        print("\n레이드 API는 호출하지 않았습니다.")
        print("이제 앱에서 커뮤니티 화면을 다시 열어주세요.")

    except Exception:
        db.rollback()
        raise

    finally:
        db.close()


def cleanup():
    answer = input(
        "정말 모든 로컬 테스트 데이터를 삭제할까요? (yes 입력): "
    )

    if answer != "yes":
        print("삭제 취소")
        return

    clear_local_data()
    print("모든 테스트 데이터 삭제 완료")
    print("character와 schema_version은 보존했습니다.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "action",
        choices=["setup", "final-run", "cleanup"]
    )

    args = parser.parse_args()

    if args.action == "setup":
        setup()
    elif args.action == "final-run":
        add_final_run()
    elif args.action == "cleanup":
        cleanup()


from datetime import date, datetime, time
from zoneinfo import ZoneInfo

from community_grouping import (
    get_db_connection,
    get_three_month_range,
    fetch_grouping_candidates,
    fetch_current_month_groups,
    fetch_midmonth_user_features,
    calculate_group_features,
    select_best_group,
    run_monthly_grouping,
    join_monthly_group,
)

KST = ZoneInfo("Asia/Seoul")

JOINED_USER = "community_joined_test"
UNJOINED_USER = "community_unjoined_test"
ANCHOR_USER = "community_join_test_anchor"

TEST_USERS = (JOINED_USER, UNJOINED_USER)

CHARACTER_ID = 1
CHARACTER_LEVEL = 10

# --------------------------------------------------
# 공통 데이터 생성
# --------------------------------------------------

def get_group_month():
    return datetime.now(KST).date().replace(day=1)


def get_run_dates(application_time):
    """
    신청 시점 기준 직전 3개월에 각각 한 번씩
    러닝 기록을 생성한다.
    """

    dates = []

    for offset in (3, 2, 1):
        month_index = (
            application_time.year * 12
            + application_time.month - 1
            - offset
        )

        year = month_index // 12
        month = month_index % 12 + 1

        dates.append(date(year, month, 10))

    return dates


def make_run_record(user_id, run_date, run_index):
    """
    테스트용 러닝 기록을 생성한다.

    거리: 5km
    평균 페이스: 기록마다 조금씩 다르게 설정
    """

    distance = 5000.0
    pace = 350.0 + (run_index * 5)
    duration = distance / 1000 * pace

    avg_speed = 3600 / pace
    max_speed = avg_speed + 2

    run_datetime = datetime.combine(
        run_date,
        time(8, 0),
        tzinfo=KST,
    )

    run_timestamp = int(
        run_datetime.timestamp() * 1000
    )

    return (
        user_id,
        run_timestamp,
        distance,
        duration,
        pace,
        avg_speed,
        max_speed,
        "CITY",
    )


# --------------------------------------------------
# 중복 데이터 확인
# --------------------------------------------------

def check_existing_test_users(conn):
    """
    두 테스트 계정에 기존 데이터가 있는지 확인한다.

    기존 데이터가 하나라도 있으면 중단한다.
    """

    for user_id in TEST_USERS:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) AS cnt FROM user_character WHERE user_id = %s",
                (user_id,),
            )
            character_count = cur.fetchone()["cnt"]

            cur.execute(
                "SELECT COUNT(*) AS cnt FROM running_record WHERE user_id = %s",
                (user_id,),
            )
            running_count = cur.fetchone()["cnt"]

            cur.execute(
                "SELECT COUNT(*) AS cnt FROM community_group_member WHERE user_id = %s",
                (user_id,),
            )
            membership_count = cur.fetchone()["cnt"]

        if character_count or running_count or membership_count:
            raise ValueError(
                f"{user_id} 계정의 기존 데이터가 발견되었습니다. "
                "중복 생성을 방지하기 위해 중단합니다."
            )


# --------------------------------------------------
# 테스트 사용자 생성
# --------------------------------------------------

def insert_test_user(conn, user_id, application_time):
    """
    테스트 계정에 대표 캐릭터와 러닝 기록 3개를 생성한다.
    """

    now = application_time.replace(tzinfo=None)

    run_dates = get_run_dates(application_time)

    run_records = [
        make_run_record(user_id, run_date, index)
        for index, run_date in enumerate(run_dates)
    ]

    with conn.cursor() as cur:
        cur.execute(
            "INSERT INTO user_character (user_id, character_id, level, exp, is_main, acquired_at) VALUES (%s, %s, %s, %s, %s, %s)",
            (
                user_id,
                CHARACTER_ID,
                CHARACTER_LEVEL,
                0,
                1,
                now,
            ),
        )

        cur.executemany(
            "INSERT INTO running_record (user_id, run_date, distance, duration, pace, avg_speed, max_speed, environment) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)",
            run_records,
        )


def insert_anchor_user(conn, application_time):
    """
    그룹핑 대상자가 한 명도 없을 때만 사용하는
    내부 기준 사용자 데이터를 생성한다.

    그룹핑 알고리즘이 최소 한 개의 그룹을 만들 수 있도록
    하는 용도이며 실제 테스트 계정은 아니다.
    """

    now = application_time.replace(tzinfo=None)

    run_dates = get_run_dates(application_time)

    run_records = [
        make_run_record(ANCHOR_USER, run_date, index)
        for index, run_date in enumerate(run_dates)
    ]

    with conn.cursor() as cur:
        cur.execute(
            "SELECT COUNT(*) AS cnt FROM user_character WHERE user_id = %s",
            (ANCHOR_USER,),
        )
        character_count = cur.fetchone()["cnt"]

        cur.execute(
            "SELECT COUNT(*) AS cnt FROM running_record WHERE user_id = %s",
            (ANCHOR_USER,),
        )
        running_count = cur.fetchone()["cnt"]

    if character_count or running_count:
        raise ValueError(
            "기준 사용자 데이터가 이미 존재합니다. "
            "데이터를 확인한 후 진행해 주세요."
        )

    with conn.cursor() as cur:
        cur.execute(
            "INSERT INTO user_character (user_id, character_id, level, exp, is_main, acquired_at) VALUES (%s, %s, %s, %s, %s, %s)",
            (
                ANCHOR_USER,
                CHARACTER_ID,
                CHARACTER_LEVEL,
                0,
                1,
                now,
            ),
        )

        cur.executemany(
            "INSERT INTO running_record (user_id, run_date, distance, duration, pace, avg_speed, max_speed, environment) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)",
            run_records,
        )


# --------------------------------------------------
# 그룹 상태 확인 및 준비
# --------------------------------------------------

def ensure_grouping_ready(application_time, group_month):
    """
    현재 월의 그룹 상태를 확인한다.

    1. 그룹과 SUCCESS 이력이 모두 있으면 그대로 사용
    2. 둘 다 없으면 최초 그룹핑 실행
    3. 둘 중 하나만 있으면 데이터 보호를 위해 중단
    """

    conn = get_db_connection()

    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) AS cnt FROM community_group WHERE group_month = %s",
                (group_month,),
            )
            group_count = cur.fetchone()["cnt"]

            cur.execute(
                "SELECT COUNT(*) AS cnt FROM community_grouping_history WHERE group_month = %s AND status = 'SUCCESS'",
                (group_month,),
            )
            success_count = cur.fetchone()["cnt"]

            candidates = fetch_grouping_candidates(
                cur,
                group_month,
            )

        # 그룹과 성공 이력이 모두 존재하면 기존 데이터 사용
        if group_count > 0 and success_count > 0:
            print("기존 그룹과 성공한 그룹핑 이력을 확인했습니다.")
            print("기존 그룹을 변경하지 않습니다.")
            return

        # 그룹과 성공 이력이 모두 없으면 최초 그룹핑 진행
        if group_count == 0 and success_count == 0:
            print("현재 월에 사용할 그룹이 없습니다.")

            # 그룹핑 대상자가 한 명도 없는 경우에만 기준 사용자 생성
            if not candidates:
                print("그룹핑 대상자가 없어 기준 사용자를 생성합니다.")

                insert_anchor_user(
                    conn,
                    application_time,
                )

                conn.commit()

            conn.close()
            conn = None

            result = run_monthly_grouping(
                group_month,
                "ADMIN",
            )

            print("최초 그룹핑 결과:", result)

            if result["status"] != "SUCCESS":
                raise ValueError("최초 그룹핑에 실패했습니다.")

            return

        # 일부 데이터만 존재하는 상태에서는 절대 자동 복구하지 않는다.
        raise ValueError(
            "현재 월의 그룹과 성공한 그룹핑 이력이 일치하지 않습니다. "
            "기존 데이터를 보호하기 위해 자동 그룹핑을 중단합니다."
        )

    finally:
        if conn is not None:
            conn.close()


# --------------------------------------------------
# 실제 가입 가능 여부 사전 검사
# --------------------------------------------------

def check_join_possible(application_time, group_month):
    """
    기존 그룹 중 가입자를 비교할 수 있는 그룹이 있는지 확인한다.

    가입이 불가능한 상태라면 테스트 계정 데이터를
    생성하기 전에 중단한다.
    """

    conn = get_db_connection()

    try:
        with conn.cursor() as cur:
            groups = fetch_current_month_groups(
                cur,
                group_month,
            )

            existing_user_ids = [
                member["user_id"]
                for group in groups
                for member in group["members"]
            ]

            user_features = fetch_midmonth_user_features(
                cur,
                list(dict.fromkeys(existing_user_ids)),
                application_time,
            )

            applicant_features = {
                "user_id": JOINED_USER,
                "total_level": CHARACTER_LEVEL,
                "average_pace": 360.0,
            }

            selected_group = select_best_group(
                applicant_features,
                groups,
                user_features,
            )

            if selected_group is None:
                raise ValueError(
                    "가입 가능한 기존 그룹을 찾을 수 없습니다. "
                    "그룹에 빈자리가 있는지, 유효한 러닝 기록을 가진 "
                    "그룹원이 있는지 확인해 주세요."
                )

            print(
                "가입 가능한 그룹 확인:",
                selected_group["group_number"],
            )

    finally:
        conn.close()


# --------------------------------------------------
# 결과 확인
# --------------------------------------------------

def verify_test_users(group_month):
    conn = get_db_connection()

    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) AS cnt FROM community_group_member WHERE user_id = %s AND group_month = %s",
                (JOINED_USER, group_month),
            )
            joined_count = cur.fetchone()["cnt"]

            cur.execute(
                "SELECT COUNT(*) AS cnt FROM community_group_member WHERE user_id = %s AND group_month = %s",
                (UNJOINED_USER, group_month),
            )
            unjoined_count = cur.fetchone()["cnt"]

            cur.execute(
                "SELECT COUNT(*) AS cnt FROM user_character WHERE user_id IN (%s, %s) AND is_main = 1",
                (JOINED_USER, UNJOINED_USER),
            )
            main_character_count = cur.fetchone()["cnt"]

            cur.execute(
                "SELECT user_id, COUNT(*) AS run_count FROM running_record WHERE user_id IN (%s, %s) GROUP BY user_id",
                (JOINED_USER, UNJOINED_USER),
            )
            run_counts = cur.fetchall()

        if joined_count != 1:
            raise ValueError("가입 계정의 멤버십 확인에 실패했습니다.")

        if unjoined_count != 0:
            raise ValueError("미가입 계정에 멤버십이 생성되었습니다.")

        if main_character_count != 2:
            raise ValueError("대표 캐릭터 데이터 확인에 실패했습니다.")

        if len(run_counts) != 2 or any(row["run_count"] != 3 for row in run_counts):
            raise ValueError("러닝 기록 개수 확인에 실패했습니다.")

        print("\n===== 테스트 데이터 생성 완료 =====")
        print(f"가입 계정: {JOINED_USER}")
        print("가입 상태: 가입 완료")

        print(f"\n미가입 계정: {UNJOINED_USER}")
        print("가입 상태: 미가입")

        print("\n두 계정 모두 대표 캐릭터와 러닝 기록 3개를 보유합니다.")

    finally:
        conn.close()


# --------------------------------------------------
# 실행
# --------------------------------------------------

def main():
    application_time = datetime.now(KST)
    group_month = application_time.date().replace(day=1)

    print("===== 커뮤니티 가입 테스트 데이터 생성 =====")
    print("대상 월:", group_month)

    conn = get_db_connection()

    try:
        check_existing_test_users(conn)
    finally:
        conn.close()

    # 1. 현재 월의 그룹 준비
    ensure_grouping_ready(
        application_time,
        group_month,
    )

    # 2. 실제 가입 가능한 기존 그룹이 있는지 확인
    check_join_possible(
        application_time,
        group_month,
    )

    # 3. 두 테스트 계정 생성
    conn = get_db_connection()

    try:
        conn.begin()

        insert_test_user(
            conn,
            JOINED_USER,
            application_time,
        )

        insert_test_user(
            conn,
            UNJOINED_USER,
            application_time,
        )

        conn.commit()

        print("\n테스트 계정 2개와 러닝 기록을 생성했습니다.")

    except Exception:
        conn.rollback()
        raise

    finally:
        conn.close()

    # 4. 가입 계정만 실제 가입 함수 호출
    join_result = join_monthly_group(
        JOINED_USER,
        application_time,
    )

    print("\n===== 실제 그룹 가입 결과 =====")
    print(join_result)

    # 5. 최종 상태 검증
    verify_test_users(group_month)


if __name__ == "__main__":
    try:
        main()

    except Exception as error:
        print("\n테스트 데이터 생성 실패")
        print("오류:", error)

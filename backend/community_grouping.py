from datetime import date, datetime, time
from zoneinfo import ZoneInfo
from typing import Any
from scipy.optimize import linear_sum_assignment
import numpy as np
import math
import pymysql

KST = ZoneInfo("Asia/Seoul")
MAX_GROUP_SIZE = 20

def get_db_connection():
    """
    RUNCATCH MySQL 데이터베이스에 연결한다.
    """

    return pymysql.connect(
        host="localhost",
        port=3306,
        user="runcatch",
        password="runcatch1234",
        database="runcatch",
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )

def calculate_user_features(
    running_records: list[dict[str, Any]],
    character_levels: list[int],
) -> dict[str, float] | None:
    """
    사용자의 그룹핑 특징값을 계산한다.

    - total_level: 보유 캐릭터 총 레벨
    - average_pace: 최근 3개월 평균 페이스(초/km)

    유효 러닝 기록이 3회 미만이면 None을 반환한다.
    """

    valid_records = [
        record
        for record in running_records
        if record["distance"] is not None
        and record["duration"] is not None
        and record["distance"] > 0
        and record["duration"] > 0
    ]

    if len(valid_records) < 3:
        return None

    total_distance = sum(
        record["distance"] for record in valid_records
    )

    total_duration = sum(
        record["duration"] for record in valid_records
    )

    total_level = sum(character_levels)

    average_pace = total_duration / (total_distance / 1000)

    return {
        "total_level": float(total_level),
        "average_pace": float(average_pace),
    }

def get_three_month_range(group_month: date) -> tuple[int, int]:
    """
    그룹핑 대상 월의 직전 3개월 범위를
    Unix timestamp 밀리초로 반환한다.

    예: 2026-12-01 그룹핑
        시작: 2026-09-01 00:00:00 KST
        종료: 2026-12-01 00:00:00 KST
    """

    if group_month.day != 1:
        raise ValueError("group_month는 해당 월의 1일이어야 합니다.")

    end_date = group_month

    month_index = group_month.year * 12 + group_month.month - 1 - 3
    start_year = month_index // 12
    start_month = month_index % 12 + 1

    start_date = date(start_year, start_month, 1)

    start_datetime = datetime.combine(
        start_date, time.min, tzinfo=KST
    )
    end_datetime = datetime.combine(
        end_date, time.min, tzinfo=KST
    )

    start_ms = int(start_datetime.timestamp() * 1000)
    end_ms = int(end_datetime.timestamp() * 1000)

    return start_ms, end_ms

def fetch_grouping_candidates(
    cur,
    group_month: date,
) -> list[dict]:
    """
    최근 3개월의 유효 러닝 기록이 3회 이상인
    사용자들의 그룹핑 특징값을 조회한다.
    """

    start_ms, end_ms = get_three_month_range(group_month)

    cur.execute(
        """
        WITH running_features AS (
            SELECT
                user_id,
                COUNT(*) AS run_count,
                SUM(distance) AS total_distance,
                SUM(duration) AS total_duration
            FROM running_record
            WHERE run_date >= %s
              AND run_date < %s
              AND distance > 0
              AND duration > 0
            GROUP BY user_id
            HAVING COUNT(*) >= 3
        ),
        character_features AS (
            SELECT
                user_id,
                SUM(level) AS total_level
            FROM user_character
            GROUP BY user_id
        )
        SELECT
            r.user_id,
            r.run_count,
            COALESCE(c.total_level, 0) AS total_level,
            r.total_duration / (r.total_distance / 1000)
                AS average_pace
        FROM running_features r
        LEFT JOIN character_features c
            ON r.user_id = c.user_id
        ORDER BY r.user_id
        """,
        (start_ms, end_ms),
    )

    return cur.fetchall()

def count_excluded_users(cur, group_month):
    """
    최근 3개월 동안 유효 러닝 기록이 1~2회인 사용자 수를 계산한다.

    별도의 함수로 분리한 이유:
    - 현재 DB에는 전체 가입자 정보를 관리하는 user 테이블이 없다.
    - 따라서 전체 사용자 수에서 그룹핑 대상자 수를 빼는 방식으로
      제외 인원을 계산할 수 없다.
    - 대신 running_record 테이블에서 유효 러닝 기록이
      1~2회인 사용자를 직접 조회한다.

    참고:
    - 러닝 기록이 0회인 사용자는 현재 DB 구조로 파악할 수 없다.
    - 추후 user 테이블이 추가되면 전체 가입자 수에서
      그룹핑 대상자 수를 빼는 방식으로 변경할 수 있다.
    """
    start_ms, end_ms = get_three_month_range(group_month)

    query = """
        SELECT COUNT(*) AS excluded_count
        FROM (
            SELECT user_id
            FROM running_record
            WHERE run_date >= %s
              AND run_date < %s
              AND distance > 0
              AND duration > 0
            GROUP BY user_id
            HAVING COUNT(*) BETWEEN 1 AND 2
        ) AS excluded_users
    """

    cur.execute(query, (start_ms, end_ms))
    result = cur.fetchone()

    return result["excluded_count"]

def normalize_features(
    candidates: list[dict],
) -> list[dict]:
    """
    캐릭터 총 레벨과 평균 페이스를 0~1 범위로 정규화한다.

    캐릭터 레벨: 낮을수록 0, 높을수록 1
    평균 페이스: 빠를수록 1, 느릴수록 0
    """

    if not candidates:
        return []

    levels = [float(user["total_level"]) for user in candidates]
    paces = [float(user["average_pace"]) for user in candidates]

    min_level = min(levels)
    max_level = max(levels)

    min_pace = min(paces)
    max_pace = max(paces)

    normalized = []

    for user in candidates:
        level = float(user["total_level"])
        pace = float(user["average_pace"])

        if max_level == min_level:
            normalized_level = 0.0
        else:
            normalized_level = (
                (level - min_level) / (max_level - min_level)
            )

        if max_pace == min_pace:
            normalized_pace = 0.0
        else:
            normalized_pace = (
                (max_pace - pace) / (max_pace - min_pace)
            )

        normalized.append({
            **user,
            "normalized_level": normalized_level,
            "normalized_pace": normalized_pace,
        })

    return normalized

def calculate_distance(
    user_a: dict,
    user_b: dict,
) -> float:
    """
    두 사용자 사이의 그룹핑 거리를 계산한다.

    캐릭터 레벨과 평균 페이스에 각각 50% 가중치를 적용한다.
    """

    level_diff = (
        user_a["normalized_level"]
        - user_b["normalized_level"]
    )

    pace_diff = (
        user_a["normalized_pace"]
        - user_b["normalized_pace"]
    )

    return math.sqrt(
        0.5 * (level_diff ** 2)
        + 0.5 * (pace_diff ** 2)
    )

def create_distance_matrix(
    candidates: list[dict],
) -> np.ndarray:
    """
    모든 사용자 사이의 거리를 계산하여
    거리 행렬을 생성한다.
    """

    user_count = len(candidates)

    distance_matrix = np.zeros(
        (user_count, user_count)
    )

    for i in range(user_count):
        for j in range(i + 1, user_count):
            distance = calculate_distance(
                candidates[i],
                candidates[j],
            )

            distance_matrix[i][j] = distance
            distance_matrix[j][i] = distance

    return distance_matrix

def calculate_group_count(user_count: int) -> int:
    """
    전체 사용자 수를 기준으로 필요한 그룹 수를 계산한다.

    그룹당 최대 인원은 20명이다.
    """

    if user_count < 0:
        raise ValueError("사용자 수는 음수일 수 없습니다.")

    if user_count == 0:
        return 0

    return math.ceil(user_count / MAX_GROUP_SIZE)

def calculate_group_capacities(
    user_count: int,
    group_count: int,
) -> list[int]:
    """
    전체 사용자를 그룹별로 최대한 균등하게 배분한다.

    각 그룹의 최대 인원은 20명이다.
    """

    if user_count < 0:
        raise ValueError("사용자 수는 음수일 수 없습니다.")

    if group_count < 0:
        raise ValueError("그룹 수는 음수일 수 없습니다.")

    if user_count == 0 and group_count == 0:
        return []

    if group_count == 0:
        raise ValueError("사용자가 있으면 그룹이 필요합니다.")

    if user_count == 0:
        raise ValueError("사용자가 없으면 그룹을 만들 수 없습니다.")

    if group_count > user_count:
        raise ValueError("그룹 수가 사용자 수보다 많을 수 없습니다.")

    if group_count * MAX_GROUP_SIZE < user_count:
        raise ValueError("그룹의 최대 수용 인원을 초과했습니다.")

    base_size = user_count // group_count
    remainder = user_count % group_count

    capacities = []

    for i in range(group_count):
        if i < remainder:
            capacities.append(base_size + 1)
        else:
            capacities.append(base_size)

    return capacities

def initialize_centroids(
    candidates: list[dict],
    group_count: int,
    random_seed: int = 42,
) -> np.ndarray:
    """
    K-means++ 방식으로 초기 그룹 중심점을 선정한다.

    각 중심점은 다음 두 특징값으로 구성된다.
    - normalized_level
    - normalized_pace
    """

    if not candidates:
        raise ValueError("그룹핑 대상자가 없습니다.")

    if group_count <= 0 or group_count > len(candidates):
        raise ValueError("올바르지 않은 그룹 수입니다.")

    features = np.array([
        [
            user["normalized_level"],
            user["normalized_pace"],
        ]
        for user in candidates
    ], dtype=float)

    rng = np.random.default_rng(random_seed)

    centroids = []
    selected_indices = []

    first_index = rng.integers(len(features))

    centroids.append(features[first_index])
    selected_indices.append(first_index)

    while len(centroids) < group_count:
        current_centroids = np.array(centroids)

        distances = np.linalg.norm(
            features[:, np.newaxis, :]
            - current_centroids[np.newaxis, :, :],
            axis=2,
        )

        min_distances = np.min(distances, axis=1)
        probabilities = min_distances ** 2

        # 이미 선택한 사용자는 다시 선택하지 않는다.
        probabilities[selected_indices] = 0

        probability_sum = probabilities.sum()

        if probability_sum == 0:
            remaining_indices = [
                i for i in range(len(features))
                if i not in selected_indices
            ]

            next_index = rng.choice(remaining_indices)

        else:
            probabilities /= probability_sum

            next_index = rng.choice(
                len(features),
                p=probabilities,
            )

        centroids.append(features[next_index])
        selected_indices.append(next_index)

    return np.array(centroids)

def assign_users_to_groups(
    candidates: list[dict],
    centroids: np.ndarray,
    capacities: list[int],
) -> np.ndarray:
    """
    각 사용자를 그룹 중심점과의 거리가 최소가 되도록 배정한다.

    그룹별 수용 인원을 반드시 지키며,
    전체 배정 거리의 합이 최소가 되도록 한다.

    반환값:
        각 사용자의 그룹 번호를 담은 배열
    """

    user_count = len(candidates)
    group_count = len(centroids)

    if user_count == 0:
        raise ValueError("그룹핑 대상자가 없습니다.")

    if group_count == 0:
        raise ValueError("그룹 중심점이 없습니다.")

    if len(capacities) != group_count:
        raise ValueError("그룹 수와 수용 인원 목록의 길이가 다릅니다.")

    if sum(capacities) != user_count:
        raise ValueError("그룹별 수용 인원의 합이 사용자 수와 다릅니다.")

    if any(capacity <= 0 or capacity > MAX_GROUP_SIZE for capacity in capacities):
        raise ValueError("그룹별 수용 인원이 올바르지 않습니다.")

    # 사용자들의 특징값을 배열로 변환한다.
    features = np.array([
        [
            user["normalized_level"],
            user["normalized_pace"],
        ]
        for user in candidates
    ], dtype=float)

    # 사용자와 각 그룹 중심점 사이의 거리를 계산한다.
    distances = np.linalg.norm(
        features[:, np.newaxis, :]
        - centroids[np.newaxis, :, :],
        axis=2,
    )

    # 그룹별 수용 인원만큼 그룹 번호를 반복하여 슬롯을 만든다.
    group_slots = np.repeat(
        np.arange(group_count),
        capacities,
    )

    # 각 사용자와 각 슬롯 사이의 비용 행렬을 생성한다.
    cost_matrix = distances[:, group_slots] ** 2

    # 전체 비용 합이 최소가 되도록 사용자를 슬롯에 배정한다.
    user_indices, slot_indices = linear_sum_assignment(cost_matrix)

    # 각 사용자에게 배정된 그룹 번호를 저장한다.
    assignments = np.empty(user_count, dtype=int)
    assignments[user_indices] = group_slots[slot_indices]

    return assignments

def update_centroids(
    candidates: list[dict],
    assignments: np.ndarray,
    group_count: int,
) -> np.ndarray:
    """
    각 그룹에 배정된 사용자들의 평균 특징값으로
    새로운 그룹 중심점을 계산한다.
    """

    user_count = len(candidates)

    if user_count == 0:
        raise ValueError("그룹핑 대상자가 없습니다.")

    if len(assignments) != user_count:
        raise ValueError("사용자 수와 배정 결과의 길이가 다릅니다.")

    if group_count <= 0:
        raise ValueError("올바르지 않은 그룹 수입니다.")

    features = np.array([
        [
            user["normalized_level"],
            user["normalized_pace"],
        ]
        for user in candidates
    ], dtype=float)

    new_centroids = np.zeros(
        (group_count, 2),
        dtype=float,
    )

    for group_id in range(group_count):
        # 현재 그룹에 배정된 사용자들의 특징값만 선택한다.
        group_features = features[assignments == group_id]

        if len(group_features) == 0:
            raise ValueError(
                f"{group_id}번 그룹에 배정된 사용자가 없습니다."
            )

        # 각 특징값의 평균을 새로운 중심점으로 설정한다.
        new_centroids[group_id] = np.mean(
            group_features,
            axis=0,
        )

    return new_centroids

def run_clustering(
    candidates: list[dict[str, Any]],
    group_count: int,
    capacities: list[int],
    random_seed: int = 42,
    max_iterations: int = 100,
) -> tuple[np.ndarray, np.ndarray]:
    """
    그룹핑 알고리즘을 실행한다.

    - K-means++ 방식으로 초기 중심점 설정
    - 사용자를 그룹에 배정
    - 그룹별 중심점 갱신
    - 그룹 배정이 더 이상 변하지 않으면 종료
    - 최대 반복 횟수는 100회

    반환값:
    - assignments: 각 사용자의 그룹 번호
    - centroids: 최종 그룹 중심점
    """

    user_count = len(candidates)

    if user_count == 0:
        raise ValueError("그룹핑 대상 사용자가 없습니다.")

    if group_count <= 0 or group_count > user_count:
        raise ValueError("그룹 개수가 올바르지 않습니다.")

    if len(capacities) != group_count:
        raise ValueError("그룹별 정원 정보가 올바르지 않습니다.")

    if sum(capacities) != user_count:
        raise ValueError("그룹별 정원의 합이 사용자 수와 다릅니다.")

    if max_iterations <= 0:
        raise ValueError("최대 반복 횟수는 1 이상이어야 합니다.")

    # 1. 초기 중심점 설정
    centroids = initialize_centroids(
        candidates,
        group_count,
        random_seed
    )

    previous_assignments = None

    # 2. 그룹 배정과 중심점 갱신 반복
    for _ in range(max_iterations):

        # 사용자들을 그룹에 배정
        assignments = assign_users_to_groups(
            candidates,
            centroids,
            capacities
        )

        # 이전 반복과 그룹 배정이 같으면 종료
        if (
            previous_assignments is not None
            and np.array_equal(assignments, previous_assignments)
        ):
            break

        # 그룹별 새로운 중심점 계산
        centroids = update_centroids(
            candidates,
            assignments,
            group_count
        )

        # 현재 배정 결과 저장
        previous_assignments = assignments.copy()

    return assignments, centroids

def execute_grouping(
    candidates: list[dict],
) -> tuple[list[dict], np.ndarray, np.ndarray, list[int]]:
    """
    그룹핑 대상자를 정규화하고 그룹핑을 실행한다.

    대상자가 없으면 알고리즘을 실행하지 않는다.
    """

    # 1. 그룹핑 대상자가 없는 경우
    if not candidates:
        print("그룹핑 대상자가 없습니다.")
        return (
            [],
            np.array([], dtype=int),
            np.empty((0, 2)),
            [],
        )

    # 2. 특징값 정규화
    normalized_candidates = normalize_features(candidates)

    # 3. 그룹 개수 계산
    user_count = len(normalized_candidates)
    group_count = calculate_group_count(user_count)

    # 4. 그룹별 정원 계산
    capacities = calculate_group_capacities(
        user_count,
        group_count,
    )

    # 5. 그룹핑 실행
    assignments, centroids = run_clustering(
        normalized_candidates,
        group_count,
        capacities,
    )

    # 6. 결과 검증
    validate_grouping_result(
        normalized_candidates,
        assignments,
        group_count,
        capacities,
    )

    return (
        normalized_candidates,
        assignments,
        centroids,
        capacities,
    )

def validate_grouping_result(
    candidates: list[dict],
    assignments: np.ndarray,
    group_count: int,
    capacities: list[int],
) -> bool:
    """
    그룹핑 결과가 올바른지 검증한다.

    검증 항목:
    1. 모든 사용자가 배정되었는가?
    2. 배정 결과에 누락이나 중복이 없는가?
    3. 모든 그룹의 인원이 정원과 일치하는가?
    4. 그룹별 인원이 1명 이상 20명 이하인가?

    모든 검증을 통과하면 True를 반환한다.
    """

    user_count = len(candidates)

    # 1. 배정된 사용자 수 확인
    if len(assignments) != user_count:
        raise ValueError("배정된 사용자 수가 전체 사용자 수와 다릅니다.")

    # 2. 그룹 개수와 정원 정보 확인
    if len(capacities) != group_count:
        raise ValueError("그룹별 정원 정보가 올바르지 않습니다.")

    if len(set(user["user_id"] for user in candidates)) != user_count:
        raise ValueError("중복된 사용자 ID가 존재합니다.")

    # 3. 배정된 그룹 번호 확인
    if np.any(assignments < 0) or np.any(assignments >= group_count):
        raise ValueError("유효하지 않은 그룹 번호가 존재합니다.")

    # 4. 그룹별 인원 확인
    for group_id in range(group_count):

        member_count = int(np.sum(assignments == group_id))

        if member_count != capacities[group_id]:
            raise ValueError(
                f"{group_id}번 그룹의 인원이 정원과 일치하지 않습니다."
            )

        if not 1 <= member_count <= MAX_GROUP_SIZE:
            raise ValueError(
                f"{group_id}번 그룹의 인원이 허용 범위를 벗어났습니다."
            )

    # 5. 전체 그룹 인원 합계 확인
    if sum(
        int(np.sum(assignments == group_id))
        for group_id in range(group_count)
    ) != user_count:
        raise ValueError("전체 그룹 인원 합계가 사용자 수와 다릅니다.")

    return True

def test_grouping_algorithm():
    """가상 사용자 데이터로 그룹핑 알고리즘을 테스트한다."""

    # 1. 가상의 사용자 50명 생성
    candidates = []

    for i in range(50):
        candidates.append({
            "user_id": f"user{i + 1:02d}",
            "total_level": 10 + (i * 2),
            "average_pace": 300 + (i * 3),
        })

    # 2. 특징값 정규화
    candidates = normalize_features(candidates)

    # 3. 필요한 그룹 개수 계산
    user_count = len(candidates)
    group_count = calculate_group_count(user_count)

    # 4. 그룹별 정원 계산
    capacities = calculate_group_capacities(
        user_count,
        group_count,
    )

    # 5. 그룹핑 실행
    assignments, centroids = run_clustering(
        candidates,
        group_count,
        capacities,
    )

    # 6. 그룹핑 결과 검증
    is_valid = validate_grouping_result(
        candidates,
        assignments,
        group_count,
        capacities,
    )

    if is_valid:
        print("\n그룹핑 검증 성공!")

    # 7. 결과 출력
    print("===== 그룹핑 테스트 결과 =====")
    print(f"전체 사용자 수: {user_count}")
    print(f"그룹 개수: {group_count}")
    print(f"그룹별 정원: {capacities}")

    for group_id in range(group_count):
        group_users = [
            user
            for index, user in enumerate(candidates)
            if assignments[index] == group_id
        ]

        print(f"\n[{group_id}번 그룹] ({len(group_users)}명)")

        for user in group_users:
            print(
                f"{user['user_id']} | "
                f"레벨: {user['total_level']} | "
                f"평균 페이스: {user['average_pace']}"
            )

    print("\n===== 그룹별 중심점 =====")

    for group_id, centroid in enumerate(centroids):
        print(
            f"{group_id}번 그룹: "
            f"레벨={centroid[0]:.3f}, "
            f"페이스={centroid[1]:.3f}"
        )

def create_grouping_history(
    conn,
    group_month: date,
    execution_type: str,
    target_count: int,
    excluded_count: int,
) -> int:
    """
    그룹핑 실행 이력을 RUNNING 상태로 등록한다.

    등록된 이력의 history_id를 반환한다.
    """

    if execution_type not in ("ADMIN", "AUTO"):
        raise ValueError("실행 유형은 ADMIN 또는 AUTO여야 합니다.")

    cur = conn.cursor()

    try:
        cur.execute(
            """
            INSERT INTO community_grouping_history (
                group_month,
                status,
                started_at,
                target_count,
                excluded_count,
                group_count,
                execution_type
            )
            VALUES (%s, 'RUNNING', %s, %s, %s, 0, %s)
            """,
            (
                group_month,
                datetime.now(KST).replace(tzinfo=None),
                target_count,
                excluded_count,
                execution_type,
            ),
        )

        history_id = cur.lastrowid

        conn.commit()

        return history_id

    except Exception:
        conn.rollback()
        raise

    finally:
        cur.close()

def save_grouping_result(
    conn,
    group_month: date,
    history_id: int,
    candidates: list[dict],
    assignments: np.ndarray,
    capacities: list[int],
) -> None:
    """
    그룹핑 결과를 DB에 저장한다.

    - 기존 해당 월의 그룹과 멤버를 교체한다.
    - 새 그룹과 멤버를 등록한다.
    - 그룹핑 실행 이력을 SUCCESS로 변경한다.
    - 오류 발생 시 기존 그룹 데이터를 복구하고 이력을 FAILED로 변경한다.
    """

    cur = conn.cursor()

    try:
        # 1. 그룹핑 결과 검증
        if group_month.day != 1:
            raise ValueError("group_month는 해당 월의 1일이어야 합니다.")

        if history_id <= 0:
            raise ValueError("올바르지 않은 history_id입니다.")

        if len(assignments) != len(candidates):
            raise ValueError("사용자 수와 그룹 배정 결과가 일치하지 않습니다.")

        if len(capacities) != calculate_group_count(len(candidates)):
            raise ValueError("그룹 개수가 올바르지 않습니다.")

        if sum(capacities) != len(candidates):
            raise ValueError("그룹별 정원의 합이 사용자 수와 다릅니다.")

        if len(set(user["user_id"] for user in candidates)) != len(candidates):
            raise ValueError("중복된 사용자 ID가 존재합니다.")

        if validate_grouping_result(
            candidates,
            assignments,
            len(capacities),
            capacities,
        ) is False:
            raise ValueError("그룹핑 결과 검증에 실패했습니다.")

        # 2. 데이터 저장 트랜잭션 시작
        conn.begin()

        # 해당 실행 이력이 존재하고 RUNNING 상태인지 확인
        cur.execute(
            """
            SELECT history_id
            FROM community_grouping_history
            WHERE history_id = %s
              AND group_month = %s
              AND status = 'RUNNING'
            FOR UPDATE
            """,
            (history_id, group_month),
        )

        if cur.fetchone() is None:
            raise ValueError("실행 중인 그룹핑 이력을 찾을 수 없습니다.")

        # 3. 기존 해당 월의 그룹 멤버 삭제
        cur.execute(
            """
            DELETE FROM community_group_member
            WHERE group_month = %s
            """,
            (group_month,),
        )

        # 4. 기존 해당 월의 그룹 삭제
        cur.execute(
            """
            DELETE FROM community_group
            WHERE group_month = %s
            """,
            (group_month,),
        )

        # 5. 새로운 그룹 및 그룹 멤버 등록
        now = datetime.now(KST).replace(tzinfo=None)

        for group_index, capacity in enumerate(capacities):

            # 새로운 그룹 등록
            cur.execute(
                """
                INSERT INTO community_group (
                    history_id,
                    group_month,
                    group_number,
                    member_count,
                    created_at
                )
                VALUES (%s, %s, %s, %s, %s)
                """,
                (
                    history_id,
                    group_month,
                    group_index + 1,
                    capacity,
                    now,
                ),
            )

            group_id = cur.lastrowid

            # 해당 그룹에 배정된 사용자 찾기
            group_members = [
                (group_id, candidate["user_id"], group_month, now)
                for index, candidate in enumerate(candidates)
                if int(assignments[index]) == group_index
            ]

            # 그룹 멤버 등록
            if group_members:
                cur.executemany(
                    """
                    INSERT INTO community_group_member (
                        group_id,
                        user_id,
                        group_month,
                        assigned_at
                    )
                    VALUES (%s, %s, %s, %s)
                    """,
                    group_members,
                )

        # 6. 그룹핑 실행 이력을 SUCCESS로 변경
        cur.execute(
            """
            UPDATE community_grouping_history
            SET
                status = 'SUCCESS',
                finished_at = %s,
                group_count = %s,
                error_message = NULL
            WHERE history_id = %s
              AND group_month = %s
              AND status = 'RUNNING'
            """,
            (
                now,
                len(capacities),
                history_id,
                group_month,
            ),
        )

        if cur.rowcount != 1:
            raise ValueError("그룹핑 실행 이력 업데이트에 실패했습니다.")

        # 7. 모든 저장 작업 확정
        conn.commit()

    except Exception as error:
        # 그룹 데이터 변경 사항 전체 취소
        conn.rollback()

        # 실행 이력은 별도 트랜잭션으로 FAILED 처리
        failed_cur = conn.cursor()

        try:
            failed_cur.execute(
                """
                UPDATE community_grouping_history
                SET
                    status = 'FAILED',
                    finished_at = %s,
                    error_message = %s
                WHERE history_id = %s
                  AND group_month = %s
                  AND status = 'RUNNING'
                """,
                (
                    datetime.now(KST).replace(tzinfo=None),
                    str(error),
                    history_id,
                    group_month,
                ),
            )

            conn.commit()

        except Exception:
            conn.rollback()

        finally:
            failed_cur.close()

        # 원래 발생한 오류 전달
        raise

    finally:
        cur.close()

def run_monthly_grouping(
    group_month: date,
    execution_type: str,
) -> dict:
    """
    월별 그룹핑 전체 과정을 실행한다.
    """
    if group_month.day != 1:
        raise ValueError("group_month는 해당 월의 1일이어야 합니다.")

    conn = get_db_connection()
    history_id = None

    try:
        with conn.cursor() as cur:
            candidates = fetch_grouping_candidates(cur, group_month)
            excluded_count = count_excluded_users(cur, group_month)

        history_id = create_grouping_history(
            conn,
            group_month,
            execution_type,
            len(candidates),
            excluded_count,
        )

        normalized_candidates, assignments, centroids, capacities = (
            execute_grouping(candidates)
        )

        save_grouping_result(
            conn,
            group_month,
            history_id,
            normalized_candidates,
            assignments,
            capacities,
        )

        return {
            "history_id": history_id,
            "group_month": group_month,
            "status": "SUCCESS",
            "target_count": len(normalized_candidates),
            "excluded_count": excluded_count,
            "group_count": len(capacities),
        }

    except Exception as error:
        if history_id is not None:
            failed_cur = conn.cursor()
            try:
                failed_cur.execute(
                    """
                    UPDATE community_grouping_history
                    SET
                        status = 'FAILED',
                        finished_at = %s,
                        error_message = %s
                    WHERE history_id = %s
                      AND status = 'RUNNING'
                    """,
                    (
                        datetime.now(KST).replace(tzinfo=None),
                        str(error),
                        history_id,
                    ),
                )
                conn.commit()
            except Exception:
                conn.rollback()
            finally:
                failed_cur.close()
        raise

    finally:
        conn.close()

if __name__ == "__main__":
    test_grouping_algorithm()



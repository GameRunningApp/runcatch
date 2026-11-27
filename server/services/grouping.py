# [구조변경] 예전 위치: backend/community_grouping.py
#   DB 연결은 db.get_db(), 시간대 · 최대 인원은 config 사용
#   호출되지 않던 get_db_connection, calculate_user_features, create_distance_matrix 삭제
#   파일 안에 있던 가상 사용자 테스트는 tests/grouping_algorithm_test.py로 옮김
from datetime import date, datetime, time
from typing import Any
from scipy.optimize import linear_sum_assignment
import numpy as np
import math
from config import KST, MAX_GROUP_SIZE
from db import get_db

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

def get_midmonth_range(
    application_time: datetime | None = None,
) -> tuple[int, int]:
    """
    월중 참가 신청 시점 기준으로
    직전 3개월의 시작일부터 신청 시점까지의 범위를 반환한다.

    예: 2026-11-21 신청
        시작: 2026-08-01 00:00:00 KST
        종료: 2026-11-21 신청 시점
    """

    if application_time is None:
        application_time = datetime.now(KST)

    elif application_time.tzinfo is None:
        application_time = application_time.replace(tzinfo=KST)

    else:
        application_time = application_time.astimezone(KST)

    month_index = (
        application_time.year * 12
        + application_time.month - 1 - 3
    )

    start_year = month_index // 12
    start_month = month_index % 12 + 1

    start_date = date(start_year, start_month, 1)

    start_datetime = datetime.combine(
        start_date,
        time.min,
        tzinfo=KST,
    )

    start_ms = int(start_datetime.timestamp() * 1000)
    end_ms = int(application_time.timestamp() * 1000)

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

def fetch_midmonth_user_features(
    cur,
    user_ids: list[str],
    application_time: datetime | None = None,
) -> list[dict]:
    """
    월중 참가 매칭에 필요한 사용자 특징값을 조회한다.

    - 신청 시점 기준 직전 3개월부터 현재까지의 러닝 기록
    - 유효 러닝 기록 3회 이상
    - 보유 캐릭터 총 레벨
    - 평균 페이스
    """

    if not user_ids:
        return []

    start_ms, end_ms = get_midmonth_range(application_time)

    placeholders = ", ".join(["%s"] * len(user_ids))

    query = f"""
        WITH running_features AS (
            SELECT
                user_id,
                COUNT(*) AS run_count,
                SUM(distance) AS total_distance,
                SUM(duration) AS total_duration
            FROM running_record
            WHERE run_date >= %s
              AND run_date <= %s
              AND distance > 0
              AND duration > 0
              AND user_id IN ({placeholders})
            GROUP BY user_id
        ),
        character_features AS (
            SELECT
                user_id,
                SUM(level) AS total_level
            FROM user_character
            WHERE user_id IN ({placeholders})
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
    """

    params = (
        [start_ms, end_ms]
        + user_ids
        + user_ids
    )

    cur.execute(query, params)

    return cur.fetchall()

def fetch_current_month_groups(
    cur,
    group_month: date,
) -> list[dict]:
    """
    해당 월에 생성된 그룹과 그룹원 목록을 조회한다.

    그룹 정보와 그룹원 정보를 함께 가져오며,
    그룹 행에 FOR UPDATE를 적용해 동시 변경을 방지한다.
    """

    cur.execute(
        """
        SELECT
            g.group_id,
            g.group_number,
            g.member_count,
            m.user_id
        FROM community_group g
        LEFT JOIN community_group_member m
            ON g.group_id = m.group_id
           AND g.group_month = m.group_month
        WHERE g.group_month = %s
        ORDER BY g.group_number, m.user_id
        FOR UPDATE
        """,
        (group_month,),
    )

    rows = cur.fetchall()

    groups_by_id = {}

    for row in rows:
        group_id = row["group_id"]

        if group_id not in groups_by_id:
            groups_by_id[group_id] = {
                "group_id": group_id,
                "group_number": row["group_number"],
                "member_count": row["member_count"],
                "members": [],
            }

        # 그룹에 실제 참가자가 있는 경우에만 추가한다.
        if row["user_id"] is not None:
            groups_by_id[group_id]["members"].append({
                "user_id": row["user_id"],
            })

    return list(groups_by_id.values())

def calculate_group_features(
    group_members: list[dict],
    user_features: list[dict],
) -> dict | None:
    """
    기존 그룹원들의 특징값 평균으로 그룹 특징값을 계산한다.

    러닝 기록이 없는 그룹원은 특징값 계산에서 제외한다.
    """

    feature_by_user = {
        str(user["user_id"]): user
        for user in user_features
    }

    matched_members = [
        feature_by_user[str(member["user_id"])]
        for member in group_members
        if str(member["user_id"]) in feature_by_user
    ]

    if not matched_members:
        return None

    average_level = sum(
        float(user["total_level"])
        for user in matched_members
    ) / len(matched_members)

    average_pace = sum(
        float(user["average_pace"])
        for user in matched_members
    ) / len(matched_members)

    return {
        "total_level": average_level,
        "average_pace": average_pace,
        "feature_member_count": len(matched_members),
    }

def select_best_group(
    applicant_features: dict,
    groups: list[dict],
    user_features: list[dict],
) -> dict | None:
    """
    신규 참가자와 가장 유사한 기존 그룹을 선택한다.

    - 정원이 남아 있는 그룹만 비교한다.
    - 그룹원들의 평균 특징값을 그룹 대표값으로 사용한다.
    - 신규 참가자와 그룹 대표값을 동일한 기준으로 정규화한다.
    - 거리가 가장 가까운 그룹을 반환한다.

    적합한 그룹이 없으면 None을 반환한다.
    """

    group_candidates = []

    for group in groups:

        # 정원이 가득 찬 그룹은 제외한다.
        if group["member_count"] >= MAX_GROUP_SIZE:
            continue

        group_features = calculate_group_features(
            group["members"],
            user_features,
        )

        # 특징값을 계산할 수 없는 그룹은 제외한다.
        if group_features is None:
            continue

        group_candidates.append({
            "user_id": f"group_{group['group_id']}",
            "total_level": group_features["total_level"],
            "average_pace": group_features["average_pace"],
            "group_id": group["group_id"],
            "group_number": group["group_number"],
            "member_count": group["member_count"],
        })

    if not group_candidates:
        return None

    # 신규 참가자와 그룹 대표값을 함께 정규화한다.
    comparison_candidates = normalize_features(
        group_candidates + [{
            "user_id": str(applicant_features["user_id"]),
            "total_level": applicant_features["total_level"],
            "average_pace": applicant_features["average_pace"],
        }]
    )

    normalized_groups = comparison_candidates[:-1]
    normalized_applicant = comparison_candidates[-1]

    # 신규 참가자와 각 그룹 사이의 거리 계산
    for group in normalized_groups:
        group["distance"] = calculate_distance(
            normalized_applicant,
            group,
        )

    # 거리가 가장 가까운 그룹 선택
    return min(
        normalized_groups,
        key=lambda group: group["distance"],
    )

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

    - MySQL Named Lock으로 동시 실행 방지
    - AUTO 실행은 이미 성공한 월이면 건너뛴다.
    - ADMIN 실행은 기존처럼 재실행을 허용한다.
    - 실행 이력은 기존 정책대로 보존한다.
    """

    if group_month.day != 1:
        raise ValueError("group_month는 해당 월의 1일이어야 합니다.")

    if execution_type not in ("ADMIN", "AUTO"):
        raise ValueError("실행 유형은 ADMIN 또는 AUTO여야 합니다.")

    conn = get_db()
    history_id = None

    # 월별 고유 잠금 이름
    lock_name = f"runcatch_grouping_{group_month:%Y_%m}"
    lock_acquired = False

    try:
        # 1. 해당 월의 그룹핑 잠금 획득
        with conn.cursor() as cur:
            cur.execute(
                "SELECT GET_LOCK(%s, 0) AS lock_result",
                (lock_name,),
            )
            lock_result = cur.fetchone()["lock_result"]

        if lock_result != 1:
            raise ValueError(
                f"{group_month:%Y-%m} 그룹핑이 이미 실행 중입니다."
            )

        lock_acquired = True

        # 2. AUTO 실행이면 이미 성공한 이력이 있는지 확인
        if execution_type == "AUTO":
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT
                        history_id,
                        target_count,
                        excluded_count,
                        group_count
                    FROM community_grouping_history
                    WHERE group_month = %s
                      AND status = 'SUCCESS'
                    ORDER BY finished_at DESC, history_id DESC
                    LIMIT 1
                    """,
                    (group_month,),
                )

                existing = cur.fetchone()

            # 이미 성공한 그룹핑이 있다면 실행하지 않는다.
            if existing:
                return {
                    "history_id": existing["history_id"],
                    "group_month": group_month,
                    "status": "SKIPPED",
                    "target_count": existing["target_count"],
                    "excluded_count": existing["excluded_count"],
                    "group_count": existing["group_count"],
                    "message": "이미 해당 월의 그룹핑이 성공적으로 실행되었습니다.",
                }

        # 3. 그룹핑 대상자 조회
        with conn.cursor() as cur:
            candidates = fetch_grouping_candidates(
                cur,
                group_month,
            )

            excluded_count = count_excluded_users(
                cur,
                group_month,
            )

        # 4. 그룹핑 실행 이력 생성
        history_id = create_grouping_history(
            conn,
            group_month,
            execution_type,
            len(candidates),
            excluded_count,
        )

        # 5. 그룹핑 알고리즘 실행
        normalized_candidates, assignments, centroids, capacities = (
            execute_grouping(candidates)
        )

        # 6. 그룹핑 결과 저장
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
        # 실행 이력이 생성된 상태에서 오류가 발생하면 FAILED 처리
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
        # 7. 월별 잠금 해제
        if lock_acquired:
            try:
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT RELEASE_LOCK(%s)",
                        (lock_name,),
                    )
            except Exception as lock_error:
                print(f"그룹핑 잠금 해제 오류: {lock_error}")

        conn.close()

def fetch_latest_successful_history(
    cur,
    group_month: date,
) -> dict | None:
    """
    해당 월의 가장 최근 성공한 그룹핑 이력을 조회한다.

    새 그룹을 생성할 때 사용할 history_id를 가져온다.
    """

    cur.execute(
        """
        SELECT
            history_id,
            group_count
        FROM community_grouping_history
        WHERE group_month = %s
          AND status = 'SUCCESS'
        ORDER BY finished_at DESC, history_id DESC
        LIMIT 1
        FOR UPDATE
        """,
        (group_month,),
    )

    return cur.fetchone()

def join_monthly_group(
    user_id: str,
    application_time: datetime | None = None,
) -> dict:
    """
    월중 참가 신청자를 기존 그룹에 배정한다.

    - 유효 러닝 기록이 3회 이상인 사용자만 참가 가능
    - 가장 유사한 기존 그룹에 배정
    - 모든 그룹이 가득 찬 경우 새 그룹 생성
    - 기존 그룹원은 재배정하지 않음
    """

    # 1. 신청 시각을 한국 시간으로 통일한다.
    if application_time is None:
        application_time = datetime.now(KST)

    elif application_time.tzinfo is None:
        application_time = application_time.replace(tzinfo=KST)

    else:
        application_time = application_time.astimezone(KST)

    group_month = application_time.date().replace(day=1)

    conn = get_db()

    lock_name = f"runcatch_grouping_{group_month:%Y_%m}"
    lock_acquired = False

    try:
        # 2. 월별 그룹핑 잠금 획득
        with conn.cursor() as cur:
            cur.execute(
                "SELECT GET_LOCK(%s, 0) AS lock_result",
                (lock_name,),
            )

            lock_result = cur.fetchone()["lock_result"]

        if lock_result != 1:
            raise ValueError("현재 해당 월의 그룹핑 작업이 진행 중입니다.")

        lock_acquired = True

        conn.begin()

        with conn.cursor() as cur:

            # 3. 이미 해당 월에 참가한 사용자인지 확인
            cur.execute(
                """
                SELECT group_id
                FROM community_group_member
                WHERE user_id = %s
                  AND group_month = %s
                FOR UPDATE
                """,
                (user_id, group_month),
            )

            if cur.fetchone() is not None:
                raise ValueError("이미 해당 월의 그룹에 참가한 사용자입니다.")

            # 4. 해당 월의 성공한 그룹핑 이력 조회
            history = fetch_latest_successful_history(
                cur,
                group_month,
            )

            if history is None:
                raise ValueError("해당 월에 성공한 그룹핑 이력이 없습니다.")

            # 5. 기존 그룹과 그룹원 조회
            groups = fetch_current_month_groups(
                cur,
                group_month,
            )

            if not groups:
                raise ValueError("해당 월에 생성된 그룹이 없습니다.")

            # 6. 기존 그룹원과 신청자의 특징값 조회
            existing_user_ids = [
                member["user_id"]
                for group in groups
                for member in group["members"]
            ]

            all_user_ids = list(dict.fromkeys(
                existing_user_ids + [user_id]
            ))

            user_features = fetch_midmonth_user_features(
                cur,
                all_user_ids,
                application_time,
            )

            # 7. 신청자의 참가 자격 확인
            applicant = next(
                (
                    user
                    for user in user_features
                    if str(user["user_id"]) == str(user_id)
                ),
                None,
            )

            if applicant is None or applicant["run_count"] < 3:
                raise ValueError(
                    "최근 3개월 동안 유효한 러닝 기록이 3회 이상이어야 합니다."
                )

            applicant_features = {
                "user_id": user_id,
                "total_level": float(applicant["total_level"]),
                "average_pace": float(applicant["average_pace"]),
            }

            # 8. 가장 유사한 기존 그룹 선택
            selected_group = select_best_group(
                applicant_features,
                groups,
                user_features,
            )

            # 9. 기존 그룹에 빈자리가 있지만
            # 비교 가능한 그룹이 없다면 새 그룹을 만들지 않는다.
            has_available_group = any(
                group["member_count"] < MAX_GROUP_SIZE
                for group in groups
            )

            if selected_group is None and has_available_group:
                raise ValueError(
                    "비교 가능한 기존 그룹이 없어 참가자를 배정할 수 없습니다."
                )

            now = datetime.now(KST).replace(tzinfo=None)

            # 10. 기존 그룹에 배정
            if selected_group is not None:

                group_id = selected_group["group_id"]

                cur.execute(
                    """
                    UPDATE community_group
                    SET member_count = member_count + 1
                    WHERE group_id = %s
                      AND group_month = %s
                      AND member_count < %s
                    """,
                    (
                        group_id,
                        group_month,
                        MAX_GROUP_SIZE,
                    ),
                )

                if cur.rowcount != 1:
                    raise ValueError("그룹 정원을 변경할 수 없습니다.")

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
                        group_id,
                        user_id,
                        group_month,
                        now,
                    ),
                )

                result = {
                    "user_id": user_id,
                    "group_id": group_id,
                    "group_number": selected_group["group_number"],
                    "member_count": selected_group["member_count"] + 1,
                    "created_new_group": False,
                    "status": "SUCCESS",
                }

            # 11. 모든 기존 그룹이 가득 찬 경우 새 그룹 생성
            else:

                group_number = max(
                    group["group_number"]
                    for group in groups
                ) + 1

                cur.execute(
                    """
                    INSERT INTO community_group (
                        history_id,
                        group_month,
                        group_number,
                        member_count,
                        created_at
                    )
                    VALUES (%s, %s, %s, 1, %s)
                    """,
                    (
                        history["history_id"],
                        group_month,
                        group_number,
                        now,
                    ),
                )

                group_id = cur.lastrowid

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
                        group_id,
                        user_id,
                        group_month,
                        now,
                    ),
                )

                # 실제 그룹 개수를 다시 조회한다.
                cur.execute(
                    """
                    SELECT COUNT(*) AS group_count
                    FROM community_group
                    WHERE group_month = %s
                    """,
                    (group_month,),
                )

                new_group_count = cur.fetchone()["group_count"]

                # 가장 최근 성공 이력의 그룹 개수 갱신
                cur.execute(
                    """
                    UPDATE community_grouping_history
                    SET group_count = %s
                    WHERE history_id = %s
                      AND status = 'SUCCESS'
                    """,
                    (
                        new_group_count,
                        history["history_id"],
                    ),
                )

                result = {
                    "user_id": user_id,
                    "group_id": group_id,
                    "group_number": group_number,
                    "member_count": 1,
                    "created_new_group": True,
                    "status": "SUCCESS",
                }

        # 12. 모든 변경 사항 확정
        conn.commit()

        return result

    except Exception:
        conn.rollback()
        raise

    finally:
        # 13. 월별 잠금 해제
        if lock_acquired:
            try:
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT RELEASE_LOCK(%s)",
                        (lock_name,),
                    )
            except Exception as lock_error:
                print(f"그룹핑 잠금 해제 오류: {lock_error}")

        conn.close()

# [구조변경] 새 파일. 예전 community_grouping.py 안에 있던 test_grouping_algorithm()을 옮겨 옴
r"""
가상 사용자 50명으로 그룹 배정 알고리즘을 확인한다 (DB 없이 실행).
실행: server 폴더에서  venv\Scripts\python -m tests.grouping_algorithm_test
"""
from services.grouping import (
    calculate_group_capacities,
    calculate_group_count,
    normalize_features,
    run_clustering,
    validate_grouping_result,
)


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


if __name__ == "__main__":
    test_grouping_algorithm()

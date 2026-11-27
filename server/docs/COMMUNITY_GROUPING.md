# 커뮤니티 그룹핑 기능

## 1. 기능 개요

커뮤니티 그룹핑은 월별로 러닝 사용자들을 그룹으로 나누는 기능이다. 관리자 API를 통해 특정 월의 그룹핑을 실행하며, 실행 결과는 `community_group`과 `community_group_member` 테이블에 저장된다.

현재 구현은 관리자 수동 실행을 기준으로 한다. 매월 자동 실행, 그룹 조회 API, 월중 신규 사용자 배정은 아직 구현 범위에 포함되지 않는다.

## 2. 관련 파일

| 파일 | 역할 |
|---|---|
| `services/grouping.py` | 사용자 데이터 조회, 특성 계산, 정규화, 그룹핑 알고리즘, 결과 저장 및 실행 이력 관리 |
| `routers/community_grouping.py` | 관리자 그룹핑 실행 API, 그룹 가입·조회·피드·리그 API |

## 3. 그룹핑 기준

### 대상 사용자

- 그룹핑 대상 월의 직전 3개월 러닝 기록을 조회한다.
- 유효한 러닝 기록은 거리와 운동 시간이 모두 0보다 큰 기록이다.
- 해당 기간에 유효한 러닝 기록이 3회 이상인 사용자를 그룹핑 대상으로 선정한다.

### 사용자 특성

| 특성 | 계산 방법 |
|---|---|
| 캐릭터 레벨 합계 | 사용자가 보유한 캐릭터 레벨의 합 |
| 평균 페이스 | 전체 운동 시간 합계 ÷ 전체 러닝 거리(km) 합계 |

두 특성은 Min-Max 방식으로 정규화한다. 페이스는 숫자가 작을수록 빠른 것이므로, 빠른 사용자가 높은 정규화 값을 갖도록 역방향으로 변환한다.

두 특성의 가중치는 각각 50%로 적용한다.

### 그룹 구성

- 그룹당 최대 인원은 20명이다.
- 그룹 수는 대상 인원에 대해 `ceil(대상 인원 / 20)`으로 계산한다.
- 그룹별 인원수가 가능한 한 균형을 이루도록 수용 인원을 배분한다.
- K-means++ 방식으로 초기 중심점을 정하고, 그룹별 수용 인원 제한을 고려해 사용자를 배정한다.
- 배정과 중심점 갱신을 반복하며, 최대 반복 횟수는 100회다.

## 4. 실행 이력과 재실행 처리

그룹핑 실행은 `community_grouping_history`에 이력으로 남는다.

- 실행 시작 시 상태를 `RUNNING`으로 기록한다.
- 정상 완료 시 `SUCCESS`, 오류 발생 시 `FAILED`로 기록한다.
- 오류가 발생하면 오류 메시지도 이력에 저장한다.
- 같은 월의 그룹핑을 다시 실행하면 해당 월의 기존 그룹 및 멤버 결과를 새 결과로 교체한다.
- 과거 실행 이력은 삭제하지 않고 보존한다.
- 결과 저장은 트랜잭션으로 처리하여 저장 도중 오류가 발생하면 그룹 및 멤버 변경을 롤백한다.

## 5. 실행 환경 준비

아래 명령은 Windows PowerShell에서 `server` 폴더를 현재 위치로 두고 실행한다.

```powershell
.\venv\Scripts\Activate.ps1
```

가상환경이 준비되지 않았다면 프로젝트의 기존 환경 설정 절차에 따라 가상환경을 만들고 필요한 패키지를 설치한다. 그룹핑 기능에서 사용하는 주요 패키지는 다음과 같다.

```powershell
python -m pip install fastapi pydantic pymysql numpy scipy
```

> 프로젝트에 `requirements.txt`가 있다면 팀에서 정한 의존성 설치 방법을 우선한다. 이미 설치된 패키지를 불필요하게 다시 설치할 필요는 없다.

## 6. 백엔드 서버 실행

`server` 폴더의 가상환경을 활성화한 PowerShell에서 실행한다.

```powershell
uvicorn main:app --reload
```

서버 실행 후 Swagger 문서를 연다.

- `http://127.0.0.1:8000/docs`

## 7. 테스트 데이터 준비

그룹핑 API를 테스트하려면 대상 사용자의 러닝 기록이 필요하다. 현재 개발 DB에는 실제 서비스 사용자 데이터가 충분하지 않을 수 있으므로, 로컬 DB에 테스트용 러닝 기록을 추가해 테스트할 수 있다.

> **주의:** 아래 예시는 `api_test_user1`부터 `api_test_user5`까지의 테스트 계정 5개와 러닝 기록 15건을 추가한다. 같은 명령을 여러 번 실행하면 기록이 중복되므로 한 번만 실행한다. 반드시 로컬 개발 DB에 연결되어 있는지 확인한 뒤 실행한다.

### 7-1. 테스트 데이터가 필요한 이유

현재 그룹핑 대상은 지정한 월의 직전 3개월 동안 유효한 러닝 기록이 3회 이상인 사용자다. 아래 데이터는 각 테스트 사용자에게 러닝 기록을 3건씩 추가한다.

- 사용자: `api_test_user1` ~ `api_test_user5`
- 기록 날짜: 2026-10-01, 2026-10-15, 2026-11-01
- 거리: 각 기록 5,000m
- 운동 시간: 사용자별 페이스에 맞춰 설정
- 그룹핑 대상 월 예시: `2026-12-01`

### 7-2. 테스트 기록 추가

`server` 폴더의 가상환경을 활성화한 PowerShell에서 실행한다.

```powershell
python -c "from datetime import datetime; from community_grouping import get_db_connection; conn=get_db_connection(); cur=conn.cursor(); rows=[(f'api_test_user{i}', int(datetime(2026,m,d).timestamp()*1000), 5000, (300+i*20)*5, 300+i*20, 12, 15) for i in range(1,6) for m,d in [(10,1),(10,15),(11,1)]]; cur.executemany('INSERT INTO running_record (user_id, run_date, distance, duration, pace, avg_speed, max_speed) VALUES (%s,%s,%s,%s,%s,%s,%s)', rows); conn.commit(); print('추가된 테스트 러닝 기록:', cur.rowcount, '건'); cur.close(); conn.close()"
```

정상 실행되면 추가된 테스트 러닝 기록 수가 `15건`으로 표시된다.

이 명령은 `datetime.timestamp()`를 사용하므로 실행 PC의 로컬 시간대 설정에 영향을 받는다. 테스트 환경이 한국 시간(KST)으로 설정되어 있는지 확인한다.

### 7-3. 테스트 데이터 조회

MySQL에서 테스트 데이터가 추가되었는지 확인한다.

```sql
USE runcatch;

SELECT
    user_id,
    COUNT(*) AS run_count
FROM running_record
WHERE user_id LIKE 'api_test_user%'
GROUP BY user_id
ORDER BY user_id;
```

각 테스트 사용자별 기록 수가 3건인지 확인한다. 기존에 같은 테스트 데이터를 넣은 적이 있다면 3건보다 많게 조회될 수 있으므로, API 테스트 전에 중복 여부를 확인한다.

---

## 8. 관리자 그룹핑 API 테스트

Swagger에서 다음 API를 찾는다.

`POST /admin/community-groups/grouping`

요청 본문 예시:

```json
{
  "group_month": "2026-12-01"
}
```

`group_month`는 그룹핑 대상 월의 첫째 날로 전달한다.

정상 실행 시 응답 예시:

```json
{
  "history_id": 8,
  "group_month": "2026-12-01",
  "status": "SUCCESS",
  "target_count": 5,
  "excluded_count": 0,
  "group_count": 1
}
```

위 값은 응답 형식을 설명하기 위한 예시이며, 실제 결과는 DB 데이터에 따라 달라진다.

## 9. DB에서 결과 확인

MySQL에서 데이터베이스를 선택한 뒤 조회한다.

```sql
USE runcatch;
```

특정 월의 그룹 목록과 그룹별 인원을 확인한다.

```sql
SELECT
    g.group_id,
    g.group_month,
    g.group_number,
    COUNT(gm.user_id) AS member_count
FROM community_group g
LEFT JOIN community_group_member gm
    ON g.group_id = gm.group_id
WHERE g.group_month = '2026-12-01'
GROUP BY g.group_id, g.group_month, g.group_number
ORDER BY g.group_number;
```

그룹에 속한 사용자 목록을 확인한다.

```sql
SELECT
    g.group_number,
    gm.user_id
FROM community_group g
JOIN community_group_member gm
    ON g.group_id = gm.group_id
WHERE g.group_month = '2026-12-01'
ORDER BY g.group_number, gm.user_id;
```

실행 이력을 확인한다.

```sql
SELECT
    history_id,
    group_month,
    status,
    target_count,
    group_count,
    execution_type,
    error_message
FROM community_grouping_history
WHERE group_month = '2026-12-01'
ORDER BY history_id;
```

## 10. 동일 월 재실행 확인

동일한 `group_month`로 API를 다시 호출하면 새 실행 이력이 추가되고, 해당 월의 기존 그룹 및 멤버 결과는 새 결과로 교체된다.

확인할 사항:

1. 재실행 전후 그룹 및 멤버 구성이 새 실행 결과로 바뀌었는지 확인한다.
2. 이전 실행 이력이 `community_grouping_history`에 남아 있는지 확인한다.
3. 새 실행 이력이 `SUCCESS`로 기록되었는지 확인한다.

실제 운영 DB가 아닌 로컬 개발 DB에서 테스트하는 것을 권장한다.

## 11. 현재 구현 범위와 향후 작업

### 현재 구현된 항목

- 최근 3개월 러닝 기록 기반 그룹핑 대상 조회
- 사용자 특성 계산 및 정규화
- 그룹 수와 그룹별 수용 인원 계산
- 수용 인원 제한을 고려한 그룹핑 알고리즘
- 그룹 및 멤버 결과 저장
- 실행 이력 기록과 실패 시 롤백
- 관리자 수동 실행 API

### 아직 구현하지 않은 항목

- 매월 지정된 시각의 자동 그룹핑
- 월중 신규 사용자의 기존 그룹 참여 처리
- 그룹 조회 API
- 실제 서비스 사용자 데이터와의 최종 연동 및 운영 환경 검증
- 관리자 API 인증 및 권한 검증

## 12. 테스트 시 주의사항

- 테스트 데이터 삽입은 같은 명령을 여러 번 실행하면 중복 데이터가 생길 수 있으므로 주의한다.
- `config.py`의 DB 설정을 확인하여 로컬 DB에 연결되어 있는지 먼저 확인한다.
- 팀 공용 DB나 운영 DB에서 임의의 테스트 데이터를 삽입하거나 그룹핑을 실행하지 않는다.
- 그룹핑 대상 월은 반드시 해당 월의 첫째 날로 지정한다.

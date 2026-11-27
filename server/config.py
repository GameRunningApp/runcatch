# [구조변경] 새 파일. main.py의 DB_CONFIG, community_grouping.py의 고정 DB 주소 · KST · 그룹 최대 인원,
#   main.py의 EXP_PER_LEVEL을 한곳에 모았음 (그룹 배정만 다른 DB에 붙던 문제 해결)
"""서버 설정값. 접속 정보는 환경 변수로 바꿀 수 있음 (없으면 로컬 MySQL 기본값)."""
import os
from zoneinfo import ZoneInfo

import pymysql

DB_CONFIG = {
    "host": os.getenv("DB_HOST", "localhost"),
    "port": int(os.getenv("DB_PORT", "3306")),
    "user": os.getenv("DB_USER", "runcatch"),
    # TODO(배포): 기본 비밀번호는 로컬 개발용. 배포 서버에서는 DB_PASSWORD 환경 변수로만 설정
    "password": os.getenv("DB_PASSWORD", "runcatch1234"),
    "database": os.getenv("DB_NAME", "runcatch"),
    "charset": "utf8mb4",
    "cursorclass": pymysql.cursors.DictCursor,
}

# 날짜 계산 기준 시간대 (그룹 배정, 레이드)
KST = ZoneInfo("Asia/Seoul")

# 대표 캐릭터 경험치: 러닝 1m = 1, 이 값마다 레벨업 (러닝 · 레이드 보상 공통)
EXP_PER_LEVEL = 5000

# 커뮤니티 그룹 최대 인원 (sql/create_community_tables.sql 의 CHECK와 같게 유지)
MAX_GROUP_SIZE = 20

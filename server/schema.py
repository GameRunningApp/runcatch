# main.py 맨 아래에서 import 시 바로 실행되던 테이블 생성 코드를 init_schema()로 옮김
#   DB 연결 작업에서 `user` 테이블 추가
"""테이블 생성과 캐릭터 초기 데이터. 서버 시작 시(main.lifespan) 한 번 실행한다."""
from characters import CHARACTERS
from db import db_cursor

# 테이블 구조를 바꾸면 올린다. DB의 버전이 더 낮으면 테이블을 지우고 새로 만든다 (개발 단계)
# TODO(배포): 실데이터가 생기면 DROP 대신 버전별 ALTER로 바꿔야 함
# 2: 캐릭터를 앱의 14종(정수 id)으로, running_record를 앱 구조로 변경
# 3: SQLite에서 MySQL로 변경
# 4: running_record에 environment(러닝 환경) 추가
SCHEMA_VERSION = 4


def init_schema():
    with db_cursor() as cur:
        # MySQL에는 SQLite의 PRAGMA user_version이 없어서 버전을 테이블에 저장
        cur.execute("CREATE TABLE IF NOT EXISTS schema_version (version INT NOT NULL)")
        cur.execute("SELECT version FROM schema_version")
        row = cur.fetchone()
        if row is None or row["version"] < SCHEMA_VERSION:
            cur.execute("DROP TABLE IF EXISTS run_reaction")
            cur.execute("DROP TABLE IF EXISTS run_cheer")
            cur.execute("DROP TABLE IF EXISTS user_character")
            cur.execute("DROP TABLE IF EXISTS `character`")
            cur.execute("DROP TABLE IF EXISTS running_record")
            cur.execute("DELETE FROM schema_version")
            cur.execute("INSERT INTO schema_version (version) VALUES (%s)", (SCHEMA_VERSION,))
        # 회원 (앱 회원가입 시 POST /users로 등록). user_id는 앱 닉네임
        # 여러 폰에서 같은 닉네임 · 이메일로 가입해 서버 데이터가 섞이지 않도록 중복을 막는 용도.
        # TODO(인증): 비밀번호 · 토큰은 아직 없음 (비밀번호는 앱 로컬 DB에만 해시로 저장)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS `user` (
                user_id    VARCHAR(100) PRIMARY KEY,
                mail       VARCHAR(255) NOT NULL UNIQUE,
                created_at DATETIME     NOT NULL
            )
        """)
        # 앱 running_record와 같은 구조 (GPS 경로는 받지 않음)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS running_record (
                running_number INT          PRIMARY KEY AUTO_INCREMENT,
                user_id        VARCHAR(100) NOT NULL,
                run_date       BIGINT       NOT NULL,  -- 러닝 시작 시각 (epoch ms)
                environment    VARCHAR(20),            -- CITY, PARK, FOREST, RIVER, LAKE
                distance       DOUBLE,                 -- m
                duration       DOUBLE,                 -- 초
                pace           DOUBLE,                 -- 초/km
                avg_speed      DOUBLE,                 -- km/h
                max_speed      DOUBLE                  -- km/h
            )
        """)
        # character, condition은 MySQL 예약어라서 ``로 감쌈
        cur.execute("""
            CREATE TABLE IF NOT EXISTS `character` (
                character_id      INT          PRIMARY KEY,
                name              VARCHAR(50)  NOT NULL,
                emoji             VARCHAR(20)  NOT NULL,
                category          VARCHAR(20)  NOT NULL,
                `condition`       VARCHAR(100) NOT NULL,
                hint              VARCHAR(200) NOT NULL,
                description       VARCHAR(200) NOT NULL,
                required_distance INT,
                image_url         VARCHAR(500)
            )
        """)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS user_character (
                user_id      VARCHAR(100) NOT NULL,
                character_id INT          NOT NULL,
                level        INT          NOT NULL DEFAULT 1,
                exp          INT          NOT NULL DEFAULT 0,
                is_main      BOOLEAN      NOT NULL DEFAULT FALSE,
                acquired_at  DATETIME     NOT NULL,
                PRIMARY KEY (user_id, character_id),
                FOREIGN KEY (character_id) REFERENCES `character` (character_id)
            )
        """)
        # 커뮤니티 피드의 러닝 기록에 단 반응 (한 사람이 같은 이모지는 한 번만)
        # 이모지는 휴대폰 전체 이모지라서 utf8mb4, 이모지끼리 구분되도록 _bin 정렬 규칙
        # (기본 규칙에서는 서로 다른 이모지가 같은 값으로 취급될 수 있음)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS run_reaction (
                running_number INT          NOT NULL,
                user_id        VARCHAR(100) NOT NULL,
                emoji          VARCHAR(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
                created_at     DATETIME     NOT NULL,
                PRIMARY KEY (running_number, user_id, emoji),
                FOREIGN KEY (running_number) REFERENCES running_record (running_number)
            )
        """)
        # 응원하기 (한 사람이 한 기록에 한 번만)
        cur.execute("""
            CREATE TABLE IF NOT EXISTS run_cheer (
                running_number INT          NOT NULL,
                user_id        VARCHAR(100) NOT NULL,
                created_at     DATETIME     NOT NULL,
                PRIMARY KEY (running_number, user_id),
                FOREIGN KEY (running_number) REFERENCES running_record (running_number)
            )
        """)
        # 캐릭터 정보를 고치면 서버 재시작 때 반영되도록 UPDATE
        # (REPLACE는 행을 지웠다 다시 넣어서 user_character 외래 키와 부딪힘)
        cur.executemany(
            "INSERT INTO `character` "
            "(character_id, name, emoji, category, `condition`, hint, description, required_distance) "
            "VALUES (%s, %s, %s, %s, %s, %s, %s, %s) "
            "ON DUPLICATE KEY UPDATE name = VALUES(name), emoji = VALUES(emoji), "
            "category = VALUES(category), `condition` = VALUES(`condition`), hint = VALUES(hint), "
            "description = VALUES(description), required_distance = VALUES(required_distance)",
            CHARACTERS,
        )

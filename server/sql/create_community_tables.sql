
-- =====================================================
-- RUNCATCH Community Grouping Tables
-- 커뮤니티 그룹핑 테이블 생성
-- =====================================================

-- 1. 그룹핑 실행 이력 테이블
CREATE TABLE IF NOT EXISTS community_grouping_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    group_month DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME NOT NULL,
    finished_at DATETIME NULL,
    target_count INT NOT NULL,
    excluded_count INT NOT NULL,
    group_count INT NOT NULL,
    execution_type VARCHAR(20) NOT NULL,
    error_message TEXT NULL,

    PRIMARY KEY (history_id),

    CONSTRAINT chk_history_status
        CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),

    CONSTRAINT chk_execution_type
        CHECK (execution_type IN ('AUTO', 'ADMIN')),

    CONSTRAINT chk_history_counts
        CHECK (
            target_count >= 0
            AND excluded_count >= 0
            AND group_count >= 0
        )
) ENGINE=InnoDB;


-- 2. 그룹 정보 테이블
CREATE TABLE IF NOT EXISTS community_group (
    group_id BIGINT NOT NULL AUTO_INCREMENT,
    history_id BIGINT NOT NULL,
    group_month DATE NOT NULL,
    group_number INT NOT NULL,
    member_count INT NOT NULL,
    created_at DATETIME NOT NULL,

    PRIMARY KEY (group_id),

    CONSTRAINT uq_group_month_number
        UNIQUE (group_month, group_number),

    CONSTRAINT uq_group_id_month
        UNIQUE (group_id, group_month),

    CONSTRAINT fk_group_history
        FOREIGN KEY (history_id)
        REFERENCES community_grouping_history (history_id),

    CONSTRAINT chk_group_number
        CHECK (group_number > 0),

    CONSTRAINT chk_member_count
        CHECK (member_count BETWEEN 1 AND 20)
) ENGINE=InnoDB;


-- 3. 그룹별 사용자 배정 테이블
CREATE TABLE IF NOT EXISTS community_group_member (
    group_id BIGINT NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    group_month DATE NOT NULL,
    assigned_at DATETIME NOT NULL,

    PRIMARY KEY (group_id, user_id),

    CONSTRAINT uq_user_group_month
        UNIQUE (user_id, group_month),

    CONSTRAINT fk_member_group_month
        FOREIGN KEY (group_id, group_month)
        REFERENCES community_group (group_id, group_month)
) ENGINE=InnoDB;

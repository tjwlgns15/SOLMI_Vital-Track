-- V1: 현재 엔티티 매핑 기준 스키마 베이스라인.
--
-- 지금까지는 Hibernate의 ddl-auto: update가 엔티티를 보고 스키마를 알아서 맞춰왔다.
-- 이 파일은 "그 결과 지금 어떤 스키마여야 하는가"를 코드(엔티티)가 아니라 SQL로 명시적으로
-- 고정해두는 첫 번째 지점이다. 이미 ddl-auto로 만들어져 있는 기존 개발 DB에는
-- (baseline-on-migrate 설정 덕분에) 이 파일이 실제로 재실행되지 않고 "적용된 것으로" 표시만
-- 되며, 완전히 새로운 DB를 만들 때만 이 파일이 실제로 실행되어 테이블을 만든다.
-- 이후의 스키마 변경은 전부 V2, V3 ... 형태의 새 마이그레이션 파일로 추가한다 (이 파일은 고치지 않는다).

CREATE TABLE members (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    login_id   VARCHAR(50)  NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    role       VARCHAR(20)  NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_login_id (login_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE subject (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    owner_id   BIGINT      NOT NULL,
    name       VARCHAR(50) NOT NULL,
    type       VARCHAR(20) NOT NULL,
    species    VARCHAR(50),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_subject_owner FOREIGN KEY (owner_id) REFERENCES members (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE refresh_token (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked    BIT(1)      NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_token_hash (token_hash),
    CONSTRAINT fk_refresh_token_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE measurement_session (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    subject_id        BIGINT      NOT NULL,
    status            VARCHAR(20) NOT NULL,
    -- status가 ACTIVE일 때만 subject_id 값을 갖는 생성 컬럼. 그 위의 유니크 인덱스로
    -- "같은 대상에 ACTIVE 세션이 동시에 둘 이상 존재할 수 없다"를 DB 레벨에서 강제한다.
    active_subject_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN subject_id ELSE NULL END) STORED,
    started_at        DATETIME(3) NOT NULL,
    ended_at          DATETIME(3),
    end_reason        VARCHAR(20),
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_measurement_session_active_subject (active_subject_id),
    CONSTRAINT fk_measurement_session_subject FOREIGN KEY (subject_id) REFERENCES subject (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE location_record (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    session_id  BIGINT      NOT NULL,
    latitude    DOUBLE      NOT NULL,
    longitude   DOUBLE      NOT NULL,
    measured_at DATETIME(3) NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_location_record_session FOREIGN KEY (session_id) REFERENCES measurement_session (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE ecg_sample_record (
    id                BIGINT   NOT NULL AUTO_INCREMENT,
    session_id        BIGINT   NOT NULL,
    sampling_rate_hz  INT      NOT NULL,
    samples_csv       LONGTEXT NOT NULL,
    measured_at       DATETIME(3) NOT NULL,
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_ecg_sample_record_session FOREIGN KEY (session_id) REFERENCES measurement_session (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE acceleration_record (
    id                BIGINT   NOT NULL AUTO_INCREMENT,
    session_id        BIGINT   NOT NULL,
    sampling_rate_hz  INT      NOT NULL,
    samples_csv       LONGTEXT NOT NULL,
    measured_at       DATETIME(3) NOT NULL,
    created_at        DATETIME(6),
    updated_at        DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_acceleration_record_session FOREIGN KEY (session_id) REFERENCES measurement_session (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE velocity_record (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    session_id  BIGINT      NOT NULL,
    speed       DOUBLE      NOT NULL,
    measured_at DATETIME(3) NOT NULL,
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_velocity_record_session FOREIGN KEY (session_id) REFERENCES measurement_session (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

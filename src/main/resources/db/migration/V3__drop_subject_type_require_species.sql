-- V3: 측정 대상을 동물 전용으로 바꾼다.
--
-- 프로젝트 목적이 동물 모니터링으로 확정되어 사람/동물 구분(type) 컬럼을 제거하고,
-- 대신 품종(species)을 필수값으로 만든다 (대시보드 뱃지 등에서 종류 대신 품종을 보여준다).
-- 기존에 품종 없이 등록된 대상은 NOT NULL 제약을 걸기 전에 '미상'으로 채워둔다.

UPDATE subject
SET species = '미상'
WHERE species IS NULL
   OR TRIM(species) = '';

ALTER TABLE subject
    MODIFY COLUMN species VARCHAR(50) NOT NULL,
    DROP COLUMN type;

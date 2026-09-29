-- V4: ECG 배치에 기기가 보낸 심박수(bpm)를 함께 저장한다.
--
-- 지금까지는 심박수를 받지 않고 화면이 파형의 R파 간격으로 직접 추정했다. 이제 EcgSampleMessage에
-- heartRate가 추가되어 그 값을 그대로 저장하고 보여준다. 이 컬럼이 생기기 전의 기록과 심박수를
-- 보내지 않는 기기의 기록은 값이 없으므로 NULL을 허용한다.

ALTER TABLE ecg_sample_record
    ADD COLUMN heart_rate INT NULL AFTER sampling_rate_hz;

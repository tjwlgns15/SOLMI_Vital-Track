-- V2: 회원이 마지막으로 선택한 화면 언어를 저장한다.
--
-- 브라우저 쿠키(LOCALE)만으로는 실시간(WebSocket) 알림처럼 HTTP 요청 스레드 밖에서 만들어지는
-- 메시지의 언어를 판단할 수 없다 (쿠키는 그 브라우저에서만 접근 가능하고, WebSocket 브로드캐스트는
-- 특정 HTTP 요청에 묶여 있지 않다). 그래서 회원이 언어 전환 드롭다운으로 언어를 바꿀 때마다
-- 이 컬럼에도 함께 남겨두고, 실시간 알림을 만들 때는 이 값을 조회해서 쓴다.
-- 아직 한 번도 언어를 바꾼 적 없는 회원은 NULL이며, 이 경우 기본값(한국어)으로 취급한다.

ALTER TABLE members
    ADD COLUMN preferred_language VARCHAR(10) NULL AFTER role;

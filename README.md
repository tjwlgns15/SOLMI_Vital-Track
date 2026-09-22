# VitalTrack

측정 대상(사람 또는 동물)의 위치와 생체/움직임 신호(심전도, 가속도, 속도)를 실시간으로 웹에서 모니터링하는 Spring Boot + JPA 프로젝트입니다.

## 이번 산출물의 범위 (MVP)

- 계정 생성/로그인
- 측정 대상 등록(사람/동물)
- **웹 기반 시뮬레이터**로 실제 웨어러블 디바이스 + 스마트폰 앱 역할을 대신함 (측정 대상 선택 → 측정 시작 → 가짜 GPS/ECG/가속도/속도 데이터를 주기적으로 생성해 서버로 전송)
- 여러 측정 대상을 동시에 볼 수 있는 실시간 모니터링 대시보드 (좌: 지도, 우: 대상별 신호 패널)
- **측정 이력 저장 및 재생**: 세션이 진행되는 동안 수신한 위치/ECG/가속도/속도를 모두 DB에 기록하고, 종료된 세션을 목록에서 골라 배속(1~8배)·탐색(seek)까지 가능한 형태로 재생

다음 항목은 이번 범위에서 **의도적으로 제외**했습니다 (사용자와 협의된 MVP 범위):

- 이상 신호/임계값 알림
- 실제 네이티브 모바일 앱 (Android/iOS)
- 실제 BLE 웨어러블 디바이스 연동

## 기술 스택

- Java 17, Spring Boot 3.3.4
- Spring Data JPA + MySQL (`com.mysql:mysql-connector-j`)
- Flyway (`flyway-core`, `flyway-mysql`) — DB 스키마 버전 관리 (`ddl-auto: validate`와 함께 사용, `db/migration`의 마이그레이션 스크립트가 스키마 변경의 유일한 경로)
- Spring Security (웹: 폼 로그인 + 세션, 앱: JWT — 아래 "인증 방식" 참고)
- JWT (`io.jsonwebtoken:jjwt`) — 네이티브 앱 등 세션 쿠키를 쓸 수 없는 클라이언트용 토큰 인증
- Spring WebSocket (STOMP + SockJS) — 실시간 위치/신호 전송
- Thymeleaf + 순수 JS (Leaflet.js로 지도, Canvas로 ECG 파형)
- Lombok (getter 자동 생성, setter는 사용하지 않고 도메인 메서드로만 상태 변경)

## 실행 방법

사전 준비:
- JDK 17 이상
- 로컬에 MySQL 서버가 떠 있어야 합니다 (`src/main/resources/application.yml` 기준 `localhost:3308`, 계정 `root/1234`). 포트가 기본값(3306)이 아니므로 본인 MySQL 설정과 다르면 `application.yml`의 `spring.datasource` 값을 맞춰주세요.
- 데이터베이스(`vital_track`)는 JDBC URL에 `createDatabaseIfNotExist=true`가 붙어 있어 최초 접속 시 자동 생성됩니다 (root 계정에 DB 생성 권한이 있어야 함). 안 되면 미리 `CREATE DATABASE vital_track;`을 실행해두세요.
- 스키마는 Flyway가 관리합니다 (`src/main/resources/db/migration/V1__init_schema.sql`). 처음 실행할 때 이 마이그레이션이 적용되어 테이블이 만들어지며, `spring.jpa.hibernate.ddl-auto: validate`는 엔티티 매핑이 실제 스키마와 일치하는지 검증만 하고 스키마를 직접 바꾸지는 않습니다. (이미 `ddl-auto: update`로 만들어져 있던 기존 DB라면 `spring.flyway.baseline-on-migrate: true` 덕분에 V1이 재실행되지 않고 "이미 적용된 것"으로 기준선만 찍힙니다.)

```bash
# 프로젝트 루트에서
./gradlew bootRun      # (Windows: gradlew.bat bootRun)
```

기동 후 브라우저에서 http://localhost:8080 접속 (자동으로 /dashboard 로 리다이렉트되며, 로그인 안 되어 있으면 /login 으로 이동합니다).

## 동작 확인 순서 (End-to-End 시나리오)

1. `/signup` 에서 계정 생성 후 로그인
2. `/subjects` 에서 측정 대상 등록 (예: "홍길동" - 사람, "초코" - 동물/강아지)
3. 브라우저 탭을 하나 더 열어 같은 계정으로 로그인 후 `/simulator` 접속
4. 시뮬레이터에서 대상을 선택하고 "측정 시작" 클릭 → 가짜 위치/ECG/가속도/속도 데이터가 주기적으로 전송 시작
5. 원래 탭에서 `/dashboard` 접속 → 좌측 지도에 해당 대상의 위치 마커가 나타나고, 우측 카드에 실시간 ECG 파형과 가속도/속도가 갱신되는지 확인
6. 시뮬레이터에서 "측정 종료" 클릭 → 일정 시간(6초) 후 대시보드 카드가 "오프라인" 상태로 바뀌는지 확인
7. 여러 측정 대상을 동시에 등록하고 각각 시뮬레이터를 시작하면, 대시보드에서 여러 대상이 동시에 모니터링되는지 확인
8. `/history` 접속 → 방금 종료한 세션이 목록에 보이는지 확인 후 "재생" 클릭 → 지도에 전체 이동 경로(선)가 그려지고, ▶ 재생을 누르면 마커가 경로를 따라 움직이며 ECG/가속도/속도가 시간 순서대로 재생되는지 확인 (배속 변경, 탐색바 드래그도 테스트)

## 인증 방식

두 가지 인증 수단이 하나의 Spring Security 필터 체인 안에서 공존합니다. 어느 쪽으로 인증했든 이후 컨트롤러는 동일하게 `@AuthenticationPrincipal MemberPrincipal`을 사용하므로, 기존 API 컨트롤러들은 인증 수단을 신경 쓸 필요가 없습니다.

1. **웹 브라우저**: 기존과 동일하게 폼 로그인(`POST /login`) → 세션 쿠키 + CSRF. `/dashboard`, `/subjects`, `/simulator` 등 화면이 이 방식을 그대로 씁니다.
2. **네이티브 앱(및 세션 쿠키를 쓸 수 없는 클라이언트)**: JWT 기반.
   - `POST /api/auth/login` (`{loginId, password}`) → `{accessToken, refreshToken}` 발급
   - 이후 모든 API 호출에 `Authorization: Bearer <accessToken>` 헤더를 실어 보냅니다.
   - accessToken은 짧게(기본 30분) 만료되며, 만료되면 `POST /api/auth/refresh` (`{refreshToken}`)로 새 토큰 쌍을 재발급받습니다 (재발급 시 기존 refreshToken은 즉시 폐기되는 1회용 로테이션 방식).
   - `POST /api/auth/logout` (`{refreshToken}`)으로 해당 refreshToken을 서버에서 즉시 폐기할 수 있습니다.
   - refreshToken은 원문이 아니라 SHA-256 해시로 DB(`refresh_token` 테이블)에 저장되어, DB가 유출되어도 토큰 자체를 복원할 수 없습니다.
   - `/api/**`로 오는 미인증 요청은 (앱이 JSON을 기대하므로) `/login`으로 리다이렉트하지 않고 401만 응답합니다.

### 실시간(WebSocket/STOMP) 채널 인증

`/ws` 핸드셰이크(HTTP 요청) 자체는 `SecurityConfig`에서 `permitAll`로 열려 있습니다. 순수 WebSocket 클라이언트는 핸드셰이크 요청에 커스텀 헤더를 못 붙이는 경우가 많아, JWT를 HTTP 헤더가 아니라 **STOMP `CONNECT` 프레임 자체의 헤더**로 실어 보내는 방식을 택했기 때문입니다. 실제 인증은 `StompAuthChannelInterceptor`(`configureClientInboundChannel`로 등록됨)가 `CONNECT` 프레임을 가로채 처리합니다.

- **웹 브라우저**: `/ws` 핸드셰이크 시점에 이미 세션 쿠키로 인증되어 있으므로, 그 `Principal`이 STOMP 세션에도 그대로 이어집니다(인터셉터는 손대지 않고 통과시킴).
- **네이티브 앱**: STOMP 클라이언트 라이브러리의 `connect()` 호출 시 넘기는 헤더 맵에 `Authorization: Bearer <accessToken>`을 넣어 보내면(예: `stomp.js`의 `client.connectHeaders = { Authorization: 'Bearer ' + accessToken }`), 인터셉터가 이를 검증해 `MemberPrincipal`을 세팅합니다.
- 둘 다 아니면(세션도 없고 유효한 토큰도 없으면) 인터셉터가 `MessagingException`을 던져 STOMP `ERROR` 프레임과 함께 연결이 거부됩니다.
- 이 인증은 최초 `CONNECT` 시점에 한 번만 이루어지고, 이후 같은 커넥션 위의 모든 메시지는 그때 확인된 사용자로 취급됩니다(요청마다 다시 검증하는 REST/HTTP와 다른 점).

## 프로젝트 구조 (패키지 by 기능)

```
com.solmi.vitaltrack
├── common            공통 (BaseTimeEntity - 생성/수정시각 Auditing)
├── member            계정 도메인 (Member, MemberPrincipal, Spring Security 연동)
├── auth              앱용 JWT 인증 (AuthApiController, JwtTokenProvider, RefreshToken, JwtAuthenticationFilter, StompAuthChannelInterceptor)
├── subject           측정 대상 등록/조회 (Subject, SubjectType)
├── measurement        측정 세션 생명주기 (MeasurementSession)
│   ├── realtime       실시간 데이터 수신/중계 (WebSocket 메시지, STOMP 컨트롤러, MeasurementIngestService)
│   └── history        측정 이력 저장/조회 (LocationRecord/EcgSampleRecord/AccelerationRecord/VelocityRecord, 재생용 API)
├── web                화면 컨트롤러 (대시보드, 시뮬레이터, 이력/재생)
└── config             SecurityConfig, WebSocketConfig
```

측정 데이터가 들어오는 흐름은 다음과 같습니다: 시뮬레이터 → WebSocket → `RealtimeRelayController`(프로토콜 어댑터) → `MeasurementIngestService`(활성 세션 검증) → `RealtimeBroadcastService`(대시보드로 실시간 중계) + `MeasurementHistoryService`(DB 저장, 재생용). 중계와 저장을 분리해뒀기 때문에, 나중에 저장 방식만(예: 시계열 DB로 교체) 바꾸고 싶을 때 `MeasurementHistoryService`만 건드리면 됩니다.

### 측정 세션 중복/방치 방지

같은 측정 대상에 ACTIVE 세션이 두 개 이상 생기지 않도록 두 단계로 막습니다.

1. **동시 시작 방지 (DB 유니크 제약)**: `MeasurementSessionService.start()`의 "조회 후 저장" 로직만으로는 두 요청이 거의 동시에 들어올 때(check-then-act 레이스 컨디션) 막을 수 없으므로, `MeasurementSession`에 MySQL 생성 컬럼(`active_subject_id`, `status`가 `ACTIVE`일 때만 `subject_id` 값을 가짐)을 두고 그 위에 유니크 제약을 걸어 DB 레벨에서 최종적으로 중복을 차단합니다. 레이스가 실제로 발생하면 나중 요청은 `DataIntegrityViolationException`을 받고, 서비스가 이를 잡아 기존과 동일한 `"이미 진행 중인 측정 세션이 있습니다"` 오류로 변환합니다.
2. **방치된 세션 자동 정리 (타임아웃)**: 앱이 크래시 등으로 `/end`를 호출하지 못하면 세션이 영원히 `ACTIVE`로 남아 해당 대상의 새 측정을 계속 막게 됩니다. "마지막으로 데이터를 받은 시각"은 `MeasurementSession` 엔티티가 아니라 `SessionActivityTracker`(인메모리 `ConcurrentHashMap`)가 전담해서 추적합니다 — 매 메시지마다 DB row를 UPDATE하면 여러 WebSocket 스레드가 같은 row를 동시에 갱신하게 되어 락 경합·데드락을 유발하기 때문입니다. `StaleMeasurementSessionScheduler`가 30초마다 `MeasurementSessionService.endStaleSessions()`를 호출해, `SessionActivityTracker`에 기록된 마지막 활동 시각(없으면 세션 시작 시각) 기준으로 90초 이상 데이터가 없는 `ACTIVE` 세션을 `endReason: TIMEOUT`으로 자동 종료합니다. 앱은 재시작 시 `GET /api/sessions/active`로 자신이 추적하던 대상에 아직 활성 세션이 남아있는지 확인해, 남아있다면 새로 시작하지 않고 그 세션에 데이터 전송을 이어서 재개하는 것을 권장합니다 (API 명세서 "측정 세션 API" 섹션 참고).

시뮬레이터는 실제 스마트폰 앱과 동일하게 동작하도록, 측정 대상 목록을 서버가 화면(HTML)에 미리 심어주지 않고 `GET /api/subjects`(`SubjectApiController`)를 클라이언트에서 호출해 JSON으로 받아온다. `/simulator` 페이지 자체가 이미 로그인을 요구하므로(Spring Security), 이 API 호출 시점에는 항상 인증된 상태가 보장된다.

설계 원칙:
- 엔티티는 `setter`를 두지 않고, 상태 변경이 필요한 지점마다 의미가 분명한 도메인 메서드(`Subject.register(...)`, `MeasurementSession.start()/end()`)로만 변경합니다.
- 반복적인 getter/생성자/시각 관리는 Lombok(`@Getter`, `@RequiredArgsConstructor`)과 Spring Data JPA Auditing 어노테이션으로 처리합니다.
- 패키지를 계층(controller/service/repository)이 아니라 **기능 단위**로 나누고, 각 서비스는 하나의 책임만 지도록 했습니다 (예: `RealtimeBroadcastService`는 실시간 중계만, `MeasurementHistoryService`는 이력 저장/조회만, `MeasurementIngestService`는 둘을 조율하는 것만).
- `auth` 패키지도 동일한 원칙으로 쪼갰습니다: `JwtTokenProvider`는 액세스 토큰(JWT) 발급/검증만, `RefreshTokenService`는 리프레시 토큰 발급/검증/폐기만, `JwtAuthenticationFilter`는 HTTP 요청 헤더를 읽어 `SecurityContext`를 채우는 것만, `StompAuthChannelInterceptor`는 STOMP `CONNECT` 프레임을 읽어 인증하는 것만, `AuthService`는 로그인/재발급/로그아웃 흐름을 조율하는 것만 담당합니다. `JwtAuthenticationFilter`와 `StompAuthChannelInterceptor`는 "토큰에서 인증 정보를 꺼내 Principal을 만든다"는 로직은 같지만, 전송 계층(HTTP 요청 vs STOMP 프레임)이 다르기 때문에 억지로 하나로 합치지 않고 각자의 진입점에 맞춰 분리했습니다.
- `EcgSampleRecord`는 파형 샘플을 별도 조인 테이블 없이 콤마 구분 문자열로 저장하지만, 외부에는 `getSamples()`로 `List<Double>`만 노출해서 저장 형식이 엔티티 내부에만 감춰지도록(캡슐화) 했습니다.

## 다음 단계로 확장한다면

- 이상 신호(부정맥 등) 감지 및 알림
- 실제 BLE 웨어러블 연동 및 네이티브 앱 개발 (REST API·실시간 채널 인증 모두 JWT로 준비되어 있음 — 위 "인증 방식" 참고)
- 다중 사용자/조직 단위 권한 분리 (현재는 계정별 소유 모델만 존재)

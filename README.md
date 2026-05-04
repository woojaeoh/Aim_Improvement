# AIM (Analyst Information Management)

증권 애널리스트의 신뢰도를 평가하고 투자 인사이트를 제공하는 종합 정보 관리 시스템

## 프로젝트 개요

AIM은 증권 애널리스트들이 발행하는 리포트를 체계적으로 수집·분석하여, **AIMS Score**라는 독자적인 신뢰도 지표를 통해 애널리스트를 평가하는 백엔드 시스템입니다.

투자자들에게 다음과 같은 가치를 제공합니다:
- 애널리스트의 과거 예측 정확도 기반 신뢰도 평가
- 종목별 목표가 통계 및 추이 분석
- AI 기반 리포트 숨겨진 의견(Hidden Opinion) 분석
- 섹터별 투자 전망 및 트렌드 파악

## 주요 기능

### 1. 애널리스트 평가 시스템
- **AIMS Score**: 목표가 예측 정확도, 의견 적중률 등을 종합한 신뢰도 점수
- **애널리스트 랭킹**: AIMS Score 기준 순위 제공
- **트렌딩 애널리스트**: 최근 7일 검색량 기반 인기 애널리스트

### 2. 종목 분석
- **목표가 통계**: 종목별 애널리스트 목표가 최대/평균/최소
- **상승여력 분석**: 현재가 대비 목표가 기반 상승 가능성 계산
- **컨센서스 의견**: 종목에 대한 애널리스트들의 종합 투자 의견
- **종가 변동 추이**: 시계열 주가 데이터 시각화

### 3. 리포트 분석
- **Surface Opinion**: 리포트에 명시된 공개 의견 (BUY/HOLD/SELL)
- **Hidden Opinion**: AI 모델 기반 숨겨진 의견 분석 (0.0 ~ 1.0 확률)
- **의견 변화 추적**: 애널리스트별 리포트 이력 및 의견 변경 분석

### 4. 섹터 분석
- **섹터별 매수 비율**: 각 섹터에 대한 BUY 의견 비율
- **섹터 랭킹**: 매수 비율 기준 투자 유망 섹터 제공

### 5. 통합 대시보드
- TOP 3 신뢰도 애널리스트 (AIMS Score 기준)
- TOP 3 상승여력 종목 (목표가 기반)
- TOP 3 매수 섹터 (매수 의견 비율 기준)
- 실시간 검색 트렌드

## 기술 스택

### Frontend
- **React.JS** - 최신 리액트 버전

### AI
- **Pytorch** - 파이썬 학습 모델
- **FinBERT** - 금융사전 학습 모델 및 분류기 모델

### Backend
- **Java 21** - 최신 LTS 버전
- **Spring Boot 3.5.6** - 프레임워크
- **Spring Data JPA** - ORM 및 데이터 접근
- **Hibernate** - JPA 구현체
- **Spring Retry** - 낙관적 락 재시도 처리

### Database & Cache
- **MySQL 8** - 관계형 데이터베이스
- **Redis 7** - 공유 캐시 및 분산락
- **P6Spy** - SQL 쿼리 모니터링

### Infra
- **Docker Compose** - 다중 인스턴스 컨테이너 오케스트레이션
- **Nginx** - 로드밸런서 (Round-Robin)

### API & Documentation
- **RESTful API** - 표준 REST API 설계
- **SpringDoc OpenAPI (Swagger) 2.6.0** - API 문서 자동화

### Data Processing
- **OpenCSV 5.9** - CSV 데이터 파싱 및 임포트

### Development Tools
- **Lombok** - 보일러플레이트 코드 자동 생성
- **Gradle** - 빌드 및 의존성 관리

## 시스템 아키텍처

```
┌─────────────────┐
│   Frontend      │
│  (React.js)     │
└────────┬────────┘
         │ REST API
┌────────▼────────┐
│     Nginx       │  ← 로드밸런서 (Round-Robin)
│   (port 80)     │
└────┬──────┬─────┘
     │      │
┌────▼──┐ ┌─▼─────┐
│ App1  │ │ App2  │  ← Spring Boot 다중 인스턴스
└────┬──┘ └──┬────┘
     │       │
     └───┬───┘
         │
┌────────▼────────┐     ┌─────────────┐
│    MySQL 8      │     │   Redis 7   │
│  (영구 데이터)   │     │  (캐시/락)   │
└─────────────────┘     └─────────────┘
```

## 설치 및 실행

### 방법 1: Docker Compose (권장)

```bash
# 전체 환경 실행 (nginx + app x2 + redis + mysql)
docker-compose up --build
```

- API: http://localhost/swagger-ui/index.html
- 로드밸런서(nginx)가 app1/app2에 자동 분산

### 방법 2: 로컬 실행

**사전 요구사항**
- Java 21 이상
- MySQL 8.0 이상
- Redis 7.0 이상

```sql
CREATE DATABASE aim CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
./gradlew bootRun
```

- API: http://localhost:8080/swagger-ui/index.html

## 🔥 기술적 도전과제

### 1. N+1 문제 → 벌크 조회 최적화

#### P (Problem)
애널리스트 랭킹 조회 시 애널리스트 수만큼 쿼리가 추가 발생하는 N+1 문제.
200명 애널리스트 조회 시 201개 쿼리 실행.

#### A (Action)
섹터별 평균 메트릭을 사전에 HashMap으로 일괄 계산하여 in-memory에 올려두고,
각 애널리스트 계산 시 DB 조회 없이 HashMap lookup으로 대체.

```java
// 섹터 평균 1회 bulk 조회
Map<String, SectorAverageMetrics> sectorAverages = calculateSectorAverageMetrics();

// 이후 각 애널리스트 계산에서 DB 조회 없이 Map lookup
SectorAverageMetrics avg = sectorAverages.get(eval.sector);
```

#### R (Result)
- 쿼리 수: 201개 → 1개 (섹터 평균 사전 계산)
- 단일 인스턴스 환경에서 유효한 해결책

**트레이드오프**: 인스턴스가 여러 개로 늘어나면 각 인스턴스가 독립적인 HashMap을 가지게 되어 데이터 불일치 가능성 → Redis 공유 캐시로 해결

---

### 2. 단일 인스턴스 한계 → Redis 공유 캐시 도입

#### P (Problem)
스케일아웃(다중 인스턴스) 환경에서 각 인스턴스의 로컬 메모리가 독립적으로 존재.
app1과 app2가 서로 다른 캐시 상태를 가져 데이터 불일치 발생 가능.

#### A (Action)
Redis를 인스턴스 간 공유 캐시(Single Source of Truth)로 도입.
`@Cacheable` / `@CacheEvict`로 캐시 읽기/무효화를 선언적으로 처리.

```java
@Cacheable(value = "analystRanking", key = "#sortBy")
public AnalystRankingResponseDTO getRankedAnalysts(String sortBy) { ... }

@CacheEvict(value = "analystRanking", allEntries = true)
public int calculateAllAnalystMetricsWithCache() { ... }
```

#### R (Result)
JMeter 부하 테스트 (100 threads, ramp-up 10s, loop 5회):

| 지표 | 캐시 적용 전 | 캐시 적용 후 |
|------|------------|------------|
| 평균 응답시간 | 195ms | 4ms |
| 최소 응답시간 | 28ms | 1ms |
| 최대 응답시간 | 3,681ms | 15ms |
| Error % | 0% | 0% |
| Throughput | 31.5/sec | 34.4/sec |

**응답시간 약 98% 감소**

---

### 3. 동시성 제어 문제 → 낙관적 락 적용

#### P (Problem): 동시 요청 시 데이터 유실 발생

애널리스트 메트릭 계산 시 여러 스레드가 동시에 실행되면 마지막 스레드의 결과만 반영되는 **Lost Update** 현상 발생.

**재현 테스트:**
```java
@Test
void testLostUpdateWithoutLock() throws InterruptedException {
    // 100개 스레드가 동시에 같은 애널리스트의 메트릭 업데이트
    ExecutorService executor = Executors.newFixedThreadPool(100);
    for (int i = 0; i < 100; i++) {
        executor.submit(() -> {
            analystMetricsService.calculateAndSaveAccuracyRate(analystId);
        });
    }
    // 결과: 100번 업데이트 예상 → 실제 67번만 반영 (33% Lost Update!)
}
```

**측정 결과:**
- 예상 업데이트 횟수: 100회
- 실제 반영 횟수: 67회
- **Lost Update 발생: 33건 (33.0%)**

#### A (Action)
엔티티에 `@Version` 필드를 추가해 낙관적 락 적용.
충돌 감지 시 `@Retryable`로 최대 100회 재시도 (10ms backoff).

```java
@Version
private Long version;

@Retryable(
    retryFor = ObjectOptimisticLockingFailureException.class,
    maxAttempts = 100,
    backoff = @Backoff(delay = 10)
)
public void calculateAndSaveAccuracyRate(Long analystId) { ... }
```

#### R (Result)
- Lost Update 0건 (100% 데이터 정합성 보장)
- 행(Row) 레벨 잠금으로 다른 애널리스트 계산에 영향 없음

---

### 4. 스케일아웃 환경 → Redis 분산락으로 배치 중복 실행 방지

#### P (Problem)
다중 인스턴스 환경에서 `POST /admin/metrics/calculate` 동시 호출 시
app1과 app2가 동시에 전체 메트릭 재계산을 실행하여 DB 부하 및 데이터 불일치 발생.

낙관적 락은 **행(Row) 레벨** 충돌을 처리하지만,
배치 작업의 **중복 실행 자체**를 막지는 못함.

#### A (Action)
Redis `SET NX EX`로 분산락 구현. Lua 스크립트로 원자적 해제 보장.

```java
// 락 획득: SET lock:key uniqueValue NX EX 600
public boolean tryAcquire(String lockKey, String lockValue, Duration ttl) {
    return Boolean.TRUE.equals(
        redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, ttl)
    );
}

// 락 해제: 내 락인지 확인 후 삭제 (Lua 스크립트, 원자적)
private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
    Long.class
);
```

UUID로 인스턴스별 고유 락 값 생성 → 다른 인스턴스의 락을 실수로 해제하는 상황 방지.

#### R (Result)
JMeter 동시성 테스트 (10 threads, ramp-up 0s):

| 응답 | 건수 | 의미 |
|------|------|------|
| 200 OK | 1건 | 락 획득 성공, 계산 실행 |
| 409 Conflict | 9건 | 락 획득 실패, 즉시 차단 |

**Docker Compose 스케일아웃 환경 전체 부하 테스트** (100 threads, ramp-up 10s, loop 5회):

| 지표 | 수치 |
|------|------|
| 평균 응답시간 | 5ms |
| Error % | 0% |
| Throughput | 50.4/sec |
| p90 응답시간 | 9ms |
| p95 응답시간 | 11ms |
| app1 처리 요청 | 2,502건 |
| app2 처리 요청 | 2,496건 |

nginx Round-Robin으로 app1/app2 약 50:50 분산 확인.

---

### 테스트 환경
- **Java 21**
- **Spring Boot 3.5.6**
- **JUnit 5**
- **JMeter 5.x** - 부하 테스트
- **동시성 테스트**: ExecutorService + CountDownLatch

---

## 팀원

| 이름 | 역할 | 담당 업무 |
|------|------|-----------|
| **오재우** | Backend | 백엔드 시스템 설계 및 구현, REST API 개발 |
| **배성빈** | AI | Hidden Opinion 분석 모델 개발, AIMS Score 알고리즘 설계 |
| **최현승** | Frontend | 사용자 인터페이스 개발, API 연동 |

## 프로젝트 정보

- **프로젝트 기간**: 2025년 9월 ~ 2025년 12월
- **프로젝트 유형**: Capstone Design Project (Aim)
- **개발 환경**: React.js, Pytorch, Spring Boot 3.x, Java 21, MySQL 8, Redis 7

---

**2025 Capstone Design Team- A.I.M**

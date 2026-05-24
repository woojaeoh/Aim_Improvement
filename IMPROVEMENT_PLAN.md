# AIM 프로젝트 개선 계획

> 목표: 하반기 취업을 위한 기술 깊이 강화 및 자소서 스토리 재설계

---

## 1. 서류 탈락 원인 진단

### 문제 1 — 스토리가 "기술 나열"이지 "설계 사고"가 아님

6개 회사 자소서 전부에서 같은 구조가 반복됨:

> N+1 발견 → 쿼리 1040건 → 3건으로 줄임 → 처리 시간 1분 이내

이 패턴은 심사자 입장에서 "트러블슈팅 한 건이 전부인 사람"으로 읽힌다.
문제는 건수가 아니라 **그 판단의 이유와 깊이가 자소서에 없다**는 것.

### 문제 2 — 프로젝트의 진짜 흥미로운 부분을 안 씀

코드에는 실제로 깊은 설계 판단이 있는데, 자소서에 전혀 드러나지 않음:

| 코드에 존재하는 판단 | 자소서에서 |
|---|---|
| 1년 고정 비교가 아닌 **의견변화 시점 종가** 비교 | 언급 없음 |
| **섹터 상대 수익률** 도입 (불공정한 절대 비교 탈피) | 언급 없음 |
| 백분위 정규화 기반 AIMS Score 설계 | 언급 없음 |
| 리포트 수 기반 **신뢰도 가중치** (3개 미만 패널티) | 언급 없음 |
| Redis 분산락 Lua 스크립트 원자성 보장 | 언급 없음 |

### 문제 3 — 코드에 면접/코드리뷰에서 바로 지적당할 것들이 있음

```java
// 함수 이름은 소수점 2자리인데 실제론 1자리 반올림 (버그)
private double roundToTwoDecimals(double value) {
    return Math.round(value * 10.0) / 10.0;  // 100.0이어야 함
}

// 평가 루프가 4개 메서드에 거의 동일하게 중복
// calculateAndSaveAccuracyRate
// calculateAndSaveAccuracyRateWithCache
// calculateGlobalAverageMetrics
// calculateSectorAverageMetrics

// 운영 코드에 System.out.println (SLF4J 사용해야 함)
System.out.println("📊 모든 애널리스트 지표 일괄 계산 시작...");

// 전체 로드 후 Java에서 필터링 (DB 쿼리로 처리해야 함)
List<AnalystMetrics> metricsList = metricsRepository.findAll().stream()
        .filter(m -> analystIds.contains(m.getAnalyst().getId()))
        .toList();

// AIMS Score 계산에서 N번 단건 save (saveAll 배치로 처리해야 함)
metricsRepository.save(metrics);  // for문 안에서 반복
```

---

## 2. 직무 전략

### 추천: 2개 축에 집중

| 축 | 직무 | AIM 어필 포인트 |
|---|---|---|
| **1순위** | 금융IT (증권·은행·카드사) | 도메인 인사이트 (hidden vs surface opinion) + AIMS Score 알고리즘 설계 |
| **2순위** | 일반 백엔드 (스타트업·중견) | 동시성 제어, 분산락, Redis fallback 시스템 설계 |

### 비추천

- **인프라/DevOps**: Docker Compose + Nginx 수준으로 경쟁 불가
- **데이터 엔지니어링**: Spark/Flink/Kafka 경험 없이 진입 어려움

> 두 축 모두 **같은 프로젝트 개선으로 커버 가능** — 코드 품질 + AIMS Score 스토리 + Spring Batch 하나로 금융IT와 일반 백엔드 양쪽에 쓸 수 있음

---

## 3. 개선 로드맵

### Phase 1 — 코드 품질 수정 (1~2주)

자소서에서 "정밀하게 추적하는 개발자"를 강조하는데, 코드 자체에 버그와 품질 문제가 있으면 신뢰가 깨짐.

- [ ] 평가 루프 4중 중복 → 공통 메서드(`evaluateAllReports`) 추출
- [ ] `roundToTwoDecimals` 버그 수정 (`10.0` → `100.0`)
- [ ] `System.out.println` → SLF4J + `@Slf4j` 로거로 전면 교체
- [ ] `calculateAllAimsScores`의 N번 단건 `save` → `saveAll` 배치 처리
- [ ] `getRankedAnalystsByStock`의 전체 로드 후 Java 필터링 → JPQL 쿼리로 교체
- [ ] `evaluateReportAfterOpinionChange`, `StockAverageMetrics` 등 데드 코드 정리

### Phase 2 — Spring Batch 도입 (3~4주)

#### 현재 구조의 한계

```
HTTP POST /api/admin/recalculate
  → @Transactional 메서드에서 전체 애널리스트 순차 처리
  → 중간 실패 시 전체 재실행
  → 진행 상황 추적 불가
  → 전체 데이터를 메모리에 한번에 로드
```

#### Spring Batch 전환 후

```
Job: AnalystMetricsCalculationJob
  ├── Step 1: SectorAverageStep
  │     ItemReader: 전체 리포트를 청크(chunk) 단위로 읽기
  │     ItemProcessor: 섹터별 평균 계산
  │     ItemWriter: 섹터 평균 캐시에 저장
  │
  ├── Step 2: AnalystMetricsStep (Partitioned)
  │     Partitioner: 애널리스트 ID 범위별로 분할
  │     ItemReader: 파티션별 리포트 청크 읽기
  │     ItemProcessor: 지표 계산 (섹터 평균 참조)
  │     ItemWriter: AnalystMetrics 배치 저장
  │
  └── Step 3: AimsScoreStep
        ItemReader: 전체 AnalystMetrics 조회
        ItemProcessor: 백분위 계산 및 점수 산정
        ItemWriter: saveAll 배치 저장

결과:
  - 실패 시 실패한 파티션만 재시도
  - Job Execution History로 실행 이력 관리
  - 청크 단위 처리로 메모리 안정성 확보
  - Spring Batch Admin UI (선택)로 배치 모니터링
```

#### 왜 금융IT에서 중요한가

증권사 야간 정산 배치, 은행 이자 계산 배치, 카드사 매출 집계 배치가 전부 이 패턴. Spring Batch는 금융IT에서 사실상 표준 기술.

### Phase 3 — 자소서 스토리 재설계 (1주)

#### Before

> "N+1 문제로 쿼리 1,040건이 발생하는 것을 Bulk 조회와 HashMap 그룹핑으로 3건으로 줄이고 처리 시간을 1분 이내로 단축했습니다."

#### After

> "애널리스트 평가의 공정성 문제를 먼저 설계했습니다. 1년 후 고정 비교는 3개월 만에 의견을 바꾼 애널리스트와 1년간 유지한 애널리스트를 동일 기준으로 평가하는 구조적 오류가 있어, 의견변화 시점 종가 비교 방식을 직접 설계했습니다. 섹터 전체가 하락하는 장에서 방어한 애널리스트를 절대 수익률로 평가하면 왜곡이 생긴다는 문제도 섹터 상대 수익률 도입으로 해결했습니다.
>
> 이 배치 로직을 단일 트랜잭션으로 운영하던 중, 중간 실패 시 전체 재실행 문제와 N+1 성능 문제를 동시에 확인했습니다. Spring Batch 청크 기반으로 전환해 파티션별 재시도, 실행 이력 추적, 메모리 안정성을 확보했고 처리 시간을 30분에서 1분 이내로 단축했습니다."

---

## 4. 설계 판단 — 자소서에 써야 할 이유들

Phase 1~2 완료 후, 이 판단들을 명확히 서술할 것.

### 왜 의견변화 시점 종가 비교인가?

```
시나리오: 애널리스트 A가 삼성전자에 2024-01 BUY 의견 발행
          2024-04에 SELL로 의견 변경

1년 고정 비교: 2025-01 종가와 비교 → BUY였던 기간이 3개월뿐인데 1년치로 평가
의견변화 시점: 2024-04 종가와 비교 → BUY를 유지한 실제 기간만 평가 (공정)
```

### 왜 섹터 상대 수익률인가?

```
2022년 금리 인상기: IT 섹터 전체 -30%
  - 애널리스트 A (IT 커버): 삼성전자 -20% → 절대 수익률 낮음
  - 애널리스트 B (에너지 커버): 한국석유 +15% → 절대 수익률 높음

절대 수익률 비교: A가 나쁜 애널리스트처럼 보임 (왜곡)
섹터 상대 비교: A는 IT 평균(-30%) 대비 +10%포인트 → 실제로 우수한 예측
```

### 왜 백분위 정규화인가?

```
원점수 직접 비교의 문제:
  - 정답률: 80% vs 70% → 차이가 크게 보임
  - 수익률: 15% vs 14.9% → 거의 같아 보임

백분위 정규화:
  - 전체 분포에서의 상대 위치로 변환
  - 각 지표가 점수에 미치는 영향을 가중치대로 통제 가능
  - 정답률 40%, 수익률 30%, 상대수익률 15%, 목표가오차율 15%
```

---

## 5. 완료 후 예상 자소서 소재 목록

| 소재 | 깊이 | 지원 직무 |
|---|---|---|
| AIMS Score 알고리즘 설계 (백분위, 섹터 상대, 신뢰도 가중치) | 설계 판단 | 금융IT, 일반 백엔드 |
| 의견변화 시점 종가 비교 로직 | 도메인 + 알고리즘 | 금융IT |
| Spring Batch 파티셔닝 + 청크 처리 | 엔터프라이즈 패턴 | 금융IT |
| 낙관적 락 + Spring Retry 동시성 제어 | 동시성 | 일반 백엔드 |
| Redis 분산락 Lua 스크립트 원자성 | 분산 시스템 | 일반 백엔드, 금융IT |
| Redis fallback (장애 시 DB 직접 조회) | 가용성 설계 | 금융IT, 인프라 |
| N+1 → Bulk + HashMap 배치 최적화 | 성능 | (보조 소재로만) |

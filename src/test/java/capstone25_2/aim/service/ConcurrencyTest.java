package capstone25_2.aim.service;

import capstone25_2.aim.domain.entity.*;
import capstone25_2.aim.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Slf4j
class ConcurrencyTest {

    @Autowired
    private AnalystMetricsService analystMetricsService;

    @Autowired
    private AnalystRepository analystRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private AnalystMetricsRepository metricsRepository;

    private Long testAnalystId;
    private Long testStockId;

    @BeforeEach
    @Transactional
    void setUp() {
        // 기존 데이터 정리
        reportRepository.deleteAll();
        metricsRepository.deleteAll();
        analystRepository.deleteAll();
        stockRepository.deleteAll();

        // 테스트용 애널리스트 생성
        Analyst analyst = new Analyst();
        analyst.setAnalystName("김철수");
        analyst.setFirmName("테스트증권");
        analyst = analystRepository.save(analyst);
        testAnalystId = analyst.getId();

        // 테스트용 종목 생성
        Stock stock = new Stock();
        stock.setStockCode("005930");
        stock.setStockName("삼성전자");
        stock.setSector("반도체");
        stock = stockRepository.save(stock);
        testStockId = stock.getId();

        // 초기 리포트 10개 생성
        for (int i = 0; i < 10; i++) {
            Report report = new Report();
            report.setAnalyst(analyst);
            report.setStock(stock);
            report.setReportTitle("테스트 리포트 " + i);
            report.setReportDate(LocalDateTime.now().minusDays(10 - i));
            report.setTargetPrice(80000 + i * 1000);
            report.setSurfaceOpinion(SurfaceOpinion.BUY);
            report.setHiddenOpinion(0.7);
            reportRepository.save(report);
        }

        log.info("✅ 테스트 데이터 준비 완료: analyst_id={}, 초기 리포트 10개", testAnalystId);
    }

    @Test
    @DisplayName("🔥 [동시성 문제 재현] 100개 스레드 동시 메트릭 업데이트 시 Lost Update 발생")
    void testLostUpdateWithoutLock() throws InterruptedException {
        // Given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        log.info("========================================");
        log.info("🧪 동시성 테스트 시작");
        log.info("   - 스레드 수: {}", threadCount);
        log.info("   - 대상 애널리스트 ID: {}", testAnalystId);
        log.info("========================================");

        long startTime = System.currentTimeMillis();

        // When: 100개 스레드가 동시에 메트릭 계산
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    // 각 스레드가 메트릭 계산 실행
                    analystMetricsService.calculateAndSaveAccuracyRate(testAnalystId);
                    successCount.incrementAndGet();

                    if (index % 10 == 0) {
                        log.debug("   → 스레드 {} 완료", index);
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    log.error("   ✗ 스레드 {} 실패: {}", index, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // 모든 스레드 완료 대기 (최대 30초)
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        log.info("========================================");
        log.info("⏱️  실행 완료: {}ms", duration);
        log.info("   - 성공: {}/{}", successCount.get(), threadCount);
        log.info("   - 실패: {}/{}", failureCount.get(), threadCount);
        log.info("========================================");

        // Then: 결과 확인
        assertThat(completed).isTrue();

        // DB에서 실제 업데이트 카운트 확인
        AnalystMetrics result = metricsRepository.findAll().stream()
                .filter(m -> m.getAnalyst().getId().equals(testAnalystId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("메트릭을 찾을 수 없습니다"));

        log.info("========================================");
        log.info("📊 최종 결과 분석");
        log.info("   - 예상 업데이트 횟수: {}", threadCount);
        log.info("   - 실제 업데이트 횟수: {}", result.getUpdateCount());
        log.info("   - Lost Update 발생: {} 건", threadCount - result.getUpdateCount());
        log.info("   - Lost Update 비율: {}%",
                String.format("%.1f", (threadCount - result.getUpdateCount()) * 100.0 / threadCount));
        log.info("========================================");

        log.info("📋 최종 메트릭 상태:");
        log.info("   - Accuracy Rate: {}", result.getAccuracyRate());
        log.info("   - Return Rate: {}", result.getReturnRate());
        log.info("   - Update Count: {}", result.getUpdateCount());
        log.info("   - Updated At: {}", result.getUpdatedAt());
        log.info("========================================");

        // ❌ 이 테스트는 실패할 것으로 예상 (Lost Update 발생)
        // 100번 업데이트 되어야 하는데 실제로는 더 적을 것
        log.warn("⚠️  검증: updateCount가 {}보다 작으면 Lost Update 발생!", threadCount);

        // 실제 검증 (주석 처리 - 실패가 예상되므로)
        // assertThat(result.getUpdateCount()).isEqualTo(threadCount);

        // 대신 Lost Update가 발생했음을 확인
        int lostUpdates = threadCount - result.getUpdateCount();
        log.error("🚨 Lost Update {} 건 발생! ({}%)",
                lostUpdates,
                String.format("%.1f", lostUpdates * 100.0 / threadCount));

        // 통계 출력
        assertThat(result.getUpdateCount()).isLessThan(threadCount);
        assertThat(lostUpdates).isGreaterThan(0);
    }

    @Test
    @DisplayName("📌 [참고] 단일 스레드 실행 시 정상 동작 확인")
    void testSingleThreadUpdate() {
        // Given
        log.info("========================================");
        log.info("🧪 단일 스레드 테스트 (비교용)");
        log.info("========================================");

        // When: 단일 스레드로 5번 실행
        for (int i = 0; i < 5; i++) {
            analystMetricsService.calculateAndSaveAccuracyRate(testAnalystId);
            log.info("   → {} 번째 업데이트 완료", i + 1);
        }

        // Then: 정확히 5번 업데이트 되어야 함
        AnalystMetrics result = metricsRepository.findAll().stream()
                .filter(m -> m.getAnalyst().getId().equals(testAnalystId))
                .findFirst()
                .orElseThrow();

        log.info("========================================");
        log.info("📊 결과:");
        log.info("   - 예상 업데이트 횟수: 5");
        log.info("   - 실제 업데이트 횟수: {}", result.getUpdateCount());
        log.info("========================================");

        assertThat(result.getUpdateCount()).isEqualTo(5);
        log.info("✅ 단일 스레드에서는 정상 동작!");
    }
}

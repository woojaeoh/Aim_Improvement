package capstone25_2.aim.service;

import capstone25_2.aim.domain.dto.analyst.AnalystMetricsDTO;
import capstone25_2.aim.domain.dto.analyst.AnalystRankingResponseDTO;
import capstone25_2.aim.domain.entity.*;
import capstone25_2.aim.repository.AnalystMetricsRepository;
import capstone25_2.aim.repository.AnalystRepository;
import capstone25_2.aim.repository.ClosePriceRepository;
import capstone25_2.aim.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalystMetricsService {

    private final AnalystMetricsRepository metricsRepository;
    private final ReportRepository reportRepository;
    private final AnalystRepository analystRepository;
    private final ClosePriceRepository closePriceRepository;

    // 랭킹 리스트 조회 (기본: accuracyRate 순)
    @Transactional(readOnly = true)
    public AnalystRankingResponseDTO getRankedAnalysts(String sortBy) {
        List<AnalystMetrics> metricsList = metricsRepository.findAll();
        return createRankedResponse(metricsList,sortBy);
    }

    // 🔹 특정 종목 기준 랭킹
    @Transactional(readOnly = true)
    public AnalystRankingResponseDTO getRankedAnalystsByStock(Long stockId, String sortBy) {
        // 1. 해당 종목의 리포트를 전부 가져옴
        List<Long> analystIds = reportRepository.findByStockId(stockId).stream()
                .map(r -> r.getAnalyst().getId())
                .distinct()
                .toList();

        // 2. 애널리스트 ID 기반으로 메트릭 필터링
        List<AnalystMetrics> metricsList = metricsRepository.findAll().stream()
                .filter(m -> analystIds.contains(m.getAnalyst().getId()))
                .toList();

        // 3. 정렬 결과 반환
        return createRankedResponse(metricsList, sortBy);
    }

    // 내부 정렬 로직 (중복 제거)
    private static AnalystRankingResponseDTO createRankedResponse(List<AnalystMetrics> metricsList, String sortBy) {
        Comparator<AnalystMetrics> comparator = switch (sortBy) {
            case "returnRate" -> Comparator.comparing(AnalystMetrics::getReturnRate).reversed();
            case "targetDiffRate" -> Comparator.comparing(AnalystMetrics::getTargetDiffRate);
            default -> Comparator.comparing(AnalystMetrics::getAccuracyRate).reversed();
        };

        List<AnalystMetricsDTO> ranking = metricsList.stream()
                .sorted(comparator)
                .map(AnalystMetricsDTO::fromEntity)
                .toList();

        // 순위 부여
        int totalAnalysts = ranking.size();
        for (int i = 0; i < ranking.size(); i++) {
            AnalystMetricsDTO dto = ranking.get(i);
            dto.setRank(i + 1);  // 1부터 시작
            dto.setTotalAnalysts(totalAnalysts);
        }

        return AnalystRankingResponseDTO.builder()
                .criteria(sortBy)
                .rankingList(ranking)
                .build();
    }

    /**
     * 애널리스트 정확도, 수익률, 목표가 오차율 계산 후 저장
     * 모든 리포트 기준으로 계산
     * 의견변화 시점 기준으로 평가 (의견변화 시점 이후 1년 내 모든 리포트 평가)
     */
    @Transactional
    public void calculateAndSaveAccuracyRate(Long analystId) {
        // 1. 모든 리포트 조회
        List<Report> recentReports = reportRepository
                .findByAnalystIdOrderByReportDateDesc(analystId);

        if (recentReports.isEmpty()) {
            return; // 리포트가 없으면 계산 불가
        }

        // 2. 종목별로 그룹핑
        Map<Long, List<Report>> reportsByStock = recentReports.stream()
                .collect(Collectors.groupingBy(r -> r.getStock().getId()));

        // 3. 모든 평가 결과를 리스트로 수집
        List<EvaluationResult> allEvaluations = new ArrayList<>();

        for (Map.Entry<Long, List<Report>> entry : reportsByStock.entrySet()) {
            List<Report> stockReports = entry.getValue();

            // 날짜순 정렬 (오래된 것부터)
            stockReports.sort(Comparator.comparing(Report::getReportDate));

            // 모든 리포트 평가
            for (int i = 0; i < stockReports.size(); i++) {
                Report currentReport = stockReports.get(i);

                // 리포트 발행 시점의 종가 조회
                Optional<ClosePrice> reportDatePriceOpt = getActualPriceAtDate(
                        currentReport.getStock().getId(), currentReport.getReportDate());

                if (reportDatePriceOpt.isEmpty()) {
                    continue; // 발행 시점 종가 없으면 평가 불가
                }

                Integer reportDatePrice = reportDatePriceOpt.get().getClosePrice();
                LocalDateTime oneYearLater = currentReport.getReportDate().plusYears(1);

                // 1년 이내에 의견 변화가 있는지 확인
                Optional<Report> opinionChange = findOpinionChangeBeforeTarget(currentReport, oneYearLater);

                Integer comparePrice;
                if (opinionChange.isPresent()) {
                    // 의견 변화가 있으면 → 의견 변화 시점의 종가와 비교
                    Optional<ClosePrice> changePriceOpt = getActualPriceAtDate(
                            currentReport.getStock().getId(), opinionChange.get().getReportDate());

                    if (changePriceOpt.isEmpty()) {
                        continue; // 의견 변화 시점 종가 없으면 평가 불가
                    }
                    comparePrice = changePriceOpt.get().getClosePrice();
                } else {
                    // 의견 변화가 없으면 → 1년 후 종가와 비교
                    Optional<ClosePrice> oneYearPriceOpt = getActualPriceAtDate(
                            currentReport.getStock().getId(), oneYearLater);

                    if (oneYearPriceOpt.isEmpty()) {
                        continue; // 1년 후 종가 없으면 평가 불가
                    }
                    comparePrice = oneYearPriceOpt.get().getClosePrice();
                }

                // 리포트 평가
                EvaluationResult result = evaluateReport(
                        currentReport, reportDatePrice, comparePrice);
                if (result != null) {
                    allEvaluations.add(result);
                }
            }
        }

        // 4. 전체 평가 결과 집계
        if (allEvaluations.isEmpty()) {
            return; // 평가 가능한 리포트가 없으면 저장하지 않음
        }

        int correctCount = (int) allEvaluations.stream().filter(r -> r.isCorrect).count();
        double accuracyRate = (double) correctCount / allEvaluations.size() * 100.0;

        Double averageReturn = allEvaluations.stream()
                .mapToDouble(r -> r.returnRate)
                .average()
                .isPresent() ? allEvaluations.stream()
                .mapToDouble(r -> r.returnRate)
                .average()
                .getAsDouble() : null;

        Double averageTargetDiff = allEvaluations.stream()
                .filter(r -> r.targetDiffRate != null)
                .mapToDouble(r -> r.targetDiffRate)
                .average()
                .isPresent() ? allEvaluations.stream()
                .filter(r -> r.targetDiffRate != null)
                .mapToDouble(r -> r.targetDiffRate)
                .average()
                .getAsDouble() : null;

        // 상대적 성과 계산 (전체 애널리스트 평균과 비교)
        GlobalAverageMetrics globalAvg = calculateGlobalAverageMetrics();

        Double avgReturnDiff = (globalAvg.averageReturn != null && averageReturn != null)
            ? averageReturn - globalAvg.averageReturn
            : null;

        Double avgTargetDiff = (globalAvg.averageTargetDiff != null && averageTargetDiff != null)
            ? averageTargetDiff - globalAvg.averageTargetDiff
            : null;

        // 5. AnalystMetrics 조회 또는 생성 후 저장 (소수점 두자리로 반올림)
        AnalystMetrics metrics = analystRepository.findById(analystId)
                .map(analyst -> analyst.getAnalystMetrics())
                .orElseGet(AnalystMetrics::new);

        metrics.setAccuracyRate(roundToTwoDecimals(accuracyRate));
        metrics.setReturnRate(averageReturn != null ? roundToTwoDecimals(averageReturn) : null);
        metrics.setTargetDiffRate(averageTargetDiff != null ? roundToTwoDecimals(averageTargetDiff) : null);
        metrics.setAvgReturnDiff(avgReturnDiff != null ? roundToTwoDecimals(avgReturnDiff) : null);
        metrics.setAvgTargetDiff(avgTargetDiff != null ? roundToTwoDecimals(avgTargetDiff) : null);
        metrics.setAnalyst(analystRepository.findById(analystId).orElseThrow());

        metricsRepository.save(metrics);
    }

    /**
     * 애널리스트 정확도, 수익률, 목표가 오차율 계산 후 저장 (전체 평균 비교 버전)
     * 전체 애널리스트 평균과 비교하여 성능 최적화
     * 모든 리포트 평가 (의견 변화시 변화 시점 종가, 없으면 1년 후 종가 비교)
     *
     * @param analystId 애널리스트 ID
     * @param globalAverage 전체 애널리스트 평균 메트릭
     */
    @Transactional
    public void calculateAndSaveAccuracyRateWithCache(
            Long analystId,
            GlobalAverageMetrics globalAverage) {

        // 1. 모든 리포트 조회
        List<Report> recentReports = reportRepository
                .findByAnalystIdOrderByReportDateDesc(analystId);

        if (recentReports.isEmpty()) {
            return; // 리포트가 없으면 계산 불가
        }

        // 2. 종목별로 그룹핑
        Map<Long, List<Report>> reportsByStock = recentReports.stream()
                .collect(Collectors.groupingBy(r -> r.getStock().getId()));

        // 3. 모든 평가 결과를 리스트로 수집
        List<EvaluationResult> allEvaluations = new ArrayList<>();

        for (Map.Entry<Long, List<Report>> entry : reportsByStock.entrySet()) {
            List<Report> stockReports = entry.getValue();

            // 날짜순 정렬 (오래된 것부터)
            stockReports.sort(Comparator.comparing(Report::getReportDate));

            // 모든 리포트 평가
            for (int i = 0; i < stockReports.size(); i++) {
                Report currentReport = stockReports.get(i);

                // 리포트 발행 시점의 종가 조회
                Optional<ClosePrice> reportDatePriceOpt = getActualPriceAtDate(
                        currentReport.getStock().getId(), currentReport.getReportDate());

                if (reportDatePriceOpt.isEmpty()) {
                    continue; // 발행 시점 종가 없으면 평가 불가
                }

                Integer reportDatePrice = reportDatePriceOpt.get().getClosePrice();
                LocalDateTime oneYearLater = currentReport.getReportDate().plusYears(1);

                // 1년 이내에 의견 변화가 있는지 확인
                Optional<Report> opinionChange = findOpinionChangeBeforeTarget(currentReport, oneYearLater);

                Integer comparePrice;
                if (opinionChange.isPresent()) {
                    // 의견 변화가 있으면 → 의견 변화 시점의 종가와 비교
                    Optional<ClosePrice> changePriceOpt = getActualPriceAtDate(
                            currentReport.getStock().getId(), opinionChange.get().getReportDate());

                    if (changePriceOpt.isEmpty()) {
                        continue; // 의견 변화 시점 종가 없으면 평가 불가
                    }
                    comparePrice = changePriceOpt.get().getClosePrice();
                } else {
                    // 의견 변화가 없으면 → 1년 후 종가와 비교
                    Optional<ClosePrice> oneYearPriceOpt = getActualPriceAtDate(
                            currentReport.getStock().getId(), oneYearLater);

                    if (oneYearPriceOpt.isEmpty()) {
                        continue; // 1년 후 종가 없으면 평가 불가
                    }
                    comparePrice = oneYearPriceOpt.get().getClosePrice();
                }

                // 리포트 평가
                EvaluationResult result = evaluateReport(
                        currentReport, reportDatePrice, comparePrice);
                if (result != null) {
                    allEvaluations.add(result);
                }
            }
        }

        // 4. 전체 평가 결과 집계
        if (allEvaluations.isEmpty()) {
            return; // 평가 가능한 리포트가 없으면 저장하지 않음
        }

        int correctCount = (int) allEvaluations.stream().filter(r -> r.isCorrect).count();
        double accuracyRate = (double) correctCount / allEvaluations.size() * 100.0;

        Double averageReturn = allEvaluations.stream()
                .mapToDouble(r -> r.returnRate)
                .average()
                .isPresent() ? allEvaluations.stream()
                .mapToDouble(r -> r.returnRate)
                .average()
                .getAsDouble() : null;

        Double averageTargetDiff = allEvaluations.stream()
                .filter(r -> r.targetDiffRate != null)
                .mapToDouble(r -> r.targetDiffRate)
                .average()
                .isPresent() ? allEvaluations.stream()
                .filter(r -> r.targetDiffRate != null)
                .mapToDouble(r -> r.targetDiffRate)
                .average()
                .getAsDouble() : null;

        // 5. 전체 애널리스트 평균 대비 차이 계산
        Double avgReturnDiff = null;
        Double avgTargetDiff = null;

        if (globalAverage != null) {
            // 수익률 차이: 이 애널리스트의 평균 수익률 - 전체 평균 수익률
            if (globalAverage.averageReturn != null && averageReturn != null) {
                avgReturnDiff = averageReturn - globalAverage.averageReturn;
            }

            // 목표가 오차율 차이: 이 애널리스트의 평균 목표가 오차율 - 전체 평균 목표가 오차율
            if (globalAverage.averageTargetDiff != null && averageTargetDiff != null) {
                avgTargetDiff = averageTargetDiff - globalAverage.averageTargetDiff;
            }
        }

        // 6. AnalystMetrics 조회 또는 생성 후 저장 (소수점 두자리로 반올림)
        AnalystMetrics metrics = analystRepository.findById(analystId)
                .map(analyst -> analyst.getAnalystMetrics())
                .orElseGet(AnalystMetrics::new);

        metrics.setAccuracyRate(roundToTwoDecimals(accuracyRate));
        metrics.setReturnRate(averageReturn != null ? roundToTwoDecimals(averageReturn) : null);
        metrics.setTargetDiffRate(averageTargetDiff != null ? roundToTwoDecimals(averageTargetDiff) : null);
        metrics.setAvgReturnDiff(avgReturnDiff != null ? roundToTwoDecimals(avgReturnDiff) : null);
        metrics.setAvgTargetDiff(avgTargetDiff != null ? roundToTwoDecimals(avgTargetDiff) : null);
        metrics.setAnalyst(analystRepository.findById(analystId).orElseThrow());

        metricsRepository.save(metrics);
    }

    /**
     * 평가 결과를 담는 내부 클래스
     */
    private static class EvaluationResult {
        boolean isCorrect;
        double returnRate;        // 수익률
        Double targetDiffRate;    // 목표가 오차율 (의견 불일치시 null)

        EvaluationResult(boolean isCorrect, double returnRate, Double targetDiffRate) {
            this.isCorrect = isCorrect;
            this.returnRate = returnRate;
            this.targetDiffRate = targetDiffRate;
        }
    }

    /**
     * 리포트 평가 (발행 시점 종가와 비교 시점 종가 사용)
     * @param report 평가 대상 리포트
     * @param reportDatePrice 리포트 발행 시점의 종가
     * @param comparePrice 비교 시점의 종가 (의견 변화 시점 또는 1년 후)
     * @return EvaluationResult (정확도, 수익률, 목표가 오차율 포함) 또는 null (평가 불가)
     */
    private EvaluationResult evaluateReport(
            Report report, Integer reportDatePrice, Integer comparePrice) {

        Integer targetPrice = report.getTargetPrice();
        Double hiddenOpinion = report.getHiddenOpinion();

        if (targetPrice == null || targetPrice == 0 || reportDatePrice == 0 || comparePrice == 0) {
            return null; // 필요한 데이터가 없으면 평가 불가
        }

        // 1. 정확도 판단 (hiddenOpinion 기준)
        boolean isCorrect = isOpinionCorrect(hiddenOpinion, targetPrice, comparePrice);

        // 2. 수익률 계산: (비교 시점 주가 - 발행 시점 주가) / 발행 시점 주가 * 100
        double returnRate = ((double) (comparePrice - reportDatePrice) / reportDatePrice) * 100.0;

        // 3. 목표가 오차율 계산: 의견 불일치시 null 반환 (BUY인데 하락 예측 등)
        Double targetDiffRate = null;
        if (!isOpinionMismatch(report.getSurfaceOpinion(), hiddenOpinion)) {
            targetDiffRate = Math.abs((double) (targetPrice - comparePrice) / targetPrice) * 100.0;
        }

        return new EvaluationResult(isCorrect, returnRate, targetDiffRate);
    }

    /**
     * 의견변화 시점 기준으로 리포트 평가 (하위 호환성 유지용)
     * @param report 평가 대상 리포트 (의견변화가 발생한 리포트)
     * @param baseDate 기준 의견변화 시점
     * @param baseClosePrice 기준 의견변화 시점의 종가
     * @return EvaluationResult (정확도, 수익률, 목표가 오차율 포함) 또는 null (평가 불가)
     */
    private EvaluationResult evaluateReportAfterOpinionChange(
            Report report, LocalDateTime baseDate, Integer baseClosePrice) {

        // 1. 리포트 발행 후 1년 뒤의 실제 주가 조회
        LocalDateTime oneYearLater = report.getReportDate().plusYears(1);
        Optional<ClosePrice> actualPriceOpt = getActualPriceAtDate(report.getStock().getId(), oneYearLater);

        if (actualPriceOpt.isEmpty()) {
            return null; // 1년 후 주가 데이터 없으면 평가 불가
        }

        Integer oneYearLaterPrice = actualPriceOpt.get().getClosePrice();
        Integer targetPrice = report.getTargetPrice();

        if (targetPrice == null || targetPrice == 0 || baseClosePrice == 0) {
            return null; // 목표가나 기준 종가가 없으면 평가 불가
        }

        // 2. 예측 방향 판단 (목표가 vs 기준 종가)
        boolean predictedUp = targetPrice > baseClosePrice;

        // 3. 실제 방향 판단 (1년 후 주가 vs 기준 종가)
        boolean actualUp = oneYearLaterPrice > baseClosePrice;

        // 4. 정확도 판단: 예측 방향과 실제 방향이 일치하면 정답
        boolean isCorrect = (predictedUp == actualUp);

        // 5. 수익률 계산: (1년 후 주가 - 기준 종가) / 기준 종가 * 100
        double returnRate = ((double) (oneYearLaterPrice - baseClosePrice) / baseClosePrice) * 100.0;

        // 6. 목표가 오차율 계산: |목표가 - 기준 종가| / 기준 종가 * 100
        double targetDiffRate = Math.abs((double) (targetPrice - baseClosePrice) / baseClosePrice) * 100.0;

        return new EvaluationResult(isCorrect, returnRate, targetDiffRate);
    }

    /**
     * 개별 리포트 평가 (정확도 + 수익률 + 목표가 오차율)
     * @return EvaluationResult (정확도, 수익률, 목표가 오차율 포함) 또는 null (평가 불가)
     */
    private EvaluationResult evaluateReportWithReturn(Report report) {
        // 1. 중간에 의견 변화가 있는지 확인
        LocalDateTime oneYearLater = report.getReportDate().plusYears(1);
        Optional<Report> opinionChange = findOpinionChangeBeforeTarget(report, oneYearLater);

        // 의견이 변경되었으면 이 리포트는 평가 제외 (의견 변화 이후의 새 리포트부터 다시 평가)
        if (opinionChange.isPresent()) {
            return null;
        }

        // 2. 리포트 발행 시점의 실제 주가 조회
        Optional<ClosePrice> reportDatePriceOpt = getActualPriceAtDate(
                report.getStock().getId(), report.getReportDate());

        if (reportDatePriceOpt.isEmpty()) {
            return null; // 리포트 발행 시점 주가 데이터 없으면 평가 불가
        }

        Integer reportDatePrice = reportDatePriceOpt.get().getClosePrice();

        // 3. 1년 후의 실제 주가 조회
        Optional<ClosePrice> actualPriceOpt = getActualPriceAtDate(report.getStock().getId(), oneYearLater);

        if (actualPriceOpt.isEmpty()) {
            return null; // 1년 후 주가 데이터 없으면 평가 불가
        }

        Integer oneYearLaterPrice = actualPriceOpt.get().getClosePrice();
        Integer targetPrice = report.getTargetPrice();
        Double hiddenOpinion = report.getHiddenOpinion();

        if (targetPrice == null || targetPrice == 0 || reportDatePrice == 0) {
            return null; // 목표가나 발행시점 주가가 없으면 평가 불가
        }

        // 4. 정확도 판단
        boolean isCorrect = isOpinionCorrect(hiddenOpinion, targetPrice, oneYearLaterPrice);

        // 5. 수익률 계산: (1년 후 종가 - 리포트 발행시점 종가) / 리포트 발행시점 종가 * 100
        double returnRate = ((double) (oneYearLaterPrice - reportDatePrice) / reportDatePrice) * 100.0;

        // 6. 목표가 오차율 계산: 의견 불일치시 null 반환 (BUY인데 하락 예측 등)
        Double targetDiffRate = null;
        if (!isOpinionMismatch(report.getSurfaceOpinion(), hiddenOpinion)) {
            targetDiffRate = Math.abs((double) (targetPrice - oneYearLaterPrice) / targetPrice) * 100.0;
        }

        return new EvaluationResult(isCorrect, returnRate, targetDiffRate);
    }

    /**
     * 특정 날짜 이후 가장 가까운 거래일의 실제 주가 조회
     */
    private Optional<ClosePrice> getActualPriceAtDate(Long stockId, LocalDateTime targetDateTime) {
        return closePriceRepository.findFirstByStockIdAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                stockId, targetDateTime.toLocalDate());
    }

    /**
     * 리포트 이후 ~ 목표일 이전에 같은 종목에 대한 의견 변화가 있었는지 확인
     * hiddenOpinion의 3단계 분류(BUY/HOLD/SELL)가 변경된 경우에만 의견 변화로 판단
     */
    private Optional<Report> findOpinionChangeBeforeTarget(Report originalReport, LocalDateTime targetDate) {
        // 원본 리포트 이후의 모든 리포트를 시간순으로 조회
        List<Report> laterReports = reportRepository.findByAnalystIdAndStockIdOrderByReportDateAsc(
                originalReport.getAnalyst().getId(),
                originalReport.getStock().getId()
        ).stream()
                .filter(r -> r.getReportDate().isAfter(originalReport.getReportDate()))
                .filter(r -> r.getReportDate().isBefore(targetDate))
                .toList();

        if (laterReports.isEmpty()) {
            return Optional.empty();
        }

        // 원본 리포트의 의견 분류
        String originalCategory = HiddenOpinionLabel.toSimpleCategory(originalReport.getHiddenOpinion());
        String previousCategory = originalCategory;

        // 시간순으로 순회하면서 의견 변화가 있는지 확인
        for (Report laterReport : laterReports) {
            String currentCategory = HiddenOpinionLabel.toSimpleCategory(laterReport.getHiddenOpinion());

            // 이전 리포트와 의견이 다르면 의견 변화로 판단
            if (!java.util.Objects.equals(previousCategory, currentCategory)) {
                return Optional.of(laterReport);
            }
            previousCategory = currentCategory;
        }

        return Optional.empty();
    }

    /**
     * hiddenOpinion과 실제 주가 변동이 일치하는지 3단계로 판단
     *
     * 예측 분류 (3단계):
     * - BUY: hiddenOpinion >= 0.5
     * - HOLD: 0.17 <= hiddenOpinion < 0.5
     * - SELL: hiddenOpinion < 0.17
     *
     * 실제 결과 분류 (목표가 기준):
     * - BUY: 1년 후 실제 주가 >= 목표가
     * - HOLD: 목표가 * 0.75 <= 실제 주가 < 목표가 (목표가 ±10% 범위)
     * - SELL: 실제 주가 < 목표가 * 0.75
     *
     * @param hiddenOpinion 숨겨진 의견 (0.0 ~ 1.0)
     * @param targetPrice 목표가
     * @param actualPrice 1년 후 실제 주가
     * @return 예측과 실제가 일치하는지 여부
     */
    private boolean isOpinionCorrect(Double hiddenOpinion, Integer targetPrice, Integer actualPrice) {
        if (hiddenOpinion == null || targetPrice == null || actualPrice == null || targetPrice == 0) {
            return false;
        }

        // 1. 예측을 3단계로 분류 (BUY/HOLD/SELL)
        String predictedCategory = HiddenOpinionLabel.toSimpleCategory(hiddenOpinion);
        if (predictedCategory == null) {
            return false;
        }

        // 2. 실제 주가를 3단계로 분류 (목표가 기준)
        String actualCategory;
        if (actualPrice >= targetPrice* 0.8) {
            actualCategory = "BUY";  // 목표가 이상 달성
        } else if (actualPrice >= targetPrice * 0.7) {
            actualCategory = "HOLD";  // 목표가 75% ~ 100% 사이
        } else {
            actualCategory = "SELL";  // 목표가 75% 미달
        }

        // 3. 예측과 실제가 일치하면 정답
        return predictedCategory.equals(actualCategory);
    }

    /**
     * surfaceOpinion과 hiddenOpinion이 불일치하는지 판단
     * BUY인데 hiddenOpinion이 하락(< 0.5)이거나
     * SELL인데 hiddenOpinion이 상승(>= 0.5)인 경우 불일치로 판단
     *
     * @param surfaceOpinion 표면 의견 (BUY, HOLD, SELL)
     * @param hiddenOpinion 숨겨진 의견 (0.0 ~ 1.0)
     * @return 의견 불일치 여부
     */
    private boolean isOpinionMismatch(SurfaceOpinion surfaceOpinion, Double hiddenOpinion) {
        if (surfaceOpinion == null || hiddenOpinion == null) {
            return false;
        }

        boolean hiddenBullish = hiddenOpinion >= 0.5; // 숨은 의견이 상승

        // BUY인데 hiddenOpinion이 하락 예측
        if (surfaceOpinion == SurfaceOpinion.BUY && !hiddenBullish) {
            return true;
        }

        // SELL인데 hiddenOpinion이 상승 예측
        if (surfaceOpinion == SurfaceOpinion.SELL && hiddenBullish) {
            return true;
        }

        return false;
    }

    /**
     * 특정 종목에 대한 모든 애널리스트들의 평균 수익률과 목표가 오차율 계산
     *
     * @param stockId 종목 ID
     * @param fiveYearsAgo 5년 전 날짜
     * @return 해당 종목에 대한 모든 애널리스트들의 평균 메트릭
     */
    private StockAverageMetrics calculateStockAverageMetrics(
            Long stockId, LocalDateTime fiveYearsAgo) {

        // 해당 종목에 대한 모든 애널리스트들의 최근 5년 리포트 조회
        List<Report> allAnalystReports = reportRepository
                .findByStockIdAndReportDateAfterOrderByReportDateDesc(stockId, fiveYearsAgo);

        if (allAnalystReports.isEmpty()) {
            return new StockAverageMetrics(null, null);
        }

        // 각 리포트 평가
        double totalReturn = 0.0;
        int returnCount = 0;
        double totalTargetDiff = 0.0;
        int targetDiffCount = 0;

        for (Report report : allAnalystReports) {
            EvaluationResult result = evaluateReportWithReturn(report);
            if (result != null) {
                totalReturn += result.returnRate;
                returnCount++;

                if (result.targetDiffRate != null) {
                    totalTargetDiff += result.targetDiffRate;
                    targetDiffCount++;
                }
            }
        }

        Double averageReturn = (returnCount > 0) ? totalReturn / returnCount : null;
        Double averageTargetDiff = (targetDiffCount > 0) ? totalTargetDiff / targetDiffCount : null;

        return new StockAverageMetrics(averageReturn, averageTargetDiff);
    }

    /**
     * 모든 애널리스트의 지표를 전체 평균과 비교하여 일괄 계산 (성능 최적화 버전)
     *
     * @return 계산된 애널리스트 수
     */
    @Transactional
    public int calculateAllAnalystMetricsWithCache() {
        System.out.println("📊 모든 애널리스트 지표 일괄 계산 시작 (최적화 버전)...");

        // 0. 모든 기존 메트릭 삭제 (잘못된 데이터 제거)
        System.out.println("🗑️ 기존 메트릭 초기화 중...");
        int deletedCount = metricsRepository.findAll().size();
        metricsRepository.deleteAll();
        System.out.println("✅ 기존 메트릭 삭제 완료: " + deletedCount + "개");

        // 1. 전체 애널리스트의 평균 수익률과 목표가 오차율 계산
        System.out.println("📈 전체 애널리스트 평균 계산 중...");
        GlobalAverageMetrics globalAverage = calculateGlobalAverageMetrics();

        if (globalAverage.averageReturn != null) {
            System.out.println("  ✓ 전체 평균 수익률: " + String.format("%.2f", globalAverage.averageReturn) + "%");
        }
        if (globalAverage.averageTargetDiff != null) {
            System.out.println("  ✓ 전체 평균 목표가 오차율: " + String.format("%.2f", globalAverage.averageTargetDiff) + "%");
        }

        // 2. 모든 애널리스트 조회
        List<Analyst> allAnalysts = analystRepository.findAll();
        System.out.println("👥 전체 애널리스트 수: " + allAnalysts.size());

        // 3. 각 애널리스트마다 전체 평균과 비교하여 지표 계산
        int calculatedCount = 0;
        for (Analyst analyst : allAnalysts) {
            try {
                calculateAndSaveAccuracyRateWithCache(analyst.getId(), globalAverage);
                calculatedCount++;

                // 10명마다 진행 상황 출력
                if (calculatedCount % 10 == 0) {
                    System.out.println("  ⏳ 애널리스트 계산: " + calculatedCount + "/" + allAnalysts.size());
                }
            } catch (Exception e) {
                System.err.println("⚠️ 애널리스트 " + analyst.getId() + " 지표 계산 실패: " + e.getMessage());
            }
        }

        System.out.println("✅ 애널리스트 지표 계산 완료: " + calculatedCount + "명");

        // 4. aim's score 일괄 계산
        System.out.println("🎯 aim's score 일괄 계산 시작...");
        int scoreCalculatedCount = calculateAllAimsScores();
        System.out.println("✅ aim's score 계산 완료: " + scoreCalculatedCount + "명");

        return calculatedCount;
    }

    /**
     * 모든 애널리스트의 aim's score 일괄 계산
     * 백분위 기반 점수 시스템 (40~100점)
     *
     * @return 계산된 애널리스트 수
     */
    @Transactional
    public int calculateAllAimsScores() {
        // 1. 모든 애널리스트 메트릭 조회
        List<AnalystMetrics> allMetrics = metricsRepository.findAll();

        if (allMetrics.isEmpty()) {
            return 0;
        }

        // 2. 각 지표별 정렬된 리스트 생성
        List<AnalystMetrics> sortedByReturn = new ArrayList<>(allMetrics);
        List<AnalystMetrics> sortedByReturnDiff = new ArrayList<>(allMetrics);
        List<AnalystMetrics> sortedByAccuracy = new ArrayList<>(allMetrics);
        List<AnalystMetrics> sortedByTargetDiff = new ArrayList<>(allMetrics);

        // 3. 각 지표별로 정렬 (null 값 제외)
        sortedByReturn = sortedByReturn.stream()
                .filter(m -> m.getReturnRate() != null)
                .sorted(Comparator.comparing(AnalystMetrics::getReturnRate))
                .collect(Collectors.toList());

        sortedByReturnDiff = sortedByReturnDiff.stream()
                .filter(m -> m.getAvgReturnDiff() != null)
                .sorted(Comparator.comparing(AnalystMetrics::getAvgReturnDiff))
                .collect(Collectors.toList());

        sortedByAccuracy = sortedByAccuracy.stream()
                .filter(m -> m.getAccuracyRate() != null)
                .sorted(Comparator.comparing(AnalystMetrics::getAccuracyRate))
                .collect(Collectors.toList());

        sortedByTargetDiff = sortedByTargetDiff.stream()
                .filter(m -> m.getAvgTargetDiff() != null)
                .sorted(Comparator.comparing(AnalystMetrics::getAvgTargetDiff))  // 낮을수록 좋음
                .collect(Collectors.toList());

        // 4. 각 애널리스트의 백분위 계산 및 점수 저장
        int calculatedCount = 0;
        for (AnalystMetrics metrics : allMetrics) {
            try {
                // 각 지표의 백분위 계산
                double returnPercentile = calculatePercentile(metrics, sortedByReturn,
                        AnalystMetrics::getReturnRate);
                double returnDiffPercentile = calculatePercentile(metrics, sortedByReturnDiff,
                        AnalystMetrics::getAvgReturnDiff);
                double accuracyPercentile = calculatePercentile(metrics, sortedByAccuracy,
                        AnalystMetrics::getAccuracyRate);
                double targetDiffPercentile = calculateReversePercentile(metrics, sortedByTargetDiff,
                        AnalystMetrics::getAvgTargetDiff);  // 낮을수록 높은 백분위

                // 가중 백분위 합계 계산
                double weightedPercentile = (returnPercentile * 0.35) +
                        (returnDiffPercentile * 0.25) +
                        (accuracyPercentile * 0.25) +
                        (targetDiffPercentile * 0.15);

                // 최종 점수 계산 (40~100점 범위)
                int aimsScore = (int) Math.round(weightedPercentile * 0.6 + 40);

                // 점수 저장
                metrics.setAimsScore(aimsScore);
                metricsRepository.save(metrics);
                calculatedCount++;

            } catch (Exception e) {
                System.err.println("⚠️ 애널리스트 " + metrics.getAnalyst().getId() +
                        " aim's score 계산 실패: " + e.getMessage());
            }
        }

        return calculatedCount;
    }

    /**
     * 백분위 계산 (높을수록 좋은 지표용)
     *
     * @param metrics 대상 애널리스트 메트릭
     * @param sortedList 정렬된 전체 메트릭 리스트 (오름차순)
     * @param getter 지표 값을 가져오는 함수
     * @return 백분위 (0~100)
     */
    private double calculatePercentile(AnalystMetrics metrics,
                                       List<AnalystMetrics> sortedList,
                                       java.util.function.Function<AnalystMetrics, Double> getter) {
        Double value = getter.apply(metrics);
        if (value == null || sortedList.isEmpty()) {
            return 50.0; // 기본값
        }

        // 정렬된 리스트에서 순위 찾기
        int rank = 0;
        for (int i = 0; i < sortedList.size(); i++) {
            if (sortedList.get(i).getId().equals(metrics.getId())) {
                rank = i;
                break;
            }
        }

        // 백분위 계산: (순위 / 전체 수) * 100
        return ((double) rank / sortedList.size()) * 100.0;
    }

    /**
     * 역백분위 계산 (낮을수록 좋은 지표용)
     *
     * @param metrics 대상 애널리스트 메트릭
     * @param sortedList 정렬된 전체 메트릭 리스트 (오름차순)
     * @param getter 지표 값을 가져오는 함수
     * @return 백분위 (0~100)
     */
    private double calculateReversePercentile(AnalystMetrics metrics,
                                              List<AnalystMetrics> sortedList,
                                              java.util.function.Function<AnalystMetrics, Double> getter) {
        Double value = getter.apply(metrics);
        if (value == null || sortedList.isEmpty()) {
            return 50.0; // 기본값
        }

        // 정렬된 리스트에서 순위 찾기
        int rank = 0;
        for (int i = 0; i < sortedList.size(); i++) {
            if (sortedList.get(i).getId().equals(metrics.getId())) {
                rank = i;
                break;
            }
        }

        // 역백분위 계산: ((전체 수 - 순위 - 1) / 전체 수) * 100
        return ((double) (sortedList.size() - rank - 1) / sortedList.size()) * 100.0;
    }

    /**
     * 전체 애널리스트의 평균 수익률과 목표가 오차율 계산
     * 모든 리포트 평가 (의견 변화시 변화 시점 종가, 없으면 1년 후 종가 비교)
     *---
     * @return 전체 애널리스트들의 평균 메트릭
     */
    private GlobalAverageMetrics calculateGlobalAverageMetrics() {
        // 모든 리포트 조회
        List<Report> allReports = reportRepository.findAll();

        if (allReports.isEmpty()) {
            return new GlobalAverageMetrics(null, null);
        }

        // 애널리스트별, 종목별로 그룹핑
        Map<String, List<Report>> reportsByAnalystAndStock = allReports.stream()
                .collect(Collectors.groupingBy(r -> r.getAnalyst().getId() + "_" + r.getStock().getId()));

        // 모든 평가 결과를 리스트로 수집
        List<EvaluationResult> allEvaluations = new ArrayList<>();

        for (Map.Entry<String, List<Report>> entry : reportsByAnalystAndStock.entrySet()) {
            List<Report> reports = entry.getValue();

            // 날짜순 정렬 (오래된 것부터)
            reports.sort(Comparator.comparing(Report::getReportDate));

            // 모든 리포트 평가
            for (int i = 0; i < reports.size(); i++) {
                Report currentReport = reports.get(i);

                // 리포트 발행 시점의 종가 조회
                Optional<ClosePrice> reportDatePriceOpt = getActualPriceAtDate(
                        currentReport.getStock().getId(), currentReport.getReportDate());

                if (reportDatePriceOpt.isEmpty()) {
                    continue; // 발행 시점 종가 없으면 평가 불가
                }

                Integer reportDatePrice = reportDatePriceOpt.get().getClosePrice();
                LocalDateTime oneYearLater = currentReport.getReportDate().plusYears(1);

                // 1년 이내에 의견 변화가 있는지 확인
                Optional<Report> opinionChange = findOpinionChangeBeforeTarget(currentReport, oneYearLater);

                Integer comparePrice;
                if (opinionChange.isPresent()) {
                    // 의견 변화가 있으면 → 의견 변화 시점의 종가와 비교
                    Optional<ClosePrice> changePriceOpt = getActualPriceAtDate(
                            currentReport.getStock().getId(), opinionChange.get().getReportDate());

                    if (changePriceOpt.isEmpty()) {
                        continue; // 의견 변화 시점 종가 없으면 평가 불가
                    }
                    comparePrice = changePriceOpt.get().getClosePrice();
                } else {
                    // 의견 변화가 없으면 → 1년 후 종가와 비교
                    Optional<ClosePrice> oneYearPriceOpt = getActualPriceAtDate(
                            currentReport.getStock().getId(), oneYearLater);

                    if (oneYearPriceOpt.isEmpty()) {
                        continue; // 1년 후 종가 없으면 평가 불가
                    }
                    comparePrice = oneYearPriceOpt.get().getClosePrice();
                }

                // 리포트 평가
                EvaluationResult result = evaluateReport(
                        currentReport, reportDatePrice, comparePrice);
                if (result != null) {
                    allEvaluations.add(result);
                }
            }
        }

        if (allEvaluations.isEmpty()) {
            return new GlobalAverageMetrics(null, null);
        }

        // 평균 계산
        Double averageReturn = allEvaluations.stream()
                .mapToDouble(r -> r.returnRate)
                .average()
                .isPresent() ? allEvaluations.stream()
                .mapToDouble(r -> r.returnRate)
                .average()
                .getAsDouble() : null;

        Double averageTargetDiff = allEvaluations.stream()
                .filter(r -> r.targetDiffRate != null)
                .mapToDouble(r -> r.targetDiffRate)
                .average()
                .isPresent() ? allEvaluations.stream()
                .filter(r -> r.targetDiffRate != null)
                .mapToDouble(r -> r.targetDiffRate)
                .average()
                .getAsDouble() : null;

        return new GlobalAverageMetrics(averageReturn, averageTargetDiff);
    }

    /**
     * 소수점 두자리로 반올림
     */
    private double roundToTwoDecimals(double value) {
        // 소수점 첫째자리까지 반올림
        return Math.round(value * 10.0) / 10.0;
    }

    /**
     * 종목별 평균 메트릭을 담는 내부 클래스
     */
    private static class StockAverageMetrics {
        Double averageReturn;      // 해당 종목 모든 애널리스트들의 평균 수익률
        Double averageTargetDiff;  // 해당 종목 모든 애널리스트들의 평균 목표가 오차율

        StockAverageMetrics(Double averageReturn, Double averageTargetDiff) {
            this.averageReturn = averageReturn;
            this.averageTargetDiff = averageTargetDiff;
        }
    }

    /**
     * 전체 애널리스트 평균 메트릭을 담는 내부 클래스
     */
    private static class GlobalAverageMetrics {
        Double averageReturn;      // 전체 애널리스트들의 평균 수익률
        Double averageTargetDiff;  // 전체 애널리스트들의 평균 목표가 오차율

        GlobalAverageMetrics(Double averageReturn, Double averageTargetDiff) {
            this.averageReturn = averageReturn;
            this.averageTargetDiff = averageTargetDiff;
        }
    }

}

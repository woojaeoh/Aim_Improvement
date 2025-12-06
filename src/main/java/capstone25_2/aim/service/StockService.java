package capstone25_2.aim.service;

import capstone25_2.aim.domain.dto.stock.*;
import capstone25_2.aim.domain.entity.AnalystMetrics;
import capstone25_2.aim.domain.entity.ClosePrice;
import capstone25_2.aim.domain.entity.HiddenOpinionLabel;
import capstone25_2.aim.domain.entity.Report;
import capstone25_2.aim.domain.entity.Stock;
import capstone25_2.aim.domain.entity.SurfaceOpinion;
import capstone25_2.aim.repository.AnalystMetricsRepository;
import capstone25_2.aim.repository.ClosePriceRepository;
import capstone25_2.aim.repository.ReportRepository;
import capstone25_2.aim.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final ReportService reportService;
    private final ClosePriceRepository closePriceRepository;
    private final ReportRepository reportRepository;
    private final AnalystMetricsRepository analystMetricsRepository;

    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    // 종목 리스트 조회 (상승여력, 매수 비율 포함)
    // 쿼리 최적화: N+1 문제 해결 (전체 3개 쿼리로 처리)
    @Transactional(readOnly = true)
    public List<StockListDTO> getAllStocksWithRankingInfo() {
        // 1. 모든 종목 조회 (쿼리 1개)
        List<Stock> stocks = stockRepository.findAll();

        if (stocks.isEmpty()) {
            return List.of();
        }

        // 2. 모든 종목의 ID 수집
        List<Long> stockIds = stocks.stream()
                .map(Stock::getId)
                .collect(Collectors.toList());

        // 3. 최근 1년간의 리포트를 한 번에 조회 (쿼리 1개)
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        List<Report> allReports = reportRepository.findAll().stream()
                .filter(report -> stockIds.contains(report.getStock().getId()))
                .filter(report -> report.getReportDate().isAfter(oneYearAgo))
                .sorted(Comparator.comparing(Report::getReportDate).reversed())
                .collect(Collectors.toList());

        // 4. 모든 종목의 최신 종가를 한 번에 조회 (쿼리 1개)
        List<ClosePrice> allClosePrices = closePriceRepository
                .findByStockIdInOrderByStockIdAscTradeDateDesc(stockIds);

        // 5. 리포트를 종목별로 그룹핑 (메모리 연산)
        Map<Long, List<Report>> reportsByStock = allReports.stream()
                .collect(Collectors.groupingBy(report -> report.getStock().getId()));

        // 6. 종가를 종목별로 그룹핑하여 최신 종가만 저장 (메모리 연산)
        Map<Long, Integer> latestClosePriceByStock = new HashMap<>();
        for (ClosePrice closePrice : allClosePrices) {
            Long stockId = closePrice.getStock().getId();
            if (!latestClosePriceByStock.containsKey(stockId)) {
                latestClosePriceByStock.put(stockId, closePrice.getClosePrice());
            }
        }

        // 7. 각 종목의 상승여력과 매수비율 계산 (메모리 연산)
        return stocks.stream()
                .map(stock -> {
                    List<Report> stockReports = reportsByStock.get(stock.getId());
                    Integer latestClosePrice = latestClosePriceByStock.get(stock.getId());

                    return calculateStockRankingInfo(stock, stockReports, latestClosePrice);
                })
                .collect(Collectors.toList());
    }

    /**
     * 종목의 랭킹 정보 계산 (상승여력, 매수비율)
     */
    private StockListDTO calculateStockRankingInfo(Stock stock, List<Report> stockReports, Integer latestClosePrice) {
        Double upsidePotential = null;
        Double buyRatio = null;

        if (stockReports != null && !stockReports.isEmpty()) {
            try {
                // 애널리스트별로 최신 리포트만 선택
                Map<Long, Report> latestReportByAnalyst = new HashMap<>();
                for (Report report : stockReports) {
                    Long analystId = report.getAnalyst().getId();
                    if (!latestReportByAnalyst.containsKey(analystId)) {
                        latestReportByAnalyst.put(analystId, report);
                    }
                }

                List<Report> validReports = new ArrayList<>(latestReportByAnalyst.values());

                // surfaceOpinion 별 개수 계산 (매수비율용)
                int buyCount = (int) validReports.stream()
                        .filter(report -> report.getSurfaceOpinion() != null)
                        .filter(report -> report.getSurfaceOpinion() == SurfaceOpinion.BUY)
                        .count();

                int holdCount = (int) validReports.stream()
                        .filter(report -> report.getSurfaceOpinion() != null)
                        .filter(report -> report.getSurfaceOpinion() == SurfaceOpinion.HOLD)
                        .count();

                int sellCount = (int) validReports.stream()
                        .filter(report -> report.getSurfaceOpinion() != null)
                        .filter(report -> report.getSurfaceOpinion() == SurfaceOpinion.SELL)
                        .count();

                int totalOpinions = buyCount + holdCount + sellCount;

                // 매수 비율 계산 (소수점 한자리)
                if (totalOpinions > 0) {
                    buyRatio = Math.round((double) buyCount / totalOpinions * 1000.0) / 10.0;
                }

                // 평균 목표가 계산
                Double averageTargetPrice = validReports.stream()
                        .map(Report::getTargetPrice)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0.0);

                // 상승 여력 계산 (소수점 한자리)
                if (latestClosePrice != null && latestClosePrice > 0 && averageTargetPrice > 0) {
                    upsidePotential = ((averageTargetPrice - latestClosePrice) / latestClosePrice) * 100;
                    upsidePotential = Math.round(upsidePotential * 10.0) / 10.0;
                }
            } catch (Exception e) {
                // 계산 오류 시 null 유지
            }
        }

        return StockListDTO.builder()
                .id(stock.getId())
                .stockName(stock.getStockName())
                .stockCode(stock.getStockCode())
                .sector(stock.getSector())
                .upsidePotential(upsidePotential)
                .buyRatio(buyRatio)
                .build();
    }

    //code는 크롤링 데이터용 식별자 -> 크롤링 시 코드 기준으로 리포트 검색.
    public Optional<Stock> getStockByCode(String stockCode){
        return stockRepository.findByStockCode(stockCode);
    }


    //id는 내부 식별자
    public Optional<Stock> getStockById(Long id){
        return stockRepository.findById(id);
    }

    // 종목 ID로 조회 + 종합 의견 포함
    public Optional<StockConsensusDTO> getStockConsensusById(Long id){
        try {
            return Optional.of(reportService.getStockConsensus(id));
        } catch (RuntimeException e) {
            // 리포트가 없거나 에러 발생 시 null 반환
            return Optional.empty();
        }
    }

    //키워드로 종목 검색 (종목명 또는 종목코드에서 검색)
    public List<Stock> searchStocksByKeyword(String keyword){
        if(keyword == null || keyword.trim().isEmpty()){
            return List.of(); //빈 리스트 반환
        }
        return stockRepository.findByStockNameContainingIgnoreCaseOrStockCodeContaining(keyword, keyword);
    }

    //종목명으로만 검색
    public List<Stock> searchStocksByName(String name){
        if(name == null || name.trim().isEmpty()){
            return List.of();
        }
        return stockRepository.findByStockNameContainingIgnoreCase(name);
    }

    //업종으로 필터링
    public List<Stock> filterStocksBySector(String sector){
        if(sector == null || sector.trim().isEmpty()){
            return List.of();
        }
        return stockRepository.findBySector(sector);
    }

    // 종가 변동 추이 조회 (최근 5년)
    @Transactional(readOnly = true)
    public List<ClosePriceTrendDTO> getClosePriceTrend(Long stockId) {
        LocalDate fiveYearsAgo = LocalDate.now().minusYears(5);
        List<ClosePrice> closePrices = closePriceRepository
                .findByStockIdAndTradeDateAfterOrderByTradeDateDesc(stockId, fiveYearsAgo);

        return closePrices.stream()
                .map(cp -> ClosePriceTrendDTO.builder()
                        .tradeDate(cp.getTradeDate())
                        .closePrice(cp.getClosePrice())
                        .build())
                .collect(Collectors.toList());
    }

    // 날짜별 애널리스트 평균 목표주가 계산 (최근 2년간 매일 데이터, Forward Fill 방식)
    @Transactional(readOnly = true)
    public List<DailyAverageTargetPriceDTO> getDailyAverageTargetPrices(Long stockId) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusYears(2);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        // 2년간의 모든 리포트 조회
        List<Report> validReports = reportRepository
                .findByStockIdAndReportDateAfterOrderByReportDateDesc(stockId, startDateTime);

        if (validReports.isEmpty()) {
            return List.of();
        }

        List<DailyAverageTargetPriceDTO> result = new ArrayList<>();
        Double previousAverage = null;

        // 2년간의 모든 날짜를 순회
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            final LocalDate currentDate = date;

            // 해당 날짜 이전에 발행된 리포트들만 필터링
            List<Report> reportsUntilDate = validReports.stream()
                    .filter(report -> !report.getReportDate().toLocalDate().isAfter(currentDate))
                    .collect(Collectors.toList());

            if (!reportsUntilDate.isEmpty()) {
                // 애널리스트별 가장 최근 리포트만 선택
                Map<Long, Report> latestReportByAnalyst = reportsUntilDate.stream()
                        .collect(Collectors.toMap(
                                report -> report.getAnalyst().getId(),
                                report -> report,
                                (r1, r2) -> r1.getReportDate().isAfter(r2.getReportDate()) ? r1 : r2
                        ));

                // 평균 목표가 계산
                Double averageTargetPrice = latestReportByAnalyst.values().stream()
                        .map(Report::getTargetPrice)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(previousAverage != null ? previousAverage : 0.0);

                previousAverage = averageTargetPrice;

                result.add(DailyAverageTargetPriceDTO.builder()
                        .date(currentDate)
                        .averageTargetPrice(averageTargetPrice)
                        .build());
            } else if (previousAverage != null) {
                // Forward Fill: 리포트가 없으면 이전 평균값 유지
                result.add(DailyAverageTargetPriceDTO.builder()
                        .date(currentDate)
                        .averageTargetPrice(previousAverage)
                        .build());
            }
        }

        return result;
    }

    // 현재 기준 목표가 통계 (최대/평균/최소, 오늘 기준 1년 미만 리포트)
    @Transactional(readOnly = true)
    public TargetPriceStatsDTO getTargetPriceStats(Long stockId) {
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        List<Report> validReports = reportRepository
                .findByStockIdAndReportDateAfterOrderByReportDateDesc(stockId, oneYearAgo);

        if (validReports.isEmpty()) {
            return null;
        }

        // 애널리스트별로 최신 리포트만 선택
        Map<Long, Report> latestReportByAnalyst = new HashMap<>();
        for (Report report : validReports) {
            Long analystId = report.getAnalyst().getId();
            if (!latestReportByAnalyst.containsKey(analystId)) {
                latestReportByAnalyst.put(analystId, report);
            }
        }

        // 목표가 리스트 추출
        List<Integer> targetPrices = latestReportByAnalyst.values().stream()
                .map(Report::getTargetPrice)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (targetPrices.isEmpty()) {
            return null;
        }

        // 최대/평균/최소 계산
        Integer maxTargetPrice = targetPrices.stream().max(Integer::compareTo).orElse(null);
        Integer minTargetPrice = targetPrices.stream().min(Integer::compareTo).orElse(null);
        Double averageTargetPrice = targetPrices.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        return TargetPriceStatsDTO.builder()
                .maxTargetPrice(maxTargetPrice)
                .averageTargetPrice(averageTargetPrice)
                .minTargetPrice(minTargetPrice)
                .build();
    }

    // 해당 종목을 커버하는 애널리스트 목록 (오늘 기준 1년 미만 리포트, 지표 포함)
    @Transactional(readOnly = true)
    public List<CoveringAnalystDTO> getCoveringAnalysts(Long stockId) {
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        List<Report> validReports = reportRepository
                .findByStockIdAndReportDateAfterOrderByReportDateDesc(stockId, oneYearAgo);

        if (validReports.isEmpty()) {
            return List.of();
        }

        // 애널리스트별로 최신 리포트만 선택
        Map<Long, Report> latestReportByAnalyst = new HashMap<>();
        for (Report report : validReports) {
            Long analystId = report.getAnalyst().getId();
            if (!latestReportByAnalyst.containsKey(analystId)) {
                latestReportByAnalyst.put(analystId, report);
            }
        }

        // 모든 애널리스트의 지표를 한번에 조회
        List<Long> analystIds = new ArrayList<>(latestReportByAnalyst.keySet());
        Map<Long, AnalystMetrics> metricsMap = analystMetricsRepository.findAll().stream()
                .filter(m -> analystIds.contains(m.getAnalyst().getId()))
                .collect(Collectors.toMap(m -> m.getAnalyst().getId(), m -> m));

        // 전체 애널리스트 순위 계산 (aimsScore 기준 내림차순)
        List<AnalystMetrics> allMetrics = analystMetricsRepository.findAll();
        Map<Long, Integer> rankMap = new HashMap<>();
        int totalAnalysts = allMetrics.size();

        List<AnalystMetrics> sortedByScore = allMetrics.stream()
                .filter(m -> m.getAimsScore() != null)
                .sorted(Comparator.comparing(AnalystMetrics::getAimsScore).reversed())
                .toList();

        for (int i = 0; i < sortedByScore.size(); i++) {
            rankMap.put(sortedByScore.get(i).getAnalyst().getId(), i + 1);
        }

        // CoveringAnalystDTO 리스트로 변환 (지표 포함)
        return latestReportByAnalyst.values().stream()
                .map(report -> {
                    Long analystId = report.getAnalyst().getId();
                    AnalystMetrics metrics = metricsMap.get(analystId);

                    // hiddenOpinion을 라벨로 변환
                    HiddenOpinionLabel hiddenLabel = HiddenOpinionLabel.fromScore(report.getHiddenOpinion());

                    // 직전 리포트 대비 목표주가 차이 계산
                    Integer targetPriceDiff = null;
                    if (report.getPrevReport() != null && report.getTargetPrice() != null
                            && report.getPrevReport().getTargetPrice() != null) {
                        targetPriceDiff = report.getTargetPrice() - report.getPrevReport().getTargetPrice();
                    }

                    return CoveringAnalystDTO.builder()
                            .analystId(analystId)
                            .analystName(report.getAnalyst().getAnalystName())
                            .firmName(report.getAnalyst().getFirmName())
                            .latestTargetPrice(report.getTargetPrice())
                            .latestReportDate(report.getReportDate().toLocalDate())
                            .latestOpinion(report.getSurfaceOpinion() != null
                                    ? report.getSurfaceOpinion().toString()
                                    : null)
                            .hiddenOpinion(hiddenLabel != null ? hiddenLabel.toString() : null)
                            .targetPriceDiff(targetPriceDiff)
                            // 지표 추가 (없으면 null)
                            .accuracyRate(metrics != null ? metrics.getAccuracyRate() : null)
                            .returnRate(metrics != null ? metrics.getReturnRate() : null)
                            .targetDiffRate(metrics != null ? metrics.getTargetDiffRate() : null)
                            .avgReturnDiff(metrics != null ? metrics.getAvgReturnDiff() : null)
                            .avgTargetDiff(metrics != null ? metrics.getAvgTargetDiff() : null)
                            .aimsScore(metrics != null ? metrics.getAimsScore() : null)
                            .rank(rankMap.get(analystId))  // 전체 순위
                            .totalAnalysts(totalAnalysts)   // 전체 애널리스트 수
                            .build();
                })
                .sorted(Comparator.comparing(CoveringAnalystDTO::getLatestReportDate).reversed())
                .collect(Collectors.toList());
    }
}

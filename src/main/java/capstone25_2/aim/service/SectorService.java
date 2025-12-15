package capstone25_2.aim.service;

import capstone25_2.aim.domain.dto.sector.SectorListDTO;
import capstone25_2.aim.domain.dto.sector.SectorResponseDTO;
import capstone25_2.aim.domain.dto.sector.SectorStockDTO;
import capstone25_2.aim.domain.entity.ClosePrice;
import capstone25_2.aim.domain.entity.HiddenOpinionLabel;
import capstone25_2.aim.domain.entity.Report;
import capstone25_2.aim.domain.entity.Stock;
import capstone25_2.aim.repository.ClosePriceRepository;
import capstone25_2.aim.repository.ReportRepository;
import capstone25_2.aim.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SectorService {

    private final StockRepository stockRepository;
    private final ReportRepository reportRepository;
    private final ClosePriceRepository closePriceRepository;

    /**
     * 모든 섹터 리스트 조회
     * 각 섹터의 의견 분포와 매수 비율 제공
     *
     * 쿼리 최적화: 전체 2개 쿼리로 처리
     * 1) 모든 종목 조회
     * 2) 모든 종목의 최근 5년 리포트 한 번에 조회
     */
    @Transactional(readOnly = true)
    public List<SectorListDTO> getAllSectors() {
        // 1. 모든 종목 조회 (쿼리 1개)
        List<Stock> allStocks = stockRepository.findAll();

        if (allStocks.isEmpty()) {
            return List.of();
        }

        // 2. 모든 종목의 ID 수집
        List<Long> stockIds = allStocks.stream()
                .map(Stock::getId)
                .collect(Collectors.toList());

        // 3. 모든 종목의 최근 5년 리포트를 한 번에 조회 (쿼리 1개)
        LocalDateTime fiveYearsAgo = LocalDateTime.now().minusYears(5);
        List<Report> allReports = reportRepository.findByStockIdInAndReportDateAfterOrderByReportDateDesc(
                stockIds, fiveYearsAgo);

        // 4. 리포트를 종목별로 그룹핑 (메모리 연산)
        Map<Long, List<Report>> reportsByStock = allReports.stream()
                .collect(Collectors.groupingBy(report -> report.getStock().getId()));

        // 5. 각 종목의 다수결 의견 계산 (메모리 연산)
        Map<Long, HiddenOpinionLabel> stockOpinions = new HashMap<>();
        for (Stock stock : allStocks) {
            List<Report> stockReports = reportsByStock.get(stock.getId());
            if (stockReports != null && !stockReports.isEmpty()) {
                HiddenOpinionLabel opinion = calculateStockMajorityOpinion(stockReports);
                if (opinion != null) {
                    stockOpinions.put(stock.getId(), opinion);
                }
            }
        }

        // 6. 섹터별로 종목 그룹핑 (메모리 연산)
        Map<String, List<Stock>> stocksBySector = allStocks.stream()
                .filter(stock -> stock.getSector() != null && !stock.getSector().trim().isEmpty())
                .collect(Collectors.groupingBy(Stock::getSector));

        // 7. 각 섹터의 통계 계산 (메모리 연산)
        return stocksBySector.entrySet().stream()
                .map(entry -> {
                    String sectorName = entry.getKey();
                    List<Stock> stocks = entry.getValue();
                    return calculateSectorStats(sectorName, stocks, stockOpinions);
                })
                .sorted(Comparator.comparing(SectorListDTO::getSectorName))
                .collect(Collectors.toList());
    }

    /**
     * 특정 섹터 상세 조회
     * 섹터 정보 + 섹터 내 종목 리스트 (상승여력, 매수비율 포함)
     *
     * 쿼리 최적화: 전체 3개 쿼리로 처리
     * 1) 해당 섹터의 종목 조회
     * 2) 해당 섹터 종목들의 최근 5년 리포트 한 번에 조회
     * 3) 해당 섹터 종목들의 최신 종가 한 번에 조회
     */
    @Transactional(readOnly = true)
    public SectorResponseDTO getSectorDetails(String sectorName) {
        // 1. 해당 섹터의 모든 종목 조회 (쿼리 1개)
        List<Stock> stocks = stockRepository.findBySector(sectorName);

        if (stocks.isEmpty()) {
            throw new RuntimeException("Sector not found or has no stocks");
        }

        // 2. 모든 종목의 ID 수집
        List<Long> stockIds = stocks.stream()
                .map(Stock::getId)
                .collect(Collectors.toList());

        // 3. 모든 종목의 최근 5년 리포트를 한 번에 조회 (쿼리 1개)
        LocalDateTime fiveYearsAgo = LocalDateTime.now().minusYears(5);
        List<Report> allReports = reportRepository.findByStockIdInAndReportDateAfterOrderByReportDateDesc(
                stockIds, fiveYearsAgo);

        // 4. 모든 종목의 종가를 한 번에 조회 (쿼리 1개)
        List<ClosePrice> allClosePrices = closePriceRepository.findByStockIdInOrderByStockIdAscTradeDateDesc(stockIds);

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

        // 7. 각 종목의 다수결 의견 계산 (메모리 연산)
        Map<Long, HiddenOpinionLabel> stockOpinions = new HashMap<>();
        for (Stock stock : stocks) {
            List<Report> stockReports = reportsByStock.get(stock.getId());
            if (stockReports != null && !stockReports.isEmpty()) {
                HiddenOpinionLabel opinion = calculateStockMajorityOpinion(stockReports);
                if (opinion != null) {
                    stockOpinions.put(stock.getId(), opinion);
                }
            }
        }

        // 8. 섹터 통계 계산 (메모리 연산)
        SectorListDTO sectorStats = calculateSectorStats(sectorName, stocks, stockOpinions);

        // 9. 각 종목의 상세 정보 계산 (메모리 연산)
        List<SectorStockDTO> stockDTOs = stocks.stream()
                .map(stock -> {
                    List<Report> stockReports = reportsByStock.get(stock.getId());
                    Integer latestClosePrice = latestClosePriceByStock.get(stock.getId());
                    HiddenOpinionLabel opinion = stockOpinions.get(stock.getId());

                    return calculateStockStats(stock, stockReports, latestClosePrice, opinion);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // 10. SectorResponseDTO 생성
        return SectorResponseDTO.builder()
                .sectorName(sectorStats.getSectorName())
                .stockCount(sectorStats.getStockCount())
                .buyRatio(sectorStats.getBuyRatio())
                .strongBuyCount(sectorStats.getStrongBuyCount())
                .buyCount(sectorStats.getBuyCount())
                .holdCount(sectorStats.getHoldCount())
                .sellCount(sectorStats.getSellCount())
                .strongSellCount(sectorStats.getStrongSellCount())
                .stocks(stockDTOs)
                .build();
    }

    /**
     * 섹터 통계 계산 (5단계 의견 집계)
     * 각 종목의 다수결 의견을 집계하여 섹터 전체 의견 분포 계산
     *
     * @param sectorName 섹터명
     * @param stocks 섹터 내 종목 리스트
     * @param stockOpinions 각 종목의 다수결 의견 맵 (stockId -> HiddenOpinionLabel)
     */
    private SectorListDTO calculateSectorStats(String sectorName, List<Stock> stocks,
                                                Map<Long, HiddenOpinionLabel> stockOpinions) {
        int strongBuyCount = 0;
        int buyCount = 0;
        int holdCount = 0;
        int sellCount = 0;
        int strongSellCount = 0;

        // 각 종목의 다수결 의견 집계
        for (Stock stock : stocks) {
            HiddenOpinionLabel opinion = stockOpinions.get(stock.getId());
            if (opinion != null) {
                switch (opinion) {
                    case STRONG_BUY -> strongBuyCount++;
                    case BUY -> buyCount++;
                    case HOLD -> holdCount++;
                    case SELL -> sellCount++;
                    case STRONG_SELL -> strongSellCount++;
                }
            }
        }

        // 섹터 매수 비율 계산 (소수점 첫째자리까지)
        int totalStocksWithOpinion = strongBuyCount + buyCount + holdCount + sellCount + strongSellCount;
        Double buyRatio = null;
        if (totalStocksWithOpinion > 0) {
            buyRatio = Math.round((double) (strongBuyCount + buyCount) / totalStocksWithOpinion * 1000.0) / 10.0;
        }

        return SectorListDTO.builder()
                .sectorName(sectorName)
                .stockCount(stocks.size())
                .buyRatio(buyRatio)
                .strongBuyCount(strongBuyCount)
                .buyCount(buyCount)
                .holdCount(holdCount)
                .sellCount(sellCount)
                .strongSellCount(strongSellCount)
                .build();
    }

    /**
     * 종목의 다수결 의견 계산 (5단계)
     * 각 애널리스트의 의견 변화 이후 최신 리포트들의 hiddenOpinion을 5단계로 변환하여 다수결 적용
     *
     * 로직:
     * - 애널리스트별로 그룹핑
     * - 각 애널리스트의 최신 리포트 선택 (1년 이내 리포트만)
     * - hiddenOpinion을 5단계로 변환
     * - 가장 많은 의견을 다수결로 선택 (동점 시 보수적 의견 우선: HOLD > SELL > STRONG_SELL > BUY > STRONG_BUY)
     * - BUY인 경우 매수 비율(BUY+STRONG_BUY)에 따라 조정:
     *   · 80% 이상 → STRONG_BUY로 업그레이드
     *   · 40% 이하 → HOLD로 다운그레이드
     *   · 그 외 → BUY 유지
     *
     * @param stockReports 종목의 최근 5년 리포트 리스트
     * @return 다수결 의견 (HiddenOpinionLabel)
     */
    private HiddenOpinionLabel calculateStockMajorityOpinion(List<Report> stockReports) {
        if (stockReports == null || stockReports.isEmpty()) {
            return null;
        }

        // 1. 애널리스트별로 그룹핑
        Map<Long, List<Report>> reportsByAnalyst = stockReports.stream()
                .collect(Collectors.groupingBy(report -> report.getAnalyst().getId()));

        // 2. 각 애널리스트의 최신 리포트만 선택 (1년 이내 리포트만)
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        List<Report> latestReportsByAnalyst = new ArrayList<>();
        for (List<Report> analystReports : reportsByAnalyst.values()) {
            // 이미 날짜 내림차순으로 정렬되어 있으므로 첫 번째가 최신
            if (!analystReports.isEmpty()) {
                Report latestReport = analystReports.get(0);
                // 최신 리포트가 1년 이내인 경우만 포함
                if (latestReport.getReportDate().isAfter(oneYearAgo)) {
                    latestReportsByAnalyst.add(latestReport);
                }
            }
        }

        // 3. hiddenOpinion을 5단계로 변환하여 개수 집계
        Map<HiddenOpinionLabel, Long> opinionCounts = latestReportsByAnalyst.stream()
                .map(report -> HiddenOpinionLabel.fromScore(report.getHiddenOpinion()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(label -> label, Collectors.counting()));

        if (opinionCounts.isEmpty()) {
            return null;
        }

        // 4. 가장 많은 의견 반환 (다수결, 동점 시 보수적 의견 우선)
        long maxCount = opinionCounts.values().stream().mapToLong(Long::longValue).max().orElse(0L);

        // 동점인 의견들을 보수적 우선순위로 정렬하여 선택
        // 우선순위: HOLD > SELL > STRONG_SELL > BUY > STRONG_BUY
        HiddenOpinionLabel majorityOpinion = opinionCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .min(Comparator.comparingInt(label -> {
                    switch (label) {
                        case HOLD: return 0;
                        case SELL: return 1;
                        case STRONG_SELL: return 2;
                        case BUY: return 3;
                        case STRONG_BUY: return 4;
                        default: return 5;
                    }
                }))
                .orElse(null);

        // 5. BUY인 경우, 매수 비율에 따라 조정
        if (majorityOpinion == HiddenOpinionLabel.BUY) {
            long buyCount = opinionCounts.getOrDefault(HiddenOpinionLabel.BUY, 0L);
            long strongBuyCount = opinionCounts.getOrDefault(HiddenOpinionLabel.STRONG_BUY, 0L);
            long totalCount = opinionCounts.values().stream().mapToLong(Long::longValue).sum();

            double buyRatio = (double) (buyCount + strongBuyCount) / totalCount;

            // 매수 비율 80% 이상 -> STRONG_BUY로 업그레이드
            if (buyRatio >= 0.8) {
                return HiddenOpinionLabel.STRONG_BUY;
            }
            // 매수 비율 40% 이하 -> HOLD로 다운그레이드
            else if (buyRatio <= 0.4) {
                return HiddenOpinionLabel.HOLD;
            }
        }

        return majorityOpinion;
    }

    /**
     * 종목의 통계 정보 계산
     * 상승여력, 매수비율 계산
     *
     * @param stock 종목 엔티티
     * @param stockReports 종목의 최근 5년 리포트 리스트
     * @param latestClosePrice 최신 종가
     * @param opinion 종목의 다수결 의견
     * @return SectorStockDTO
     */
    private SectorStockDTO calculateStockStats(Stock stock, List<Report> stockReports,
                                                Integer latestClosePrice, HiddenOpinionLabel opinion) {
        if (stockReports == null || stockReports.isEmpty()) {
            return SectorStockDTO.builder()
                    .stockId(stock.getId())
                    .stockName(stock.getStockName())
                    .stockCode(stock.getStockCode())
                    .upsidePotential(null)
                    .buyRatio(null)
                    .latestOpinion(opinion)
                    .build();
        }

        // 1. 애널리스트별로 그룹핑
        Map<Long, List<Report>> reportsByAnalyst = stockReports.stream()
                .collect(Collectors.groupingBy(report -> report.getAnalyst().getId()));

        // 2. 각 애널리스트의 최신 리포트만 선택 (1년 이내 리포트만)
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        List<Report> latestReportsByAnalyst = new ArrayList<>();
        for (List<Report> analystReports : reportsByAnalyst.values()) {
            if (!analystReports.isEmpty()) {
                Report latestReport = analystReports.get(0);
                // 최신 리포트가 1년 이내인 경우만 포함
                if (latestReport.getReportDate().isAfter(oneYearAgo)) {
                    latestReportsByAnalyst.add(latestReport);
                }
            }
        }

        // 3. hiddenOpinion 별 개수 계산 (매수비율용)
        int buyCount = (int) latestReportsByAnalyst.stream()
                .filter(report -> report.getHiddenOpinion() != null)
                .filter(report -> {
                    String category = HiddenOpinionLabel.toSimpleCategory(report.getHiddenOpinion());
                    return "BUY".equals(category);
                })
                .count();

        int totalOpinions = (int) latestReportsByAnalyst.stream()
                .filter(report -> report.getHiddenOpinion() != null)
                .count();

        // 4. 매수 비율 계산 (소수점 첫째자리)
        Double buyRatio = null;
        if (totalOpinions > 0) {
            buyRatio = Math.round((double) buyCount / totalOpinions * 1000.0) / 10.0;
        }

        // 5. AIM's 평균 목표가 계산 (BUY: 실제 목표가, HOLD: 발행일 종가, SELL: 발행일 종가 × 0.8)
        Double aimsAverageTargetPrice = latestReportsByAnalyst.stream()
                .filter(report -> report.getHiddenOpinion() != null)
                .mapToDouble(report -> {
                    String category = HiddenOpinionLabel.toSimpleCategory(report.getHiddenOpinion());
                    // BUY는 실제 목표가 사용
                    if ("BUY".equals(category)) {
                        return report.getTargetPrice() != null ? report.getTargetPrice() : 0.0;
                    }
                    // HOLD는 발행일 종가 사용 (변화 없음을 의미)
                    else if ("HOLD".equals(category)) {
                        java.time.LocalDate reportDate = report.getReportDate().toLocalDate();
                        java.util.Optional<ClosePrice> reportClosePrice = closePriceRepository
                                .findFirstByStockIdAndTradeDateLessThanEqualOrderByTradeDateDesc(
                                        stock.getId(), reportDate);
                        if (reportClosePrice.isPresent()) {
                            return reportClosePrice.get().getClosePrice().doubleValue();
                        }
                    }
                    // SELL은 발행일 종가 × 0.8
                    else if ("SELL".equals(category)) {
                        java.time.LocalDate reportDate = report.getReportDate().toLocalDate();
                        java.util.Optional<ClosePrice> reportClosePrice = closePriceRepository
                                .findFirstByStockIdAndTradeDateLessThanEqualOrderByTradeDateDesc(
                                        stock.getId(), reportDate);
                        if (reportClosePrice.isPresent()) {
                            return reportClosePrice.get().getClosePrice() * 0.8;
                        }
                    }
                    return 0.0;
                })
                .filter(price -> price > 0)
                .average()
                .orElse(0.0);

        // 6. 상승 여력 계산 (AIM's 평균 목표가 기준, 소수점 첫째자리)
        Double upsidePotential = null;
        if (latestClosePrice != null && latestClosePrice > 0 && aimsAverageTargetPrice > 0) {
            upsidePotential = ((aimsAverageTargetPrice - latestClosePrice) / latestClosePrice) * 100;
            upsidePotential = Math.round(upsidePotential * 10.0) / 10.0;
        }

        return SectorStockDTO.builder()
                .stockId(stock.getId())
                .stockName(stock.getStockName())
                .stockCode(stock.getStockCode())
                .upsidePotential(upsidePotential)
                .buyRatio(buyRatio)
                .latestOpinion(opinion)
                .build();
    }
}

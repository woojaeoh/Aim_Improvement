package capstone25_2.aim.service;

import capstone25_2.aim.domain.dto.home.*;
import capstone25_2.aim.domain.dto.sector.SectorListDTO;
import capstone25_2.aim.domain.dto.stock.StockListDTO;
import capstone25_2.aim.domain.entity.Analyst;
import capstone25_2.aim.domain.entity.AnalystMetrics;
import capstone25_2.aim.repository.AnalystMetricsRepository;
import capstone25_2.aim.repository.AnalystRepository;
import capstone25_2.aim.repository.SearchLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private final AnalystMetricsRepository analystMetricsRepository;
    private final AnalystRepository analystRepository;
    private final StockService stockService;
    private final SectorService sectorService;
    private final SearchLogRepository searchLogRepository;

    /**
     * 홈 화면 데이터 조회
     * - TOP 3 신뢰도 애널리스트
     * - TOP 3 상승여력 종목
     * - TOP 3 매수 섹터
     * - TOP 3 검색량 애널리스트 (최근 7일)
     */
    public HomeResponseDTO getHomeData() {
        List<TopAnalystDTO> topAnalysts = getTopAnalysts();
        List<TopStockDTO> topStocks = getTopStocks();
        List<TopSectorDTO> topSectors = getTopSectors();
        List<TrendingAnalystDTO> trendingAnalysts = getTrendingAnalysts();

        return HomeResponseDTO.builder()
                .topAnalysts(topAnalysts)
                .topStocks(topStocks)
                .topSectors(topSectors)
                .trendingAnalysts(trendingAnalysts)
                .build();
    }

    /**
     * TOP 3 신뢰도 애널리스트 (aimsScore 기준)
     */
    private List<TopAnalystDTO> getTopAnalysts() {
        List<AnalystMetrics> allMetrics = analystMetricsRepository.findAll();

        return allMetrics.stream()
                .filter(metrics -> metrics.getAimsScore() != null)
                .sorted(Comparator.comparing(AnalystMetrics::getAimsScore).reversed())
                .limit(3)
                .map(metrics -> TopAnalystDTO.builder()
                        .analystId(metrics.getAnalyst().getId())
                        .analystName(metrics.getAnalyst().getAnalystName())
                        .firmName(metrics.getAnalyst().getFirmName())
                        .accuracyRate(metrics.getAccuracyRate())
                        .returnRate(metrics.getReturnRate())
                        .aimsScore(metrics.getAimsScore())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * TOP 3 상승여력 종목
     */
    private List<TopStockDTO> getTopStocks() {
        List<StockListDTO> allStocks = stockService.getAllStocksWithRankingInfo();

        return allStocks.stream()
                .filter(stock -> stock.getUpsidePotential() != null)
                .sorted(Comparator.comparing(StockListDTO::getUpsidePotential).reversed())
                .limit(3)
                .map(stock -> TopStockDTO.builder()
                        .stockId(stock.getId())
                        .stockName(stock.getStockName())
                        .stockCode(stock.getStockCode())
                        .upsidePotential(stock.getUpsidePotential())
                        .buyRatio(stock.getBuyRatio())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * TOP 3 매수 섹터 (Strong Buy + Buy 비율 기준)
     */
    private List<TopSectorDTO> getTopSectors() {
        List<SectorListDTO> allSectors = sectorService.getAllSectors();

        return allSectors.stream()
                .filter(sector -> sector.getBuyRatio() != null)
                .sorted(Comparator.comparing(SectorListDTO::getBuyRatio).reversed())
                .limit(3)
                .map(sector -> TopSectorDTO.builder()
                        .sectorName(sector.getSectorName())
                        .buyRatio(sector.getBuyRatio())
                        .strongBuyCount(sector.getStrongBuyCount())
                        .buyCount(sector.getBuyCount())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * TOP 3 검색량 애널리스트 (최근 7일)
     */
    private List<TrendingAnalystDTO> getTrendingAnalysts() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<Object[]> results = searchLogRepository.findTop3TrendingAnalysts(weekAgo);

        if (results.isEmpty()) {
            return List.of();
        }

        return results.stream()
                .limit(3)
                .map(result -> {
                    Long analystId = (Long) result[0];
                    Long searchCount = (Long) result[1];

                    Analyst analyst = analystRepository.findById(analystId)
                            .orElse(null);

                    if (analyst == null) {
                        return null;
                    }

                    return TrendingAnalystDTO.builder()
                            .analystId(analyst.getId())
                            .analystName(analyst.getAnalystName())
                            .firmName(analyst.getFirmName())
                            .searchCount(searchCount)
                            .build();
                })
                .filter(dto -> dto != null)
                .collect(Collectors.toList());
    }
}

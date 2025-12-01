package capstone25_2.aim.controller;

import capstone25_2.aim.domain.dto.stock.*;
import capstone25_2.aim.domain.entity.Stock;
import capstone25_2.aim.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;
    // 전체 종목 조회 (상승여력, 매수 비율 포함)
    @GetMapping
    @Operation(
            summary = "종목 랭킹 페이지"
    )
    public List<StockListDTO> getAllStocks() {
        return stockService.getAllStocksWithRankingInfo();
    }

    // 종목 ID로 조회 (종합 의견, 종가 변동 추이, 날짜별 평균 목표주가, 목표가 통계, 커버 애널리스트 포함)
    @GetMapping("/{stockId}")
    @Operation(
            summary = "종목 상세 페이지"
    )
    public StockResponseDTO getStockById(@PathVariable Long stockId) {
        Stock stock = stockService.getStockById(stockId)
                .orElseThrow(() -> new RuntimeException("Stock not found"));

        // 종합 의견 조회 (리포트가 없으면 null)
        Optional<StockConsensusDTO> consensus = stockService.getStockConsensusById(stockId);

        // 종가 변동 추이 조회
        List<ClosePriceTrendDTO> closePriceTrend = stockService.getClosePriceTrend(stockId);

        // 날짜별 애널리스트 평균 목표주가 조회
        List<DailyAverageTargetPriceDTO> dailyAverageTargetPrices =
                stockService.getDailyAverageTargetPrices(stockId);

        // 현재 기준 목표가 통계 조회 (최대/평균/최소)
        TargetPriceStatsDTO targetPriceStats = stockService.getTargetPriceStats(stockId);

        // 해당 종목을 커버하는 애널리스트 목록 조회
        List<CoveringAnalystDTO> coveringAnalysts = stockService.getCoveringAnalysts(stockId);

        // 모든 정보 포함하여 반환
        return StockResponseDTO.fromEntityWithFullDetails(
                stock,
                consensus.orElse(null),
                closePriceTrend,
                dailyAverageTargetPrices,
                targetPriceStats,
                coveringAnalysts
        );
    }

//    // 종목 코드로 조회 (예: /stocks/code/005930)
//    @GetMapping("/code/{stockCode}")
//    public StockResponseDTO getStockByCode(@PathVariable String stockCode) {
//        Stock stock = stockService.getStockByCode(stockCode)
//                .orElseThrow(() -> new RuntimeException("Stock not found"));
//        return StockResponseDTO.fromEntity(stock);
//    }

//    // 키워드로 종목 검색 (예: /stocks/search?keyword=삼성 또는 /stocks/search?keyword=005930)
//    // 종목명 또는 종목코드에서 검색 (부분 일치)
//    @GetMapping("/search")
//    public List<StockResponseDTO> searchStocks(@RequestParam(required = false) String keyword,
//                                                @RequestParam(required = false) String sector) {
//        List<Stock> stocks;
//
//        if (keyword != null && !keyword.trim().isEmpty()) {
//            // 키워드로 검색 (종목명 또는 종목코드)
//            stocks = stockService.searchStocksByKeyword(keyword);
//        } else if (sector != null && !sector.trim().isEmpty()) {
//            // 업종으로 필터링
//            stocks = stockService.filterStocksBySector(sector);
//        } else {
//            // 파라미터가 없으면 전체 목록 반환
//            stocks = stockService.getAllStocks();
//        }
//
//        return stocks.stream()
//                .map(StockResponseDTO::fromEntity)
//                .toList();
//    }
}

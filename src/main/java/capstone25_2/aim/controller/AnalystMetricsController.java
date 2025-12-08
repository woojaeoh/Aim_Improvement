package capstone25_2.aim.controller;


import capstone25_2.aim.domain.dto.analyst.AnalystRankingResponseDTO;
import capstone25_2.aim.service.AnalystMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/analysts/metrics")
@RequiredArgsConstructor
public class AnalystMetricsController {

    private final AnalystMetricsService metricsService;

//    // 전체 애널리스트 비교 지표 랭킹
//    // 예: /analysts/metrics?sortBy=returnRate
//    @GetMapping
//    public AnalystRankingResponseDTO getAnalystMetrics(
//            @RequestParam(defaultValue = "accuracyRate") String sortBy) {
//        return metricsService.getRankedAnalysts(sortBy);
//    }

    // 전체 애널리스트 랭킹
    @GetMapping
    @Operation(
            summary = "애널리스트 랭킹 페이지"
    )
    public AnalystRankingResponseDTO getAllMetrics(
            @RequestParam(defaultValue = "aimsScore") String sortBy) {
        return metricsService.getRankedAnalysts(sortBy);
    }

//    // 🔹 특정 종목 관련 애널리스트 랭킹
//    @GetMapping("/{stockId}")
//    public AnalystRankingResponseDTO getMetricsByStock(
//                    @PathVariable Long stockId,
//            @RequestParam(defaultValue = "accuracyRate") String sortBy) {
//        return metricsService.getRankedAnalystsByStock(stockId, sortBy);
//    }


}

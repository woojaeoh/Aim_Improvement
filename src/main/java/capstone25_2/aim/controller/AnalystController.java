package capstone25_2.aim.controller;

import capstone25_2.aim.domain.dto.analyst.AnalystMetricsDTO;
import capstone25_2.aim.domain.dto.analyst.AnalystReportSummaryDTO;
import capstone25_2.aim.domain.dto.analyst.AnalystResponseDTO;
import capstone25_2.aim.domain.dto.analyst.CoveredStockDTO;
import capstone25_2.aim.domain.entity.Analyst;
import capstone25_2.aim.service.AnalystService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analysts")
@RequiredArgsConstructor
public class AnalystController {

    private final AnalystService analystService;

    // 애널리스트 상세 조회 (지표 + 커버 종목 + 리포트 목록)
    @GetMapping("/{analystId}")
    @Operation(
            summary = "애널리스트 상세 페이지"
    )
    public AnalystResponseDTO getAnalystById(@PathVariable Long analystId) {
        Analyst analyst = analystService.getAnalystById(analystId)
                .orElseThrow(() -> new RuntimeException("Analyst not found"));

        // 애널리스트 지표 조회
        AnalystMetricsDTO metrics = analystService.getAnalystMetrics(analystId);

        // 커버 종목 리스트 조회
        List<CoveredStockDTO> coveredStocks = analystService.getCoveredStocks(analystId);

        // 리포트 목록 조회
        List<AnalystReportSummaryDTO> reports = analystService.getAnalystReports(analystId);

        // 모든 정보 포함하여 반환
        return AnalystResponseDTO.fromEntityWithFullDetails(
                analyst,
                metrics,
                coveredStocks,
                reports
        );
    }
}

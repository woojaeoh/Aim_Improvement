package capstone25_2.aim.controller;

import capstone25_2.aim.service.AnalystMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminMetricsController {

    private final AnalystMetricsService metricsService;

    @PostMapping("/metrics/calculate")
    @Operation(summary = "전체 애널리스트 지표 일괄 계산 (관리자 전용)")
    public ResponseEntity<String> calculateAllMetrics() {
        int count = metricsService.calculateAllAnalystMetricsWithCache();
        return ResponseEntity.ok("계산 완료: " + count + "명");
    }
}

package capstone25_2.aim.controller;

import capstone25_2.aim.service.AnalystMetricsService;
import capstone25_2.aim.service.DistributedLockService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminMetricsController {

    private static final String LOCK_KEY = "lock:metrics:calculate";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final AnalystMetricsService metricsService;
    private final DistributedLockService lockService;

    @PostMapping("/metrics/calculate")
    @Operation(summary = "전체 애널리스트 지표 일괄 계산 (관리자 전용)")
    public ResponseEntity<String> calculateAllMetrics() {
        String lockValue = UUID.randomUUID().toString();

        if (!lockService.tryAcquire(LOCK_KEY, lockValue, LOCK_TTL)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("이미 계산이 진행 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            int count = metricsService.calculateAllAnalystMetricsWithCache();
            return ResponseEntity.ok("계산 완료: " + count + "명");
        } finally {
            lockService.release(LOCK_KEY, lockValue);
        }
    }
}

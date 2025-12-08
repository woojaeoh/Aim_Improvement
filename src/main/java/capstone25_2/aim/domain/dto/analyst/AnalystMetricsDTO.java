package capstone25_2.aim.domain.dto.analyst;

import capstone25_2.aim.domain.entity.AnalystMetrics;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AnalystMetricsDTO {
    private Long analystId;
    private String analystName;
    private String firmName;
    private Double accuracyRate;      // 예측 정답률 (%)
    private Double returnRate;        // 평균 수익률 (%)
    private Double targetDiffRate;    // 목표가 오차율 (%)
    private Double avgReturnDiff;     // 평균 수익 편차
    private Double avgTargetDiff;     // 평균 목표가 편차
    private Integer aimsScore;        // aim's score (40~100점)
    private Integer reportCount;      // 평가 가능한 리포트 개수
    private Integer rank;             // 순위 (1부터 시작)
    private Integer totalAnalysts;    // 전체 애널리스트 수

    public static AnalystMetricsDTO fromEntity(AnalystMetrics metrics) {
        return AnalystMetricsDTO.builder()
                .analystId(metrics.getAnalyst().getId())
                .analystName(metrics.getAnalyst().getAnalystName())
                .firmName(metrics.getAnalyst().getFirmName())
                .accuracyRate(metrics.getAccuracyRate())
                .returnRate(metrics.getReturnRate())
                .targetDiffRate(metrics.getTargetDiffRate())
                .avgReturnDiff(metrics.getAvgReturnDiff())
                .avgTargetDiff(metrics.getAvgTargetDiff())
                .aimsScore(metrics.getAimsScore())
                .reportCount(metrics.getReportCount())
                .build();
    }
}

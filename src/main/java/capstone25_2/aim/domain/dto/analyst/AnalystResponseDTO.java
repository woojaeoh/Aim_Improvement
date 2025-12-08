package capstone25_2.aim.domain.dto.analyst;

import capstone25_2.aim.domain.entity.Analyst;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalystResponseDTO {
    private Long analystId;
    private String analystName;
    private String firmName;

    // 애널리스트 지표 (상세 조회 시에만 포함)
    private Double accuracyRate;      // 예측 정답률 (%)
    private Double returnRate;        // 평균 수익률 (%)
    private Double targetDiffRate;    // 목표가 오차율 (%)
    private Double avgReturnDiff;     // 평균 수익 편차
    private Double avgTargetDiff;     // 평균 목표가 편차
    private Integer aimsScore;        // aim's score (40~100점)
    private Integer rank;             // 전체 애널리스트 중 순위
    private Integer totalAnalysts;    // 전체 애널리스트 수

    // 커버 종목 리스트 (상세 조회 시에만 포함)
    private List<CoveredStockDTO> coveredStocks;

    // 리포트 목록 (상세 조회 시에만 포함)
    private List<AnalystReportSummaryDTO> reports;

    // 기본 정보만 포함 (리스트 조회용)
    public static AnalystResponseDTO fromEntity(Analyst analyst) {
        return AnalystResponseDTO.builder()
                .analystId(analyst.getId())
                .analystName(analyst.getAnalystName())
                .firmName(analyst.getFirmName())
                .build();
    }

    // 상세 조회용 (모든 정보 포함)
    public static AnalystResponseDTO fromEntityWithFullDetails(
            Analyst analyst,
            AnalystMetricsDTO metrics,
            List<CoveredStockDTO> coveredStocks,
            List<AnalystReportSummaryDTO> reports) {
        return AnalystResponseDTO.builder()
                .analystId(analyst.getId())
                .analystName(analyst.getAnalystName())
                .firmName(analyst.getFirmName())
                .accuracyRate(metrics != null ? metrics.getAccuracyRate() : null)
                .returnRate(metrics != null ? metrics.getReturnRate() : null)
                .targetDiffRate(metrics != null ? metrics.getTargetDiffRate() : null)
                .avgReturnDiff(metrics != null ? metrics.getAvgReturnDiff() : null)
                .avgTargetDiff(metrics != null ? metrics.getAvgTargetDiff() : null)
                .aimsScore(metrics != null ? metrics.getAimsScore() : null)
                .rank(metrics != null ? metrics.getRank() : null)
                .totalAnalysts(metrics != null ? metrics.getTotalAnalysts() : null)
                .coveredStocks(coveredStocks)
                .reports(reports)
                .build();
    }
}

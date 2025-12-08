package capstone25_2.aim.domain.dto.stock;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetPriceStatsDTO {
    private Integer maxTargetPrice;        // 최대 목표가
    private Double averageTargetPrice;     // 평균 목표가
    private Integer minTargetPrice;        // 최소 목표가
    private Integer aimsTargetPrice;        // aim이 분석한 목표가.
}

package capstone25_2.aim.domain.dto.stock;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetPriceStatsDTO {
    private Integer maxTargetPrice;        // 최대 목표가 (애널리스트 실제 목표가 기준)
    private Double averageTargetPrice;     // 평균 목표가 (애널리스트 실제 목표가 기준)
    private Integer minTargetPrice;        // 최소 목표가 (애널리스트 실제 목표가 기준)
    private Integer aimsTargetPrice;        // AIM's 평균 목표가 (BUY: 실제 목표가, HOLD: 현재가, SELL: 현재가×0.8)
    private Integer aimsMinTargetPrice;    // AIM's 최소 목표가 (BUY: 실제 목표가, HOLD: 현재가, SELL: 현재가×0.8)
    private Integer aimsMaxTargetPrice;    // AIM's 최대 목표가 (BUY: 실제 목표가, HOLD: 현재가, SELL: 현재가×0.8)
}

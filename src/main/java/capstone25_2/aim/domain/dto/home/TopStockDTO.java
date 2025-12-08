package capstone25_2.aim.domain.dto.home;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TopStockDTO {
    private Long stockId;
    private String stockName;
    private String stockCode;
    private Double upsidePotential;  // 상승 여력
    private Double buyRatio;          // 매수 의견 비율
}

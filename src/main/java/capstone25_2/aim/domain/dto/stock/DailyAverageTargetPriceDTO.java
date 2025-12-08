package capstone25_2.aim.domain.dto.stock;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyAverageTargetPriceDTO {
    private LocalDate date;                // 날짜
    private Double averageTargetPrice;     // AIM's 평균 목표가 (BUY/HOLD: 실제 목표가, SELL: 현재가×0.8)
}

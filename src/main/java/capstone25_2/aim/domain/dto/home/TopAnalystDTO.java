package capstone25_2.aim.domain.dto.home;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TopAnalystDTO {
    private Long analystId;
    private String analystName;
    private String firmName;
    private Double accuracyRate;    // 정답률
    private Double returnRate;      // 수익률
    private Integer aimsScore;      // AIM's score
}

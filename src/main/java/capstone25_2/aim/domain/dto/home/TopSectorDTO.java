package capstone25_2.aim.domain.dto.home;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TopSectorDTO {
    private String sectorName;
    private Double buyRatio;          // Strong Buy + Buy 비율
    private Integer strongBuyCount;
    private Integer buyCount;
}

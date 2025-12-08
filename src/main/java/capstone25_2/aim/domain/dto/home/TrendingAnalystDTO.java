package capstone25_2.aim.domain.dto.home;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TrendingAnalystDTO {
    private Long analystId;
    private String analystName;
    private String firmName;
    private Long searchCount;  // 최근 7일 검색 횟수
}

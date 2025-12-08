package capstone25_2.aim.domain.dto.search;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AnalystSearchResultDTO {
    private Long analystId;
    private String analystName;
    private String firmName;
}

package capstone25_2.aim.domain.dto.search;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class StockSearchResultDTO {
    private Long stockId;
    private String stockCode;
    private String stockName;
    private String sector;
}

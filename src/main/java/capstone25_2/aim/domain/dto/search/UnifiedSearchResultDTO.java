package capstone25_2.aim.domain.dto.search;

import lombok.*;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UnifiedSearchResultDTO {
    private List<AnalystSearchResultDTO> analysts;
    private List<StockSearchResultDTO> stocks;
}

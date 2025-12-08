package capstone25_2.aim.domain.dto.home;

import lombok.*;

import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class HomeResponseDTO {
    private List<TopAnalystDTO> topAnalysts;           // TOP 3 신뢰도
    private List<TopStockDTO> topStocks;               // TOP 3 상승여력
    private List<TopSectorDTO> topSectors;             // TOP 3 매수 섹터
    private List<TrendingAnalystDTO> trendingAnalysts; // TOP 3 검색량 (null 가능)
}

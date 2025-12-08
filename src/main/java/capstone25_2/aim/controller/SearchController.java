package capstone25_2.aim.controller;

import capstone25_2.aim.domain.dto.search.UnifiedSearchResultDTO;
import capstone25_2.aim.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 통합 검색 (애널리스트 + 종목)
     * 드롭다운 표시용 - 로그 저장 X
     */
    @GetMapping
    @Operation(summary = "통합 검색 (애널리스트 + 종목)",
            description = "애널리스트(이름) 및 종목(종목명, 종목코드)을 검색하면 드롭다운으로 매칭되는 리스트 보여줌. " +
                    "애널리스트는 이름으로만 검색, 종목은 종목명 또는 종목코드로 검색." )
    public UnifiedSearchResultDTO unifiedSearch(@RequestParam String keyword) {
        return searchService.unifiedSearch(keyword);
    }

    /**
     * 애널리스트 검색 로그 저장
     * 사용자가 드롭다운에서 특정 애널리스트를 클릭했을 때 호출
     */
    @PostMapping("/log/{analystId}")
    @Operation(summary = "애널리스트 검색 로그 저장. ",
            description = "애널리스트 검색 (이름 또는 회사명)하면 해당 애널리스트 검색 수 count 하나씩 증가."+
                    "검색 로그를 저장해 count+=1하면서 , 최근 7일동안 가장 hot한 애널리스트 top3제공"
    )
    public void logAnalystSearch(@PathVariable Long analystId) {
        searchService.logAnalystSearch(analystId);
    }
}

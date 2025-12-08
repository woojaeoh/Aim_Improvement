package capstone25_2.aim.controller;

import capstone25_2.aim.domain.dto.home.HomeResponseDTO;
import capstone25_2.aim.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /**
     * 홈 화면 데이터 조회
     * - TOP 3 신뢰도 애널리스트 (aimsScore 기준)
     * - TOP 3 상승여력 종목 (upsidePotential 기준)
     * - TOP 3 매수 섹터 (buyRatio 기준)
     * - TOP 3 검색량 애널리스트 (최근 7일)
     */
    @GetMapping
    @Operation(summary = "홈 화면 데이터 (TOP3 애널리스트, 종목, 섹터, 트렌딩)")
    public HomeResponseDTO getHomeData() {
        return homeService.getHomeData();
    }
}

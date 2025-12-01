package capstone25_2.aim.controller;

import capstone25_2.aim.domain.dto.sector.SectorListDTO;
import capstone25_2.aim.domain.dto.sector.SectorResponseDTO;
import capstone25_2.aim.service.SectorService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sectors")
@RequiredArgsConstructor
public class SectorController {

    private final SectorService sectorService;

    /**
     * 모든 섹터 리스트 조회
     * GET /sectors
     *
     * 각 섹터의 종목 수, 의견 분포 (5단계), 매수 비율 제공
     */
    @GetMapping
    @Operation(
            summary = "섹터 랭킹 페이지"
    )
    public List<SectorListDTO> getAllSectors() {
        return sectorService.getAllSectors();
    }

    /**
     * 특정 섹터 상세 조회
     * GET /sectors/{sectorName}
     *
     * 섹터 정보 + 섹터 내 종목 리스트 (각 종목의 상승여력, 매수비율, 최신 의견 포함)
     */
    @GetMapping("/{sectorName}")
    @Operation(
            summary = "섹터 상세 페이지"
    )
    public SectorResponseDTO getSectorDetails(@PathVariable String sectorName) {
        return sectorService.getSectorDetails(sectorName);
    }
}

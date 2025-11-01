package capstone25_2.aim.controller;

import capstone25_2.aim.domain.dto.report.ReportDetailDTO;
import capstone25_2.aim.domain.dto.report.ReportResponseDTO;
import capstone25_2.aim.domain.entity.Report;
import capstone25_2.aim.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // 특정 애널리스트의 리포트 리스트 조회
    @GetMapping("/analyst/{analystId}")
    public List<ReportResponseDTO> getReportsByAnalystId(@PathVariable Long analystId) {
        return reportService.getReportsByAnalystId(analystId).stream()
                .map(ReportResponseDTO::fromEntity)
                .toList();
    }

    // 특정 종목의 리포트 리스트 조회
    @GetMapping("/stock/{stockId}")
    public List<ReportResponseDTO> getReportsByStockId(@PathVariable Long stockId) {
        return reportService.getReportsByStockId(stockId).stream()
                .map(ReportResponseDTO::fromEntity)
                .toList();
    }

    // 리포트 상세 조회
    @GetMapping("/{reportId}")
    public ReportDetailDTO getReportDetail(@PathVariable Long reportId) {
        Report report = reportService.getReportById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));
        return ReportDetailDTO.fromEntity(report);
    }
}

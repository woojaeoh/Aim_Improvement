package capstone25_2.aim.service;

import capstone25_2.aim.domain.Report;
import capstone25_2.aim.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;

    public List<Report> getReportsByStockId(Long stockId){
        return reportRepository.findByStockId(stockId);
    }

    public List<Report> getReportsByAnalystId(Long analystId){
        return reportRepository.findByAnalystId(analystId);
    }

    public Optional<Report> getReportById(Long reportId){
        return reportRepository.findById(reportId);
    }
}

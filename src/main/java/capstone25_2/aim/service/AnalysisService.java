package capstone25_2.aim.service;

import capstone25_2.aim.domain.Analysis;
import capstone25_2.aim.repository.AnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalysisService {
    private final AnalysisRepository analysisRepository;

    public Optional<Analysis> getAnalysisByReportId(Long reportId) {
        return analysisRepository.findByReportId(reportId);
    }

    public Analysis saveAnalysis(Analysis analysis) {
        return analysisRepository.save(analysis);
    }

}

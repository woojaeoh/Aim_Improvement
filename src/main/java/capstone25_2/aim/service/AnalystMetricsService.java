package capstone25_2.aim.service;

import capstone25_2.aim.domain.dto.analyst.AnalystMetricsDTO;
import capstone25_2.aim.domain.dto.analyst.AnalystRankingResponseDTO;
import capstone25_2.aim.domain.entity.AnalystMetrics;
import capstone25_2.aim.repository.AnalystMetricsRepository;
import capstone25_2.aim.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalystMetricsService {

    private final AnalystMetricsRepository metricsRepository;
    private final ReportRepository reportRepository;

    // 랭킹 리스트 조회 (기본: accuracyRate 순)
    public AnalystRankingResponseDTO getRankedAnalysts(String sortBy) {
        List<AnalystMetrics> metricsList = metricsRepository.findAll();
        return createRankedResponse(metricsList,sortBy);
    }

    // 🔹 특정 종목 기준 랭킹
    public AnalystRankingResponseDTO getRankedAnalystsByStock(Long stockId, String sortBy) {
        // 1. 해당 종목의 리포트를 전부 가져옴
        List<Long> analystIds = reportRepository.findByStockId(stockId).stream()
                .map(r -> r.getAnalyst().getId())
                .distinct()
                .toList();

        // 2. 애널리스트 ID 기반으로 메트릭 필터링
        List<AnalystMetrics> metricsList = metricsRepository.findAll().stream()
                .filter(m -> analystIds.contains(m.getAnalyst().getId()))
                .toList();

        // 3. 정렬 결과 반환
        return createRankedResponse(metricsList, sortBy);
    }

    // 내부 정렬 로직 (중복 제거)
    private static AnalystRankingResponseDTO createRankedResponse(List<AnalystMetrics> metricsList, String sortBy) {
        Comparator<AnalystMetrics> comparator = switch (sortBy) {
            case "returnRate" -> Comparator.comparing(AnalystMetrics::getReturnRate).reversed();
            case "targetDiffRate" -> Comparator.comparing(AnalystMetrics::getTargetDiffRate);
            default -> Comparator.comparing(AnalystMetrics::getAccuracyRate).reversed();
        };

        List<AnalystMetricsDTO> ranking = metricsList.stream()
                .sorted(comparator)
                .map(AnalystMetricsDTO::fromEntity)
                .toList();

        return AnalystRankingResponseDTO.builder()
                .criteria(sortBy)
                .rankingList(ranking)
                .build();
    }


}

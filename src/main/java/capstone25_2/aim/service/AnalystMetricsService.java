package capstone25_2.aim.service;

import capstone25_2.aim.domain.AnalystMetrics;
import capstone25_2.aim.repository.AnalystMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AnalystMetricsService {
    private final AnalystMetricsRepository metricsRepository;

    public Optional<AnalystMetrics> getMetricsByAnalystId(Long analystId) {
        return metricsRepository.findByAnalystId(analystId);
    }

}

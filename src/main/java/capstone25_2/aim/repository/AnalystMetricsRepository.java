package capstone25_2.aim.repository;

import capstone25_2.aim.domain.AnalystMetrics;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnalystMetricsRepository {
    Optional<AnalystMetrics> findByAnalystId(Long analystId);
}

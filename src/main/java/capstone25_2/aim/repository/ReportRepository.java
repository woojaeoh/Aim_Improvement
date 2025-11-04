package capstone25_2.aim.repository;

import capstone25_2.aim.domain.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    //프로젝트 핵심 Repo -> 리포트 리스트, 상세 조회 전용

    List<Report> findByStockId(Long stockId); // 여러 주식 종목들 리스트
    List<Report> findByAnalystId(Long analystId); //여러 애널리스트 리스트

    // 최신 5년 리포트 조회
    List<Report> findByStockIdAndReportDateAfterOrderByReportDateDesc(Long stockId, LocalDateTime fromDate);
    List<Report> findByAnalystIdAndReportDateAfterOrderByReportDateDesc(Long analystId, LocalDateTime fromDate);
}

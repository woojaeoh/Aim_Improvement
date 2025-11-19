package capstone25_2.aim.repository;

import capstone25_2.aim.domain.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    //프로젝트 핵심 Repo -> 리포트 리스트, 상세 조회 전용

    List<Report> findByStockId(Long stockId); // 여러 주식 종목들 리스트
    List<Report> findByAnalystId(Long analystId); //여러 애널리스트 리스트

    // 최신 5년 리포트 조회
    List<Report> findByStockIdAndReportDateAfterOrderByReportDateDesc(Long stockId, LocalDateTime fromDate);
    List<Report> findByAnalystIdAndReportDateAfterOrderByReportDateDesc(Long analystId, LocalDateTime fromDate);

    // 특정 애널리스트의 특정 종목에 대한 리포트들 (오름차순)
    List<Report> findByAnalystIdAndStockIdOrderByReportDateAsc(Long analystId, Long stockId);

    // 특정 애널리스트의 특정 종목에 대해 특정 날짜 이후 가장 가까운 리포트 조회 (의견 변화 감지용)
    Optional<Report> findFirstByAnalystIdAndStockIdAndReportDateAfterOrderByReportDateAsc(
            Long analystId, Long stockId, LocalDateTime afterDate);

    // 중복 체크용: 애널리스트 + 종목 + 리포트 날짜로 기존 리포트 조회
    Optional<Report> findByAnalystIdAndStockIdAndReportDate(Long analystId, Long stockId, LocalDateTime reportDate);

    // 이전 리포트 조회용: 같은 애널리스트 + 같은 종목 + 현재 날짜 이전 중 가장 최근 리포트
    Optional<Report> findTopByAnalystIdAndStockIdAndReportDateBeforeOrderByReportDateDesc(
            Long analystId, Long stockId, LocalDateTime reportDate);
}

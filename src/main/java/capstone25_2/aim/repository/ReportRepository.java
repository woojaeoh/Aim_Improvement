package capstone25_2.aim.repository;

import capstone25_2.aim.domain.Report;
import capstone25_2.aim.domain.Stock;
import capstone25_2.aim.domain.SurfaceOpinion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    //프로젝트 핵심 Repo -> 리포트 리스트, 상세 조회 전용

    List<Report> findByStockId(Long stockId); // 여러 주식 종목들 리스트
    List<Report> findByAnalystId(Long analystId); //여러 애널리스트 리스트
    List<Report> findBySurfaceOpinion(SurfaceOpinion opinion); //종목에 해당하는 표면적 의견 리스트
}

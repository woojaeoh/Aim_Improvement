package capstone25_2.aim.repository;

import capstone25_2.aim.domain.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
    //리포트 분석 결과를 1:1로 조회한다? ..?
    // Ai 서버가 /analysis 업로드 시 저장.
    // 프론트에서 리포트 클릭 시 분석 결과 불러오기
    Optional<Analysis> findByReportId(Long reportId);

}

package capstone25_2.aim.repository;

import capstone25_2.aim.domain.Analyst;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalystRepository extends JpaRepository<Analyst, Long> {

    //애널리스트 정보 조회 및 소속 증권사별 리스트
    //findAll() : 기본 제공
    //findByFirmName() : ex) 키움증권 소속 애널리스트 검색
    List<Analyst> findByFirmName(String firmName);
}

package capstone25_2.aim.repository;

import capstone25_2.aim.domain.entity.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    /**
     * 최근 7일 내 애널리스트별 검색 횟수 TOP 3
     * 반환: [analystId, searchCount]
     */
    @Query("SELECT sl.analyst.id, COUNT(sl) " +
           "FROM SearchLog sl " +
           "WHERE sl.searchedAt >= :weekAgo " +
           "GROUP BY sl.analyst.id " +
           "ORDER BY COUNT(sl) DESC")
    List<Object[]> findTop3TrendingAnalysts(@Param("weekAgo") LocalDateTime weekAgo);
}

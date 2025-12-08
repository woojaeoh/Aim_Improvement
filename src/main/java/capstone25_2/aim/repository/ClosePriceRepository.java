package capstone25_2.aim.repository;

import capstone25_2.aim.domain.entity.ClosePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClosePriceRepository extends JpaRepository<ClosePrice, Long> {

    // 특정 종목의 특정 날짜 종가 조회
    Optional<ClosePrice> findByStockIdAndTradeDate(Long stockId, LocalDate tradeDate);

    // 특정 종목의 모든 종가 조회 (날짜 내림차순)
    List<ClosePrice> findByStockIdOrderByTradeDateDesc(Long stockId);

    // 특정 종목의 특정 기간 종가 조회
    List<ClosePrice> findByStockIdAndTradeDateBetweenOrderByTradeDateDesc(
            Long stockId, LocalDate startDate, LocalDate endDate);

    // 특정 종목의 특정 날짜 이후 종가 조회
    List<ClosePrice> findByStockIdAndTradeDateAfterOrderByTradeDateDesc(
            Long stockId, LocalDate afterDate);

    // 특정 날짜 이후 가장 가까운 거래일의 종가 조회 (1년 후 실제가 비교용)
    Optional<ClosePrice> findFirstByStockIdAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
            Long stockId, LocalDate fromDate);

    // 특정 날짜 이하의 가장 가까운 거래일의 종가 조회
    Optional<ClosePrice> findFirstByStockIdAndTradeDateLessThanEqualOrderByTradeDateDesc(
            Long stockId, LocalDate reportDate);

    // 여러 종목의 종가를 한 번에 조회 (섹터 페이지 최적화용)
    List<ClosePrice> findByStockIdInOrderByStockIdAscTradeDateDesc(List<Long> stockIds);
}

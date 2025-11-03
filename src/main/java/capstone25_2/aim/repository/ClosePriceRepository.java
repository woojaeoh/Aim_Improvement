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
}

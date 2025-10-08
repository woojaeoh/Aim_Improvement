package capstone25_2.aim.repository;

import capstone25_2.aim.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {
    //Top 100 종목 리스트나 단일 종목 조회용
    //findAll() : 기본 제공
    //findByStockCode() : 종목코드 기준 단일 조회

    Optional<Stock> findByStockCode(String stockCode);
}

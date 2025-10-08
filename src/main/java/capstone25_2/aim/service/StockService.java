package capstone25_2.aim.service;

import capstone25_2.aim.domain.Stock;
import capstone25_2.aim.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    public List<Stock> getAllStocks() {
        return stockRepository.findAll();
    }

    //code는 크롤링 데이터용 식별자 -> 크롤링 시 코드 기준으로 리포트 검색.
    public Optional<Stock> getStockByCode(String stockCode){
        return stockRepository.findByStockCode(stockCode);
    }


    //id는 내부 식별자
    public Optional<Stock> getStockById(Long id){
        return stockRepository.findById(id);
    }
}

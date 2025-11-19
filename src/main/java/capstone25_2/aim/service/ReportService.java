package capstone25_2.aim.service;

import capstone25_2.aim.domain.dto.report.ReportRequestDTO;
import capstone25_2.aim.domain.dto.report.TargetPriceTrendDTO;
import capstone25_2.aim.domain.dto.report.TargetPriceTrendResponseDTO;
import capstone25_2.aim.domain.dto.stock.StockConsensusDTO;
import capstone25_2.aim.domain.entity.Analyst;
import capstone25_2.aim.domain.entity.Report;
import capstone25_2.aim.domain.entity.Stock;
import capstone25_2.aim.domain.entity.SurfaceOpinion;
import capstone25_2.aim.repository.AnalystRepository;
import capstone25_2.aim.repository.ReportRepository;
import capstone25_2.aim.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;
    private final AnalystRepository analystRepository;
    private final StockRepository stockRepository;
    private final AnalystMetricsService analystMetricsService;

    public List<Report> getReportsByStockId(Long stockId){
        return reportRepository.findByStockId(stockId);
    }

    public List<Report> getReportsByAnalystId(Long analystId){
        return reportRepository.findByAnalystId(analystId);
    }

    public Optional<Report> getReportById(Long reportId){
        return reportRepository.findById(reportId);
    }

    // 최신 5년의 리포트 리스트 조회 (종목별)
    public List<Report> getRecentReportsByStockId(Long stockId){
        LocalDateTime fiveYearsAgo = LocalDateTime.now().minusYears(5);
        return reportRepository.findByStockIdAndReportDateAfterOrderByReportDateDesc(stockId, fiveYearsAgo);
    }

    // 최신 5년의 리포트 리스트 조회 (애널리스트별)
    public List<Report> getRecentReportsByAnalystId(Long analystId){
        LocalDateTime fiveYearsAgo = LocalDateTime.now().minusYears(5);
        return reportRepository.findByAnalystIdAndReportDateAfterOrderByReportDateDesc(analystId, fiveYearsAgo);
    }

    // 종목별 목표가 변동 추이 데이터 생성
    public TargetPriceTrendResponseDTO getTargetPriceTrend(Long stockId){
        List<Report> recentReports = getRecentReportsByStockId(stockId);

        if(recentReports.isEmpty()){
            throw new RuntimeException("No reports found for stock");
        }

        // 목표가 변동 추이 리스트 생성
        List<TargetPriceTrendDTO> trendList = recentReports.stream()
                .map(report -> {
                    String percentageChange = calculatePercentageChange(report);
                    return TargetPriceTrendDTO.builder()
                            .reportDate(LocalDate.from(report.getReportDate()))
                            .targetPrice(report.getTargetPrice())
                            .analystName(report.getAnalyst().getAnalystName())
                            .firmName(report.getAnalyst().getFirmName())
                            .reportId(report.getId())
                            .percentageChange(percentageChange)
                            .build();
                })
                .collect(Collectors.toList());

        // 응답 DTO 생성
        Report firstReport = recentReports.get(0);
        return TargetPriceTrendResponseDTO.builder()
                .stockName(firstReport.getStock().getStockName())
                .stockCode(firstReport.getStock().getStockCode())
                .targetPriceTrend(trendList)
                .reportCount(trendList.size())
                .build();
    }

    /**
     * 종목별 surfaceOpinion 종합 의견 조회
     * 각 애널리스트의 최신 리포트 1개씩만 집계 (BUY, HOLD, SELL 개수)
     */
    public StockConsensusDTO getStockConsensus(Long stockId) {
        // 1. 종목 조회
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("Stock not found"));

        // 2. 해당 종목의 최근 5년 리포트 조회
        List<Report> recentReports = getRecentReportsByStockId(stockId);

        if (recentReports.isEmpty()) {
            throw new RuntimeException("No reports found for stock");
        }

        // 3. 애널리스트별로 그룹핑하고 각 애널리스트의 최신 리포트만 선택
        Map<Long, Report> latestReportsByAnalyst = recentReports.stream()
                .collect(Collectors.toMap(
                        report -> report.getAnalyst().getId(),
                        report -> report,
                        (existing, replacement) ->
                            existing.getReportDate().isAfter(replacement.getReportDate())
                                ? existing : replacement
                ));

        // 4. 각 애널리스트의 최신 리포트만 추출
        List<Report> latestReports = new ArrayList<>(latestReportsByAnalyst.values());

        // 5. surfaceOpinion이 null이 아닌 것만 필터링
        List<Report> validReports = latestReports.stream()
                .filter(report -> report.getSurfaceOpinion() != null)
                .collect(Collectors.toList());

        if (validReports.isEmpty()) {
            throw new RuntimeException("No valid surfaceOpinion data found");
        }

        // 6. surfaceOpinion 별 개수 계산
        int buyCount = (int) validReports.stream()
                .filter(report -> report.getSurfaceOpinion() == SurfaceOpinion.BUY)
                .count();

        int holdCount = (int) validReports.stream()
                .filter(report -> report.getSurfaceOpinion() == SurfaceOpinion.HOLD)
                .count();

        int sellCount = (int) validReports.stream()
                .filter(report -> report.getSurfaceOpinion() == SurfaceOpinion.SELL)
                .count();

        // 7. 평균 목표가 계산
        Double averageTargetPrice = validReports.stream()
                .map(Report::getTargetPrice)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        // 8. DTO 생성 및 반환
        return StockConsensusDTO.builder()
                .stockId(stock.getId())
                .stockName(stock.getStockName())
                .stockCode(stock.getStockCode())
                .buyCount(buyCount)
                .holdCount(holdCount)
                .sellCount(sellCount)
                .averageTargetPrice(averageTargetPrice)
                .totalReports(validReports.size())
                .totalAnalysts(latestReportsByAnalyst.size())
                .build();
    }

    /**
     * 직전 대비 목표주가 변동률 계산
     * @param report 현재 리포트
     * @return 변동률 문자열 (예: "+8.89%", "-13.15%") 또는 null (이전 리포트가 없는 경우)
     */
    private String calculatePercentageChange(Report report) {
        // 이전 리포트가 없으면 null 반환
        if (report.getPrevReport() == null) {
            return null;
        }

        Integer currentPrice = report.getTargetPrice();
        Integer prevPrice = report.getPrevReport().getTargetPrice();

        // 현재 가격 또는 이전 가격이 없으면 null 반환
        if (currentPrice == null || prevPrice == null || prevPrice == 0) {
            return null;
        }

        // 변동률 계산: ((현재가격 - 이전가격) / 이전가격) * 100
        double changeRate = ((double) (currentPrice - prevPrice) / prevPrice) * 100;

        // 소수점 두자리로 포맷팅하고 부호 추가
        String formattedRate = String.format("%.2f", Math.abs(changeRate));

        if (changeRate > 0) {
            return "+" + formattedRate + "%";
        } else if (changeRate < 0) {
            return "-" + formattedRate + "%";
        } else {
            return "0.00%";
        }
    }

    /**
     * AI 모델로부터 받은 데이터를 저장
     * 1. Analyst 먼저 저장 (없으면 새로 생성, 있으면 기존 사용)
     * 2. stockCode로 Stock 조회
     * 3. Report 저장
     * 4. 애널리스트 정확도 자동 재계산
     */
    @Transactional
    public Report saveReportFromAI(ReportRequestDTO requestDTO) {
        Report savedReport = saveReportWithoutMetricsUpdate(requestDTO);

        // 리포트 저장 후 애널리스트 정확도 자동 재계산
        analystMetricsService.calculateAndSaveAccuracyRate(savedReport.getAnalyst().getId());

        return savedReport;
    }

    /**
     * 리포트만 저장하고 메트릭 계산은 하지 않음 (내부용)
     * 중복 체크: 애널리스트 + 종목 + 리포트 날짜가 같으면 기존 리포트 반환
     */
    private Report saveReportWithoutMetricsUpdate(ReportRequestDTO requestDTO) {
        // 1. Analyst 조회 또는 생성
        Analyst analyst = analystRepository
                .findByAnalystNameAndFirmName(
                        requestDTO.getAnalyst().getAnalystName(),
                        requestDTO.getAnalyst().getFirmName()
                )
                .orElseGet(() -> {
                    // 애널리스트가 없으면 새로 생성
                    Analyst newAnalyst = new Analyst();
                    newAnalyst.setAnalystName(requestDTO.getAnalyst().getAnalystName());
                    newAnalyst.setFirmName(requestDTO.getAnalyst().getFirmName());
                    return analystRepository.save(newAnalyst);
                });

        // 2. Stock 조회 (stockCode로)
        Stock stock = stockRepository.findByStockCode(requestDTO.getReport().getStockCode())
                .orElseThrow(() -> new RuntimeException("Stock not found with code: " + requestDTO.getReport().getStockCode()));

        // 3. 중복 체크: 애널리스트 + 종목 + 리포트 날짜로 기존 리포트 확인
        LocalDateTime reportDate = requestDTO.getReport().getReportDate().atStartOfDay();
        Optional<Report> existingReport = reportRepository.findByAnalystIdAndStockIdAndReportDate(
                analyst.getId(),
                stock.getId(),
                reportDate
        );

        // 이미 존재하면 기존 리포트 반환 (중복 저장 방지)
        if (existingReport.isPresent()) {
            return existingReport.get();
        }

        // 4. Report 생성 및 저장
        Report report = new Report();
        report.setReportTitle(requestDTO.getReport().getReportTitle());
        report.setReportDate(reportDate);
        report.setTargetPrice(requestDTO.getReport().getTargetPrice());
        report.setSurfaceOpinion(requestDTO.getReport().getSurfaceOpinion());
        report.setHiddenOpinion(requestDTO.getReport().getHiddenOpinion());
        report.setAnalyst(analyst);
        report.setStock(stock);  // JPA가 자동으로 stock_id FK 저장

        // 5. prevReport 설정: 같은 애널리스트 + 같은 종목의 직전 리포트 조회
        Optional<Report> prevReport = reportRepository
                .findTopByAnalystIdAndStockIdAndReportDateBeforeOrderByReportDateDesc(
                        analyst.getId(),
                        stock.getId(),
                        reportDate
                );
        prevReport.ifPresent(report::setPrevReport);

        return reportRepository.save(report);
    }

    /**
     * 여러 개의 리포트를 한번에 저장 (배치 처리)
     * Python에서 DataFrame을 JSON 배열로 보낼 때 사용
     * 효율성을 위해 모든 리포트 저장 후 애널리스트별로 한 번씩만 정확도 계산
     */
    @Transactional
    public List<Report> saveReportsFromAIBatch(List<ReportRequestDTO> requestDTOList) {
        // 1. 모든 리포트 저장 (정확도 계산 없이)
        List<Report> savedReports = requestDTOList.stream()
                .map(this::saveReportWithoutMetricsUpdate)
                .collect(Collectors.toList());

        // 2. 저장된 리포트에 관련된 애널리스트 ID 중복 제거
        Set<Long> analystIds = savedReports.stream()
                .map(report -> report.getAnalyst().getId())
                .collect(Collectors.toSet());

        // 3. 각 애널리스트의 정확도를 한 번씩만 재계산
        analystIds.forEach(analystMetricsService::calculateAndSaveAccuracyRate);

        return savedReports;
    }
}

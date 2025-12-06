package capstone25_2.aim.service;

import capstone25_2.aim.domain.dto.report.ReportRequestDTO;
import capstone25_2.aim.domain.dto.report.TargetPriceTrendDTO;
import capstone25_2.aim.domain.dto.report.TargetPriceTrendResponseDTO;
import capstone25_2.aim.domain.dto.stock.StockConsensusDTO;
import capstone25_2.aim.domain.entity.*;
import capstone25_2.aim.repository.AnalystRepository;
import capstone25_2.aim.repository.ClosePriceRepository;
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
    private final ClosePriceRepository closePriceRepository;
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

    // 최신 1년의 리포트 리스트 조회 (종목별)
    public List<Report> getRecentReportsByStockId(Long stockId){
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        return reportRepository.findByStockIdAndReportDateAfterOrderByReportDateDesc(stockId, oneYearAgo);
    }

    // 최신 5년의 리포트 리스트 조회 (애널리스트별)
    public List<Report> getRecentReportsByAnalystId(Long analystId){
        LocalDateTime fiveYearsAgo = LocalDateTime.now().minusYears(5);
        return reportRepository.findByAnalystIdAndReportDateAfterOrderByReportDateDesc(analystId, fiveYearsAgo);
    }

    // 종목별 목표가 변동 추이 데이터 생성
    @Transactional(readOnly = true)
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
     * 각 애널리스트의 의견 변화 이후 최신 리포트만 집계 (BUY, HOLD, SELL 개수)
     * 의견 변화가 없으면 최근 1년 리포트 중 최신 리포트 사용
     */
    @Transactional(readOnly = true)
    public StockConsensusDTO getStockConsensus(Long stockId) {
        // 1. 종목 조회
        Stock stock = stockRepository.findById(stockId)
                .orElseThrow(() -> new RuntimeException("Stock not found"));

        // 2. 해당 종목의 최근 1년 리포트 조회
        List<Report> recentReports = getRecentReportsByStockId(stockId);

        if (recentReports.isEmpty()) {
            throw new RuntimeException("No reports found for stock");
        }

        // 3. 애널리스트별로 그룹핑
        Map<Long, List<Report>> reportsByAnalyst = recentReports.stream()
                .collect(Collectors.groupingBy(report -> report.getAnalyst().getId()));

        // 4. 각 애널리스트의 의견 변화 이후 최신 리포트만 선택
        List<Report> validReportsAfterOpinionChange = new ArrayList<>();

        for (Map.Entry<Long, List<Report>> entry : reportsByAnalyst.entrySet()) {
            List<Report> analystReports = entry.getValue();

            // 날짜순 정렬 (오래된 것부터)
            analystReports.sort(Comparator.comparing(Report::getReportDate));

            // 마지막 의견 변화 시점 찾기
            int lastChangeIndex = 0;
            String previousCategory = null;

            for (int i = 0; i < analystReports.size(); i++) {
                String currentCategory = HiddenOpinionLabel.toSimpleCategory(
                        analystReports.get(i).getHiddenOpinion());

                if (previousCategory != null && !Objects.equals(previousCategory, currentCategory)) {
                    lastChangeIndex = i;  // 의견 변화 발생
                }
                previousCategory = currentCategory;
            }

            // 의견 변화 이후의 가장 최신 리포트 선택
            Report latestValidReport = analystReports.get(analystReports.size() - 1);
            validReportsAfterOpinionChange.add(latestValidReport);
        }

        // 5. hiddenOpinion이 null이 아닌 것만 필터링
        List<Report> validReports = validReportsAfterOpinionChange.stream()
                .filter(report -> report.getHiddenOpinion() != null)
                .collect(Collectors.toList());

        if (validReports.isEmpty()) {
            throw new RuntimeException("No valid hiddenOpinion data found");
        }

        // 6. hiddenOpinion 별 개수 계산 (3단계 분류)
        int buyCount = (int) validReports.stream()
                .filter(report -> {
                    String category = HiddenOpinionLabel.toSimpleCategory(report.getHiddenOpinion());
                    return "BUY".equals(category);
                })
                .count();

        int holdCount = (int) validReports.stream()
                .filter(report -> {
                    String category = HiddenOpinionLabel.toSimpleCategory(report.getHiddenOpinion());
                    return "HOLD".equals(category);
                })
                .count();

        int sellCount = (int) validReports.stream()
                .filter(report -> {
                    String category = HiddenOpinionLabel.toSimpleCategory(report.getHiddenOpinion());
                    return "SELL".equals(category);
                })
                .count();

        // 7. 평균 목표가 계산
        Double averageTargetPrice = validReports.stream()
                .map(Report::getTargetPrice)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        // 8. 현재 종가 조회 및 상승 여력 계산
        Double upsidePotential = null;
        List<ClosePrice> closePrices = closePriceRepository.findByStockIdOrderByTradeDateDesc(stockId);
        if (!closePrices.isEmpty() && averageTargetPrice > 0) {
            Integer currentClosePrice = closePrices.get(0).getClosePrice();
            if (currentClosePrice != null && currentClosePrice > 0) {
                upsidePotential = ((averageTargetPrice - currentClosePrice) / currentClosePrice) * 100;
                // 소수점 둘째자리까지 반올림
                upsidePotential = Math.round(upsidePotential * 100.0) / 100.0;
            }
        }

        // 9. DTO 생성 및 반환
        return StockConsensusDTO.builder()
                .stockId(stock.getId())
                .stockName(stock.getStockName())
                .stockCode(stock.getStockCode())
                .buyCount(buyCount)
                .holdCount(holdCount)
                .sellCount(sellCount)
                .averageTargetPrice(averageTargetPrice)
                .upsidePotential(upsidePotential)
                .totalReports(validReports.size())
                .totalAnalysts(reportsByAnalyst.size())
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
     * 배치 처리 중 애널리스트 캐시를 사용하여 중복 조회 방지
     */
    @Transactional
    public List<Report> saveReportsFromAIBatch(List<ReportRequestDTO> requestDTOList) {
        System.out.println("\n🔄 Service 계층 처리 시작: " + requestDTOList.size() + "개 DTO 받음");

        // 1. 애널리스트 캐시 생성 (배치 처리 중 중복 조회 방지)
        Map<String, Analyst> analystCache = new HashMap<>();

        // 통계 카운터
        int stockNotFoundCount = 0;
        int duplicateCount = 0;
        int newReportCount = 0;

        // 2. 모든 리포트 객체 생성 (아직 DB에 저장하지 않음)
        List<Report> reportsToSave = new ArrayList<>();
        for (ReportRequestDTO requestDTO : requestDTOList) {
            ReportSaveResult result = saveReportWithCacheAndStats(requestDTO, analystCache);

            if (result == null) {
                stockNotFoundCount++;
            } else if (result.isDuplicate) {
                duplicateCount++;
            } else {
                newReportCount++;
                reportsToSave.add(result.report);
            }
        }

        System.out.println("\n📊 Service 계층 통계:");
        System.out.println("  - 신규 리포트: " + newReportCount + "개");
        System.out.println("  - 중복 리포트 (스킵): " + duplicateCount + "개");
        System.out.println("  - Stock 없음 (스킵): " + stockNotFoundCount + "개");

        System.out.println("\n📦 Batch Insert 시작: " + reportsToSave.size() + "개 리포트");

        // 3. Batch Insert - 한 번에 저장 (대폭 성능 향상)
        List<Report> savedReports = reportRepository.saveAll(reportsToSave);

        System.out.println("✅ Batch Insert 완료: " + savedReports.size() + "개 저장됨");

        // 4. 저장된 리포트에 관련된 애널리스트 ID 중복 제거
        Set<Long> analystIds = savedReports.stream()
                .map(report -> report.getAnalyst().getId())
                .collect(Collectors.toSet());

        // 4. 각 애널리스트의 정확도를 한 번씩만 재계산 (임시 비활성화)
        // TODO: 데이터 저장 완료 후 별도 API로 실행
        // analystIds.forEach(analystMetricsService::calculateAndSaveAccuracyRate);
        System.out.println("⚠️ 지표 계산 스킵 (성능 최적화). 저장된 리포트: " + savedReports.size()
            + "개, 애널리스트: " + analystIds.size() + "명\n");

        return savedReports;
    }

    // 저장 결과를 담는 내부 클래스
    private static class ReportSaveResult {
        Report report;
        boolean isDuplicate;

        ReportSaveResult(Report report, boolean isDuplicate) {
            this.report = report;
            this.isDuplicate = isDuplicate;
        }
    }

    /**
     * 애널리스트 캐시를 사용하여 리포트 저장 (배치 처리용, 통계 포함)
     */
    private ReportSaveResult saveReportWithCacheAndStats(ReportRequestDTO requestDTO, Map<String, Analyst> analystCache) {
        // 1. 캐시에서 Analyst 조회 (analystName + firmName을 키로 사용)
        String cacheKey = requestDTO.getAnalyst().getAnalystName() + "|" + requestDTO.getAnalyst().getFirmName();

        Analyst analyst = analystCache.computeIfAbsent(cacheKey, key -> {
            // 캐시에 없으면 DB에서 조회 또는 생성
            return analystRepository
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
        });

        // 2. Stock 조회 (stockCode로) - DB에 없으면 스킵
        Optional<Stock> stockOpt = stockRepository.findByStockCode(requestDTO.getReport().getStockCode());
        if (stockOpt.isEmpty()) {
            System.err.println("⚠️ Stock을 찾을 수 없어 스킵: stockCode=" + requestDTO.getReport().getStockCode()
                + ", 리포트=" + requestDTO.getReport().getReportTitle());
            return null;  // 해당 리포트 스킵
        }
        Stock stock = stockOpt.get();

        // 3. 중복 체크: 애널리스트 + 종목 + 리포트 날짜로 기존 리포트 확인
        LocalDateTime reportDate = requestDTO.getReport().getReportDate().atStartOfDay();
        Optional<Report> existingReport = reportRepository.findByAnalystIdAndStockIdAndReportDate(
                analyst.getId(),
                stock.getId(),
                reportDate
        );

        // 이미 존재하면 기존 리포트 반환 (중복 저장 방지)
        if (existingReport.isPresent()) {
            return new ReportSaveResult(existingReport.get(), true);  // 중복
        }

        // 4. Report 생성 및 저장
        Report report = new Report();
        report.setReportTitle(requestDTO.getReport().getReportTitle());
        report.setReportDate(reportDate);
        report.setTargetPrice(requestDTO.getReport().getTargetPrice());
        report.setSurfaceOpinion(requestDTO.getReport().getSurfaceOpinion());
        report.setHiddenOpinion(requestDTO.getReport().getHiddenOpinion());
        report.setAnalyst(analyst);
        report.setStock(stock);

        // 5. prevReport 설정 (임시 비활성화 - 성능 최적화)
        // TODO: 데이터 저장 완료 후 별도로 설정
        // Optional<Report> prevReport = reportRepository
        //         .findTopByAnalystIdAndStockIdAndReportDateBeforeOrderByReportDateDesc(
        //                 analyst.getId(),
        //                 stock.getId(),
        //                 reportDate
        //         );
        // prevReport.ifPresent(report::setPrevReport);

        return new ReportSaveResult(report, false);  // 신규 리포트
    }

    /**
     * DB에 저장된 모든 리포트의 prevReport를 일괄 설정
     * 같은 애널리스트 + 같은 종목의 직전 리포트를 찾아서 매핑
     */
    @Transactional
    public int updateAllPrevReports() {
        System.out.println("🔄 모든 리포트의 prevReport 일괄 설정 시작...");

        // 1. 모든 리포트 조회
        List<Report> allReports = reportRepository.findAll();
        System.out.println("📊 전체 리포트 수: " + allReports.size());

        int updatedCount = 0;

        // 2. 각 리포트마다 prevReport 설정
        for (Report report : allReports) {
            // prevReport가 이미 설정되어 있으면 스킵
            if (report.getPrevReport() != null) {
                continue;
            }

            // 같은 애널리스트 + 같은 종목의 직전 리포트 조회
            Optional<Report> prevReport = reportRepository
                    .findTopByAnalystIdAndStockIdAndReportDateBeforeOrderByReportDateDesc(
                            report.getAnalyst().getId(),
                            report.getStock().getId(),
                            report.getReportDate()
                    );

            // prevReport가 있으면 설정
            if (prevReport.isPresent()) {
                report.setPrevReport(prevReport.get());
                updatedCount++;
            }
        }

        // 3. 배치 저장
        reportRepository.saveAll(allReports);
        System.out.println("✅ prevReport 설정 완료: " + updatedCount + "개 업데이트됨");

        return updatedCount;
    }

    /**
     * DB에 저장된 모든 애널리스트의 지표를 일괄 계산 (성능 최적화 버전 사용)
     */
    @Transactional
    public int calculateAllAnalystMetrics() {
        // 성능 최적화된 버전으로 위임
        return analystMetricsService.calculateAllAnalystMetricsWithCache();
    }
}

package capstone25_2.aim.controller;

import capstone25_2.aim.domain.dto.report.ReportDetailDTO;
import capstone25_2.aim.domain.dto.report.ReportRequestDTO;
import capstone25_2.aim.domain.dto.report.ReportResponseDTO;
import capstone25_2.aim.domain.dto.report.TargetPriceTrendResponseDTO;
import capstone25_2.aim.domain.dto.stock.StockConsensusDTO;
import capstone25_2.aim.domain.entity.Report;
import capstone25_2.aim.domain.entity.SurfaceOpinion;
import capstone25_2.aim.service.ReportService;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Report", description = "리포트 관련 API")
public class ReportController {

    private final ReportService reportService;

//    // 특정 애널리스트의 최신 5년 리포트 리스트 조회
//    @GetMapping("/analyst/{analystId}")
//    @Operation(summary = "애널리스트별 최신 5년 리포트 조회", description = "특정 애널리스트의 최신 5년간 리포트 리스트를 조회합니다.")
//    public List<ReportResponseDTO> getReportsByAnalystId(
//            @Parameter(description = "애널리스트 ID") @PathVariable Long analystId) {
//        return reportService.getRecentReportsByAnalystId(analystId).stream()
//                .map(ReportResponseDTO::fromEntity)
//                .toList();
//    }

//    // 특정 종목의 최신 5년 리포트 리스트 조회
//    @GetMapping("/stock/{stockId}")
//    @Operation(summary = "종목별 최신 5년 리포트 조회", description = "특정 종목의 최신 5년간 리포트 리스트를 조회합니다.")
//    public List<ReportResponseDTO> getReportsByStockId(
//            @Parameter(description = "종목 ID") @PathVariable Long stockId) {
//        return reportService.getRecentReportsByStockId(stockId).stream()
//                .map(ReportResponseDTO::fromEntity)
//                .toList();
//    }

//    // 리포트 상세 조회
//    @GetMapping("/{reportId}")
//    @Operation(summary = "리포트 상세 조회", description = "특정 리포트의 상세 정보를 조회합니다.")
//    public ReportDetailDTO getReportDetail(
//            @Parameter(description = "리포트 ID") @PathVariable Long reportId) {
//        Report report = reportService.getReportById(reportId)
//                .orElseThrow(() -> new RuntimeException("Report not found"));
//        return ReportDetailDTO.fromEntity(report);
//    }

//    // AI 모델로부터 리포트 데이터 저장 (단일)
//    @PostMapping("/from-ai")
//    @Operation(
//            summary = "AI 모델로부터 리포트 저장 (단일)",
//            description = "AI 모델이 분석한 리포트 데이터를 저장합니다. " +
//                    "Analyst 정보가 없으면 새로 생성하고, 있으면 기존 데이터를 사용합니다. " +
//                    "stockCode로 Stock을 조회하여 연결합니다."
//    )
//    public ResponseEntity<ReportDetailDTO> saveReportFromAI(@RequestBody ReportRequestDTO requestDTO) {
//        try {
//            Report savedReport = reportService.saveReportFromAI(requestDTO);
//            ReportDetailDTO responseDTO = ReportDetailDTO.fromEntity(savedReport);
//            return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
//        } catch (RuntimeException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
//        }
//    }

//    // AI 모델로부터 리포트 데이터 배치 저장
//    @PostMapping("/batch")
//    @Operation(
//            summary = "AI 모델로부터 리포트 배치 저장",
//                    description = "Python에서 DataFrame을 JSON 배열로 보내면 여러 개의 리포트를 한번에 저장합니다. " +
//                            "각 리포트마다 Analyst는 조회/생성하고, stockCode로 Stock을 찾아 연결합니다."
//    )
//            public ResponseEntity<List<ReportDetailDTO>> saveReportsFromAIBatch(
//                    @RequestBody List<ReportRequestDTO> requestDTOList) {
//                try {
//                    List<Report> savedReports = reportService.saveReportsFromAIBatch(requestDTOList);
//                    List<ReportDetailDTO> responseDTOList = savedReports.stream()
//                    .map(ReportDetailDTO::fromEntity)
//                    .collect(java.util.stream.Collectors.toList());
//            return ResponseEntity.status(HttpStatus.CREATED).body(responseDTOList);
//        } catch (RuntimeException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
//        }
//    }

    // CSV 파일 업로드로 리포트 배치 저장
    @PostMapping(value = "/upload-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "CSV 파일로 리포트 배치 저장",
            description = "CSV 파일을 업로드하여 여러 개의 리포트를 한번에 저장합니다. " +
                    "CSV 컬럼 순서: analystName, firmName, hiddenOpinion, reportDate, reportTitle, stockCode, surfaceOpinion, targetPrice. " +
                    "예외 처리: 컬럼이 하나라도 비어있거나, analystName이 4글자 이상이면 해당 행은 스킵됩니다."
    )
    public ResponseEntity<?> uploadCsvReports(
            @Parameter(description = "CSV 파일") @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("파일이 비어있습니다.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".csv") && !filename.endsWith(".CSV"))) {
            return ResponseEntity.badRequest().body("CSV 파일만 업로드 가능합니다.");
        }

        try {
            List<ReportRequestDTO> requestDTOList = parseCsvToReportRequestDTOList(file);

            if (requestDTOList.isEmpty()) {
                return ResponseEntity.badRequest().body("CSV 파일에 데이터가 없습니다.");
            }

            List<Report> savedReports = reportService.saveReportsFromAIBatch(requestDTOList);
            List<ReportDetailDTO> responseDTOList = savedReports.stream()
                    .map(ReportDetailDTO::fromEntity)
                    .collect(java.util.stream.Collectors.toList());

            return ResponseEntity.status(HttpStatus.CREATED).body(responseDTOList);
        } catch (IOException | CsvException e) {
            e.printStackTrace();  // 콘솔에 스택 트레이스 출력
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("CSV 파일 파싱 오류: " + e.getMessage());
        } catch (RuntimeException e) {
            System.err.println("=== 데이터 저장 오류 발생 ===");
            e.printStackTrace();  // 콘솔에 스택 트레이스 출력
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("데이터 저장 오류: " + e.getMessage());
        }
    }

    // prevReport 일괄 설정 (데이터 업로드 후 실행)
    @PostMapping("/update-prev-reports")
    @Operation(
            summary = "모든 리포트의 prevReport 일괄 설정",
            description = "DB에 저장된 모든 리포트의 prevReport를 일괄 설정합니다. " +
                    "같은 애널리스트 + 같은 종목의 직전 리포트를 찾아서 FK 매핑합니다. " +
                    "대량 데이터 업로드 후 실행해야 합니다."
    )
    public ResponseEntity<Map<String, Object>> updateAllPrevReports() {
        try {
            int updatedCount = reportService.updateAllPrevReports();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("updatedCount", updatedCount);
            response.put("message", "prevReport 설정 완료: " + updatedCount + "개 업데이트됨");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "prevReport 설정 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // 애널리스트 지표 일괄 계산 (데이터 업로드 후 실행)
    @PostMapping("/calculate-analyst-metrics")
    @Operation(
            summary = "모든 애널리스트 지표 일괄 계산",
            description = "DB에 저장된 모든 애널리스트의 정확도 지표를 일괄 계산합니다. " +
                    "대량 데이터 업로드 후 실행해야 합니다. " +
                    "애널리스트가 많을 경우 시간이 오래 걸릴 수 있습니다."
    )
    public ResponseEntity<Map<String, Object>> calculateAllAnalystMetrics() {
        try {
            int calculatedCount = reportService.calculateAllAnalystMetrics();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("calculatedCount", calculatedCount);
            response.put("message", "애널리스트 지표 계산 완료: " + calculatedCount + "명");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "지표 계산 오류: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * CSV 파일을 파싱하여 ReportRequestDTO 리스트로 변환
     * CSV 컬럼 순서: analystName, firmName, hiddenOpinion, reportDate, reportTitle, stockCode, surfaceOpinion, targetPrice
     *
     * 예외 처리:
     * - 컬럼이 하나라도 비어있으면 스킵
     * - analystName이 4글자 이상이면 스킵
     */
    private List<ReportRequestDTO> parseCsvToReportRequestDTOList(MultipartFile file)
            throws IOException, CsvException {

        List<ReportRequestDTO> result = new ArrayList<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 통계 카운터
        int totalRows = 0;
        int columnCountError = 0;
        int emptyColumnError = 0;
        int analystNameTooLongError = 0;
        int parseError = 0;
        int successCount = 0;

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> rows = reader.readAll();
            totalRows = rows.size() - 1; // 헤더 제외

            System.out.println("📄 CSV 파일 읽기 시작: 총 " + totalRows + "개 행");

            // 첫 번째 행은 헤더로 스킵
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);

                // 컬럼 수가 8개가 아니면 스킵
                if (row.length < 8) {
                    columnCountError++;
                    continue;
                }

                try {
                    // 각 컬럼 값 추출 및 trim
                    String analystName = row[0].trim();
                    String firmName = row[1].trim();
                    String hiddenOpinionStr = row[2].trim();
                    String reportDateStr = row[3].trim();
                    String reportTitle = row[4].trim();
                    String stockCode = row[5].trim();
                    String surfaceOpinionStr = row[6].trim();
                    String targetPriceStr = row[7].trim();

                    // stockCode를 6자리로 패딩 (DB에는 007393 형식으로 저장됨)
                    if (stockCode.matches("\\d+")) {  // 숫자로만 이루어진 경우
                        stockCode = String.format("%06d", Integer.parseInt(stockCode));
                    }

                    // 컬럼이 하나라도 비어있으면 스킵
                    if (analystName.isEmpty() || firmName.isEmpty() || hiddenOpinionStr.isEmpty() ||
                            reportDateStr.isEmpty() || reportTitle.isEmpty() || stockCode.isEmpty() ||
                            surfaceOpinionStr.isEmpty() || targetPriceStr.isEmpty()) {
                        emptyColumnError++;
                        continue;
                    }

                    // analystName이 4글자 이상이면 스킵
                    if (analystName.length() >= 4) {
                        analystNameTooLongError++;
                        continue;
                    }

                    // AnalystInfo 생성
                    ReportRequestDTO.AnalystInfo analystInfo = ReportRequestDTO.AnalystInfo.builder()
                            .analystName(analystName)
                            .firmName(firmName)
                            .build();

                    // ReportInfo 생성
                    ReportRequestDTO.ReportInfo reportInfo = ReportRequestDTO.ReportInfo.builder()
                            .stockCode(stockCode)
                            .reportTitle(reportTitle)
                            .reportDate(LocalDate.parse(reportDateStr, dateFormatter))
                            .targetPrice(Integer.parseInt(targetPriceStr))
                            .surfaceOpinion(SurfaceOpinion.valueOf(surfaceOpinionStr.toUpperCase()))
                            .hiddenOpinion(Double.parseDouble(hiddenOpinionStr))
                            .build();

                    // ReportRequestDTO 생성
                    ReportRequestDTO requestDTO = ReportRequestDTO.builder()
                            .analyst(analystInfo)
                            .report(reportInfo)
                            .build();

                    result.add(requestDTO);
                    successCount++;
                } catch (Exception e) {
                    // 파싱 오류 발생 시 해당 행 스킵 (예: 날짜 형식 오류, 숫자 변환 오류 등)
                    parseError++;
                    System.err.println("⚠️ 파싱 오류 (행 " + (i + 1) + "): " + e.getMessage());
                    continue;
                }
            }
        }

        // 통계 출력
        System.out.println("\n📊 CSV 파싱 결과:");
        System.out.println("  - 총 행 수: " + totalRows);
        System.out.println("  - 성공: " + successCount + "개");
        System.out.println("  - 컬럼 수 부족: " + columnCountError + "개");
        System.out.println("  - 빈 컬럼: " + emptyColumnError + "개");
        System.out.println("  - 애널리스트명 4글자 이상: " + analystNameTooLongError + "개");
        System.out.println("  - 파싱 오류: " + parseError + "개");
        System.out.println("  - 스킵된 총 개수: " + (totalRows - successCount) + "개\n");

        return result;
    }
}

package capstone25_2.aim.domain.dto.report;

import capstone25_2.aim.domain.entity.HiddenOpinion;
import capstone25_2.aim.domain.entity.Report;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor @Builder
public class ReportResponseDTO {

    private Long reportId;
    private String analystName;
    private String firmName;
    private String reportTitle;
    private LocalDate reportDate;
    private Double surfaceOpinion;
    private Integer targetPrice;
    private HiddenOpinion hiddenOpinion;
    private Long prevReportId;


    public static ReportResponseDTO fromEntity(Report report) {
        return ReportResponseDTO.builder()
                .reportId(report.getId())
                .analystName(report.getAnalyst().getAnalystName())
                .firmName(report.getAnalyst().getFirmName())
                .reportTitle(report.getReportTitle())
                .reportDate(LocalDate.from(report.getReportDate()))
                .surfaceOpinion(report.getSurfaceOpinion())
                .targetPrice(report.getTargetPrice())
                .hiddenOpinion(report.getHiddenOpinion())
                .prevReportId(report.getPrevReport() != null ? report.getPrevReport().getId() : null)
                .build();
    }
}

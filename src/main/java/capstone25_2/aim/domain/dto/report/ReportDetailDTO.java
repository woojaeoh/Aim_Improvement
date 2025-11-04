package capstone25_2.aim.domain.dto.report;

import capstone25_2.aim.domain.entity.HiddenOpinion;
import capstone25_2.aim.domain.entity.Report;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor @Builder
public class ReportDetailDTO {

    private Long reportId;
    private String reportTitle;
    private LocalDate reportDate;
    private Double surfaceOpinion;
    private Integer targetPrice;
    private HiddenOpinion hiddenOpinion;
    private Long prevReportId;

    private String analystName;
    private String firmName;

    public static ReportDetailDTO fromEntity(Report report) {
        return ReportDetailDTO.builder()
                .reportId(report.getId())
                .reportTitle(report.getReportTitle())
                .reportDate(LocalDate.from(report.getReportDate()))
                .surfaceOpinion(report.getSurfaceOpinion())
                .targetPrice(report.getTargetPrice())
                .hiddenOpinion(report.getHiddenOpinion())
                .prevReportId(report.getPrevReport() != null ? report.getPrevReport().getId() : null)
                .analystName(report.getAnalyst().getAnalystName())
                .firmName(report.getAnalyst().getFirmName())
                .build();
    }

}

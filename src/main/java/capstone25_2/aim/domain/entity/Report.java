package capstone25_2.aim.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
public class Report {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    private String reportTitle;

    private LocalDateTime reportDate;

    @Enumerated(EnumType.STRING)
    private SurfaceOpinion surfaceOpinion; //BUY, HOLD, SELL

    private Integer targetPrice;

    private Double hiddenOpinion; //0.0 ~ 1.0 사이의 확률 값

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prev_report_id")
    private Report prevReport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analyst_id")
    private Analyst analyst;

}

package capstone25_2.aim.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
public class AnalystMetrics {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double accuracyRate; //정확도
    private Double returnRate; //수익률
    private Double targetDiffRate; //목표가 오차율
    private Double avgReturnDiff; //애널리스트 평균대비 수익률 오차
    private Double avgTargetDiff; //애널리스트 평균 대비 목표가 오차율
    private Integer aimsScore; //aim's score (40~100점)
    private Integer reportCount; //평가 가능한 리포트 개수

    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analyst_id")
    private Analyst analyst;

    @PrePersist
    public void prePersist() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

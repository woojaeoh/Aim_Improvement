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

    // ✅ 동시성 테스트용 카운터 (몇 번 업데이트 되었는지 추적)
    @Column(name = "update_count")
    private Integer updateCount = 0;

    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analyst_id")
    private Analyst analyst;

    // ✅ 업데이트 카운터 증가 메서드
    public void incrementUpdateCount() {
        this.updateCount = (this.updateCount == null ? 0 : this.updateCount) + 1;
    }

    @PrePersist
    public void prePersist() {
        this.updatedAt = LocalDateTime.now();
        if (this.updateCount == null) {
            this.updateCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

package capstone25_2.aim.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter @Setter
@NoArgsConstructor
@Table(
    name = "close_price",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_stock_trade_date",
            columnNames = {"stock_id", "trade_date"}
        )
    }
)
public class ClosePrice {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "close_price_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "close_price", nullable = false)
    private Integer closePrice;

}

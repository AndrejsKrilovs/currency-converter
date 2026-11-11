package krilovs.andrejs.app.rate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import krilovs.andrejs.app.currency.Currency;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
public class Rate {
    @EmbeddedId
    private RateId id;

    @MapsId("baseCurrency")
    @JoinColumn(name = "base_currency")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Currency baseCurrency;

    @MapsId("targetCurrency")
    @JoinColumn(name = "target_currency")
    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.ALL)
    private Currency targetCurrency;

    private BigDecimal rate;
}

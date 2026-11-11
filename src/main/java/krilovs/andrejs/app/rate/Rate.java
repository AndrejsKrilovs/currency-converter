package krilovs.andrejs.app.rate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.CascadeType;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import krilovs.andrejs.app.currency.Currency;
import krilovs.andrejs.app.util.PlainDecimalSerializer;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@EqualsAndHashCode(of = {"baseCurrency", "targetCurrency", "rate"})
public class Rate {
    @EmbeddedId
    @JsonIgnore
    private RateId id;

    @MapsId("baseCurrency")
    @JoinColumn(name = "base_currency")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Currency baseCurrency;

    @MapsId("targetCurrency")
    @JoinColumn(name = "target_currency")
    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.ALL)
    private Currency targetCurrency;

    @JsonSerialize(using = PlainDecimalSerializer.class)
    private BigDecimal rate;
}

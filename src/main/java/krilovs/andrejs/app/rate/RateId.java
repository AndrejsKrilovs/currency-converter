package krilovs.andrejs.app.rate;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** Composite key of {@link Rate}; field names must match the @MapsId values, types are the Currency primary key. */
@Getter
@Setter
@Embeddable
@EqualsAndHashCode
public class RateId implements Serializable {
    private String baseCurrency;
    private String targetCurrency;
}

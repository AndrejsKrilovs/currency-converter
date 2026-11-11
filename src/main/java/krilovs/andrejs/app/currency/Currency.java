package krilovs.andrejs.app.currency;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Cacheable
public class Currency {
    @Id
    private String code;
    private String name;
    private String symbol;
}

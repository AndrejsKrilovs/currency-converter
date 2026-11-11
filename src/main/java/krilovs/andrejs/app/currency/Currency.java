package krilovs.andrejs.app.currency;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@Entity
@Cacheable
@EqualsAndHashCode(of = {"code"})
public class Currency {
    @Id
    @NotBlank(message = "Currency code should be defined")
    @Size(min = 3, max = 3, message = "Currency code must contain 3 characters")
    private String code;

    @NotBlank(message = "Currency name be defined")
    private String name;
    private String symbol;
}

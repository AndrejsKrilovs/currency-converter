package krilovs.andrejs.app.util;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import lombok.experimental.UtilityClass;

/** Explicit Bean Validation: nothing triggers @Valid inside a plain servlet, so the check is called by hand. */
@UtilityClass
public class BeanValidator {
    /** Returns the object itself when valid, otherwise throws ConstraintViolationException (mapped to 400). */
    public static <T> T validate(T value) {
        try (var validationFactory =  Validation.buildDefaultValidatorFactory()) {
            var violations = validationFactory.getValidator().validate(value);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
        }
        return value;
    }
}

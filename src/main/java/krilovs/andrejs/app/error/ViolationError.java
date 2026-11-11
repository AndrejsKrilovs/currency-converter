package krilovs.andrejs.app.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;


public record ViolationError(String detail, TreeMap<String, List<String>> errors) {
    public static ViolationError of(ConstraintViolationException exception) {
        var errors = exception.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        it -> it.getPropertyPath().toString(),
                        TreeMap::new,
                        Collectors.collectingAndThen(
                                Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList()),
                                messages -> messages.stream().sorted().toList()
                        )
                ));
        return new ViolationError("Validation errors", errors);
    }
}

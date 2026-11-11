package krilovs.andrejs.app.error;

import lombok.Getter;

import java.util.NoSuchElementException;

@Getter
public class NoEntityException extends NoSuchElementException {
    private final String message;
    private final Object value;

    public NoEntityException(String message, Object value) {
        super(message);
        this.message = message;
        this.value = value;
    }
}

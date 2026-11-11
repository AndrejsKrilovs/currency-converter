package krilovs.andrejs.app.error;

import lombok.Getter;

@Getter
public class IncorrectPathException extends RuntimeException {
    private final String message;

    public IncorrectPathException(String message) {
        super(message);
        this.message = message;
    }
}

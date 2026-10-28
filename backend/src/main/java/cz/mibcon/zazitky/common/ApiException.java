package cz.mibcon.zazitky.common;

import org.springframework.http.HttpStatus;

/** Business error that {@link ApiExceptionHandler} turns into a {@code ProblemDetail}. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String typeSuffix;
    private final String title;

    protected ApiException(HttpStatus status, String typeSuffix, String title, String detail) {
        super(detail);
        this.status = status;
        this.typeSuffix = typeSuffix;
        this.title = title;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTypeSuffix() {
        return typeSuffix;
    }

    public String getTitle() {
        return title;
    }
}

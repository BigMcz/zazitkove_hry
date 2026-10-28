package cz.mibcon.zazitky.common;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Returns all API errors as RFC 9457 {@link ProblemDetail}. The {@code type} is a relative URI
 * {@code /problems/<suffix>}, the suffixes are listed in docs/02-architektura.md.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String TYPE_PREFIX = "/problems/";

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Nenalezeno", ex.getMessage());
    }

    /** Unifies Spring's built-in 400/404 responses (bad JSON, invalid UUID in path, unknown URL…) with ours. */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.getBody();
        }
        if (body instanceof ProblemDetail problem) {
            if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
                problem.setType(URI.create(TYPE_PREFIX + "validation"));
                problem.setTitle("Neplatný požadavek");
                if (ex instanceof MethodArgumentNotValidException invalid) {
                    problem.setDetail("Požadavek obsahuje neplatné hodnoty.");
                    problem.setProperty("errors", fieldErrors(invalid));
                }
            } else if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
                problem.setType(URI.create(TYPE_PREFIX + "not-found"));
                problem.setTitle("Nenalezeno");
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    public static ProblemDetail problem(HttpStatus status, String typeSuffix, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + typeSuffix));
        problem.setTitle(title);
        return problem;
    }

    private static List<Map<String, String>> fieldErrors(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();
    }
}

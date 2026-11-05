package hu.eszkozpont.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.net.URI;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    public record FieldError(String field, String message) {}

    public static ProblemDetail problem(HttpStatus status, String code, String message) {
        var detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(switch (status) {
            case BAD_REQUEST -> "Hibás kérés";
            case UNAUTHORIZED -> "Bejelentkezés szükséges";
            case FORBIDDEN -> "Nincs jogosultság";
            case NOT_FOUND -> "Nem található";
            case CONFLICT -> "Adatütközés";
            default -> "A kérés nem teljesíthető";
        });
        detail.setType(URI.create("urn:eszkozpont:problem:" + code));
        detail.setProperty("code", code);
        detail.setProperty("errors", List.of());
        return detail;
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException exception) {
        return problem(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccess(AccessDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Ehhez a művelethez nincs jogosultságod.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "DATA_CONFLICT", "Az adatok már létező vagy hivatkozott rekorddal ütköznek.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraint(ConstraintViolationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Ellenőrizd a megadott paramétereket.");
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail handleConcurrentChange(Exception exception) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_CHANGE", "Az adatot másik művelet módosítja. Frissítsd az oldalt, és próbáld újra.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var detail = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Ellenőrizd a megjelölt mezőket.");
        detail.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage())).toList());
        return handleExceptionInternal(exception, detail, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var detail = body instanceof ProblemDetail p && p.getProperties() != null && p.getProperties().containsKey("code")
                ? p : problem(HttpStatus.valueOf(status.value()), "INVALID_REQUEST",
                "A kérés formátuma, paraméterei vagy HTTP-metódusa nem megfelelő.");
        return super.handleExceptionInternal(exception, detail, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Váratlan hiba a kérés feldolgozásakor", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Váratlan hiba történt. Kérjük, próbáld újra később.");
    }
}

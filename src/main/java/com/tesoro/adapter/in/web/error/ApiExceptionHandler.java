package com.tesoro.adapter.in.web.error;

import com.tesoro.application.exception.ApplicationException;
import com.tesoro.application.exception.EmailAlreadyRegisteredException;
import com.tesoro.application.exception.ExchangeRateUnavailableException;
import com.tesoro.application.exception.IdempotencyKeyReusedException;
import com.tesoro.application.exception.InvalidCredentialsException;
import com.tesoro.application.exception.InvalidRefreshTokenException;
import com.tesoro.application.exception.NotFoundException;
import com.tesoro.domain.exception.AccountFrozenException;
import com.tesoro.domain.exception.CurrencyMismatchException;
import com.tesoro.domain.exception.DomainException;
import com.tesoro.domain.exception.InsufficientFundsException;
import com.tesoro.domain.exception.InvalidAmountException;
import com.tesoro.domain.exception.SameAccountTransferException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Translates exceptions into RFC 9457 {@code application/problem+json} responses. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final Map<Class<? extends RuntimeException>, HttpStatus> STATUS = Map.ofEntries(
            Map.entry(InsufficientFundsException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(AccountFrozenException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(SameAccountTransferException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(InvalidAmountException.class, HttpStatus.BAD_REQUEST),
            Map.entry(CurrencyMismatchException.class, HttpStatus.BAD_REQUEST),
            Map.entry(NotFoundException.class, HttpStatus.NOT_FOUND),
            Map.entry(EmailAlreadyRegisteredException.class, HttpStatus.CONFLICT),
            Map.entry(InvalidCredentialsException.class, HttpStatus.UNAUTHORIZED),
            Map.entry(InvalidRefreshTokenException.class, HttpStatus.UNAUTHORIZED),
            Map.entry(IdempotencyKeyReusedException.class, HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry(ExchangeRateUnavailableException.class, HttpStatus.SERVICE_UNAVAILABLE));

    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("insufficient-funds", "Saldo insuficiente"),
            Map.entry("account-frozen", "Cuenta congelada"),
            Map.entry("same-account-transfer", "Transferencia inválida"),
            Map.entry("invalid-amount", "Monto inválido"),
            Map.entry("currency-mismatch", "Moneda inválida"),
            Map.entry("not-found", "No encontrado"),
            Map.entry("email-already-registered", "Email ya registrado"),
            Map.entry("invalid-credentials", "Credenciales inválidas"),
            Map.entry("invalid-refresh-token", "Sesión inválida"),
            Map.entry("idempotency-key-reused", "Clave de idempotencia reutilizada"),
            Map.entry("exchange-rate-unavailable", "Cotización no disponible"));

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e, HttpServletRequest request) {
        HttpStatus status = STATUS.getOrDefault(e.getClass(), HttpStatus.UNPROCESSABLE_ENTITY);
        if (!STATUS.containsKey(e.getClass())) {
            // e.g. an unbalanced entry: a bug, never a user error
            log.error("Unexpected domain error", e);
            return Problems.of(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "internal-error",
                    "Error interno",
                    "No pudimos procesar la operación",
                    request);
        }
        return Problems.of(status, e.code(), TITLES.getOrDefault(e.code(), e.code()), e.getMessage(), request);
    }

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<ProblemDetail> application(ApplicationException e, HttpServletRequest request) {
        HttpStatus status = STATUS.getOrDefault(e.getClass(), HttpStatus.BAD_REQUEST);
        ProblemDetail problem =
                Problems.of(status, e.code(), TITLES.getOrDefault(e.code(), e.code()), e.getMessage(), request);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            response.header(HttpHeaders.RETRY_AFTER, "30");
        }
        return response.body(problem);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ProblemDetail lockFailure(PessimisticLockingFailureException e, HttpServletRequest request) {
        log.warn("Lock failure: {}", e.getMostSpecificCause().getMessage());
        return Problems.of(
                HttpStatus.CONFLICT,
                "concurrent-modification",
                "Operación concurrente",
                "La cuenta está siendo modificada por otra operación. Reintentá con la misma clave de idempotencia",
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Integrity violation: {}", e.getMostSpecificCause().getMessage());
        return Problems.of(
                HttpStatus.CONFLICT,
                "conflict",
                "Conflicto",
                "La operación entra en conflicto con el estado actual de los datos",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return Problems.of(
                HttpStatus.BAD_REQUEST,
                "invalid-parameter",
                "Parámetro inválido",
                "El parámetro '%s' tiene un valor inválido".formatted(e.getName()),
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException e, HttpServletRequest request) {
        ProblemDetail problem = Problems.of(
                HttpStatus.BAD_REQUEST, "validation-failed", "Datos inválidos", "Revisá los campos marcados", request);
        problem.setProperty(
                "errors",
                e.getConstraintViolations().stream()
                        .map(v -> new FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                        .toList());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return Problems.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Error interno",
                "Ocurrió un error inesperado",
                request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = Problems.of(
                HttpStatus.BAD_REQUEST, "validation-failed", "Datos inválidos", "Revisá los campos marcados");
        List<FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        problem.setInstance(instance(request));
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = Problems.of(
                HttpStatus.BAD_REQUEST, "validation-failed", "Datos inválidos", "Revisá los parámetros enviados");
        List<FieldError> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        problem.setInstance(instance(request));
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            org.springframework.web.bind.ServletRequestBindingException e,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (e instanceof MissingRequestHeaderException missing) {
            ProblemDetail problem = Problems.of(
                    HttpStatus.BAD_REQUEST,
                    "missing-header",
                    "Falta un header",
                    "El header '%s' es obligatorio".formatted(missing.getHeaderName()));
            problem.setInstance(instance(request));
            return ResponseEntity.badRequest().body(problem);
        }
        return super.handleServletRequestBindingException(e, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // Framework errors (malformed JSON, 405, 415...) also get a stable "code".
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            problem.setProperty("code", "request-error");
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static URI instance(WebRequest request) {
        return URI.create(request.getDescription(false).replaceFirst("^uri=", ""));
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    record FieldError(String field, String message) {}
}

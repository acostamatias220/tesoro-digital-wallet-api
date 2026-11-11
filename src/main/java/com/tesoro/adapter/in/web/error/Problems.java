package com.tesoro.adapter.in.web.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Factory for RFC 9457 problem details with a stable {@code code} the clients can switch on. */
public final class Problems {

    public static final String TYPE_PREFIX = "urn:tesoro:problem:";

    private Problems() {}

    public static ProblemDetail of(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code));
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    public static ProblemDetail of(
            HttpStatus status, String code, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = of(status, code, title, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}

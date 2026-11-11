package com.tesoro.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tesoro.adapter.in.web.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Writes 401 and 403 responses as RFC 9457 problem details, like every other API error. */
@Component
class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    ProblemSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(
                response,
                Problems.of(
                        HttpStatus.UNAUTHORIZED,
                        "unauthorized",
                        "No autenticado",
                        "Falta el token de acceso o es inválido / venció",
                        request));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        write(
                response,
                Problems.of(
                        HttpStatus.FORBIDDEN,
                        "forbidden",
                        "Acceso denegado",
                        "No tenés permisos para esta operación",
                        request));
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

package com.tesoro.adapter.out.request;

import com.tesoro.application.port.out.CallerContext;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Client IP of the current HTTP request (honours X-Forwarded-For only if the server trusts it). */
@Component
class RequestCallerContext implements CallerContext {

    @Override
    public Optional<String> ipAddress() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.ofNullable(attributes.getRequest().getRemoteAddr());
        }
        return Optional.empty();
    }
}

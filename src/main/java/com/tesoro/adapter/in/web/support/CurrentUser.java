package com.tesoro.adapter.in.web.support;

import com.tesoro.application.model.Requester;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Helpers to turn the validated JWT into the ids the use cases expect. */
public final class CurrentUser {

    private CurrentUser() {}

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public static Requester requester(Jwt jwt) {
        var roles = jwt.getClaimAsStringList("roles");
        return new Requester(id(jwt), roles != null && roles.contains("ADMIN"));
    }
}

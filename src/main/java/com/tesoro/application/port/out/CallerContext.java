package com.tesoro.application.port.out;

import java.util.Optional;

/** Information about the current caller (e.g. client IP) when the call comes from a request. */
public interface CallerContext {

    Optional<String> ipAddress();
}

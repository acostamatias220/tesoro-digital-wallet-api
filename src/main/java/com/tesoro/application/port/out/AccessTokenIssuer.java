package com.tesoro.application.port.out;

import com.tesoro.application.model.IssuedToken;
import com.tesoro.domain.model.User;

public interface AccessTokenIssuer {

    IssuedToken issue(User user);
}

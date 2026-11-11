package com.tesoro.application.port.in;

import com.tesoro.application.command.RegisterCommand;
import com.tesoro.application.model.AuthTokens;
import com.tesoro.domain.model.User;
import java.util.UUID;

public interface AuthUseCase {

    AuthTokens register(RegisterCommand command);

    AuthTokens login(String email, String password);

    AuthTokens refresh(String refreshToken);

    void logout(String refreshToken);

    User currentUser(UUID userId);
}

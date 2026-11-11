package com.tesoro.application.model;

import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Direction;
import java.math.BigDecimal;
import java.util.UUID;

/** How one of the caller's wallets changed as a result of a movement. */
public record WalletChange(
        UUID accountId, CurrencyCode currency, Direction direction, BigDecimal amount, BigDecimal balanceAfter) {}

package com.tesoro.application.model;

import com.tesoro.domain.model.AccountKind;
import com.tesoro.domain.model.CurrencyCode;
import java.math.BigDecimal;
import java.util.UUID;

/** Balance of a system account, derived from its postings on its normal side. */
public record SystemAccountBalance(
        UUID accountId, String code, AccountKind kind, CurrencyCode currency, BigDecimal balance) {}

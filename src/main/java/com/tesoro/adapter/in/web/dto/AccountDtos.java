package com.tesoro.adapter.in.web.dto;

import com.tesoro.domain.ledger.EntryType;
import com.tesoro.domain.model.AccountStatus;
import com.tesoro.domain.model.CurrencyCode;
import com.tesoro.domain.model.Direction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AccountDtos {

    private AccountDtos() {}

    public record AccountResponse(
            UUID id, CurrencyCode currency, BigDecimal balance, AccountStatus status, Instant createdAt) {}

    public record MovementLineResponse(
            long id,
            UUID transactionId,
            EntryType type,
            Direction direction,
            BigDecimal amount,
            CurrencyCode currency,
            BigDecimal balanceAfter,
            String description,
            Instant createdAt) {}

    public record TransactionResponse(
            UUID id,
            EntryType type,
            String description,
            UUID initiatedBy,
            Map<String, String> metadata,
            List<PostingResponse> postings,
            Instant createdAt) {}

    public record PostingResponse(
            UUID accountId, String account, CurrencyCode currency, Direction direction, BigDecimal amount) {}
}

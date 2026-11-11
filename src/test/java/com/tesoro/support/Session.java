package com.tesoro.support;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.UUID;

/** An authenticated user in the tests, with shortcuts for the common calls. */
public record Session(Api api, String email, String accessToken, String refreshToken) {

    public Api.Response get(String path) {
        return api.get(path, accessToken);
    }

    public Api.Response post(String path, String idempotencyKey, String body) {
        return api.post(path, accessToken, idempotencyKey, body);
    }

    public Api.Response deposit(String currency, String amount) {
        return post(
                "/api/v1/deposits",
                UUID.randomUUID().toString(),
                "{\"currency\": \"%s\", \"amount\": %s}".formatted(currency, amount));
    }

    public Api.Response withdraw(String currency, String amount, String key) {
        return post("/api/v1/withdrawals", key, "{\"currency\": \"%s\", \"amount\": %s}".formatted(currency, amount));
    }

    public Api.Response transfer(String toEmail, String currency, String amount, String key) {
        return post(
                "/api/v1/transfers",
                key,
                "{\"recipientEmail\": \"%s\", \"currency\": \"%s\", \"amount\": %s}"
                        .formatted(toEmail, currency, amount));
    }

    public Api.Response convert(String from, String to, String amount, String key) {
        return post(
                "/api/v1/conversions",
                key,
                "{\"fromCurrency\": \"%s\", \"toCurrency\": \"%s\", \"amount\": %s}".formatted(from, to, amount));
    }

    public JsonNode wallet(String currency) {
        for (JsonNode account : get("/api/v1/accounts").expectStatus(200).body()) {
            if (account.get("currency").asText().equals(currency)) {
                return account;
            }
        }
        throw new AssertionError("No wallet in " + currency);
    }

    public String walletId(String currency) {
        return wallet(currency).get("id").asText();
    }

    public BigDecimal balance(String currency) {
        return wallet(currency).get("balance").decimalValue();
    }
}

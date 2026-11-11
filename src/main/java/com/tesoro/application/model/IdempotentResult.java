package com.tesoro.application.model;

/** Wraps a result telling whether it was produced now or replayed from a previous request. */
public record IdempotentResult<T>(T value, boolean replayed) {

    public static <T> IdempotentResult<T> fresh(T value) {
        return new IdempotentResult<>(value, false);
    }

    public static <T> IdempotentResult<T> replayed(T value) {
        return new IdempotentResult<>(value, true);
    }
}

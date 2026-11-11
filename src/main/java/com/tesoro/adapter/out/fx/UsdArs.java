package com.tesoro.adapter.out.fx;

import com.tesoro.domain.model.CurrencyCode;

final class UsdArs {

    private UsdArs() {}

    static void require(CurrencyCode base, CurrencyCode quote) {
        if (base != CurrencyCode.USD || quote != CurrencyCode.ARS) {
            throw new IllegalArgumentException("Only the USD/ARS pair is supported, got %s/%s".formatted(base, quote));
        }
    }
}

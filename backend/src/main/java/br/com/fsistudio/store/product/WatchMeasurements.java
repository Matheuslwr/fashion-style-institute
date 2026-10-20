package br.com.fsistudio.store.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;

public record WatchMeasurements(
    @DecimalMin("0.01") @Digits(integer = 4, fraction = 2) BigDecimal widthCm,
    @DecimalMin("0.01") @Digits(integer = 4, fraction = 2) BigDecimal lengthCm,
    @DecimalMin("0.01") @Digits(integer = 4, fraction = 2) BigDecimal heightCm,
    @DecimalMin("0.01") @Digits(integer = 4, fraction = 2) BigDecimal dialCm
) {
    public boolean isComplete() {
        return isPositive(widthCm) && isPositive(lengthCm) && isPositive(heightCm) && isPositive(dialCm);
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}

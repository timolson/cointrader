package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.enumeration.FeeMethod;

public class Prompts {
    // okcoin prompt dates for derivatives
    public static final Prompt THIS_WEEK = prompt("THISWEEK", 1, 0.01, "BTC", 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003,
            FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);
    public static final Prompt NEXT_WEEK = prompt("NEXTWEEK", 1, 0.01, "BTC", 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003,
            FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);
    public static final Prompt MONTH = prompt("MONTH", 1, 0.01, "BTC", 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003,
            FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);
    public static final Prompt QUARTER = prompt("QUARTER", 1, 0.01, "BTC", 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003,
            FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);

    private static Prompt prompt(String symbol, double tickValue, double tickSize, String currency, double volumeBasis, double priceBasis, int margin,
            FeeMethod marginMethod, double feeRate, FeeMethod feeMethod, FeeMethod marginFeeMethod) {
        return Prompt.forSymbolOrCreate(symbol, tickValue, tickSize, currency, volumeBasis, priceBasis, margin, marginMethod, feeRate, feeMethod,
                marginFeeMethod);
    }

    private static Prompt prompt(String symbol, double tickValue, double tickSize, double volumeBasis, double priceBasis, int margin, FeeMethod marginMethod,
            double feeRate, FeeMethod feeMethod, FeeMethod marginFeeMethod) {
        return Prompt.forSymbolOrCreate(symbol, tickValue, tickSize, volumeBasis, priceBasis, margin, marginMethod, feeRate, feeMethod, marginFeeMethod);
    }

}

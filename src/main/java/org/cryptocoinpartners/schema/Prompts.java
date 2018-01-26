package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.enumeration.FeeMethod;

public class Prompts {
	// okcoin prompt dates for derivatives
	public static final Prompt THIS_WEEK = prompt("THISWEEK", 1, 0.01, null, 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003, 0.0003,
			FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);
	public static final Prompt NEXT_WEEK = prompt("NEXTWEEK", 1, 0.01, null, 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003, 0.0003,
			FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);
	public static final Prompt MONTH = prompt("MONTH", 1, 0.01, null, 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003, 0.0003,
			FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);
	public static final Prompt QUARTER = prompt("QUARTER", 1, 0.01, null, 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003, 0.0003,
			FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);
	public static final Prompt PERPETUAL = prompt("PERPETUAL", 1, 0.01, null, 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003, 0.0003,
			FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);
	public static final Prompt MONTHLY = prompt("MONTHLY", 1, 0.01, null, 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003, 0.0003,
			FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);
	public static final Prompt WEEKLY = prompt("WEEKLY", 1, 0.01, null, 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003, 0.0003,
			FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);
	public static final Prompt QUARTERLY = prompt("QUARTERLY", 1, 0.01, null, 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003, 0.0003,
			FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);
	public static final Prompt BIQUARTERLY = prompt("BIQUARTERLY", 1, 0.01, null, 1, 0.0001, 20, FeeMethod.PercentagePerUnitOpening, 0.0003, 0.0003,
			FeeMethod.PercentagePerUnitOpening, FeeMethod.PercentagePerUnitOpening);

	private static Prompt prompt(String symbol, double tickValue, double tickSize, String currency, double volumeBasis, double priceBasis, int margin,
			FeeMethod marginMethod, double makerFeeRate, double takerFeeRate, FeeMethod feeMethod, FeeMethod marginFeeMethod) {
		return Prompt.forSymbolOrCreate(symbol, tickValue, tickSize, currency, volumeBasis, priceBasis, margin, marginMethod, makerFeeRate, takerFeeRate,
				feeMethod, marginFeeMethod);
	}

	private static Prompt prompt(String symbol, double tickValue, double tickSize, double volumeBasis, double priceBasis, int margin, FeeMethod marginMethod,
			double makerFeeRate, double takerFeeRate, FeeMethod feeMethod, FeeMethod marginFeeMethod) {
		return Prompt.forSymbolOrCreate(symbol, tickValue, tickSize, volumeBasis, priceBasis, margin, marginMethod, makerFeeRate, takerFeeRate, feeMethod,
				marginFeeMethod);
	}

}

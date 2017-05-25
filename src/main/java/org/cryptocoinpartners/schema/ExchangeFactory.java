package org.cryptocoinpartners.schema;

import org.cryptocoinpartners.enumeration.FeeMethod;

import com.google.inject.assistedinject.Assisted;

public interface ExchangeFactory {

    Exchange create(String symbol);

    Exchange create(String symbol, int margin, double feeRate, FeeMethod feeMethod, boolean fillsProvided);

    Exchange create(String symbol, int margin, @Assisted("feeRate") double feeRate, @Assisted("feeMethod") FeeMethod feeMethod,
            @Assisted("marginFeeRate") double marginFeeRate, @Assisted("marginFeeMethod") FeeMethod marginFeeMethod, boolean fillsProvided);

}

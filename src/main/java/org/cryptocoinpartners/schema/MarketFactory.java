package org.cryptocoinpartners.schema;

import com.google.inject.assistedinject.Assisted;

public interface MarketFactory {

  Market create(Exchange exchange, Listing listing, @Assisted("marketPriceBasis") double priceBasis, @Assisted("marketVolumeBasis") double volumeBasis);

  Market create(Exchange exchange, Listing listing, @Assisted("marketPriceBasis") double priceBasis,
      @Assisted("marketVolumeBasis") double volumeBasis, @Assisted("minimumOrderSize") double minimumOrderSize);

}

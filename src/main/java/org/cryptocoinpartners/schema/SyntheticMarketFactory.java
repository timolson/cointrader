package org.cryptocoinpartners.schema;

import java.util.List;

public interface SyntheticMarketFactory {

    SyntheticMarket create(String exchange, List<Market> markets);

}

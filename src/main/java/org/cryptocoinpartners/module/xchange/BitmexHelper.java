package org.cryptocoinpartners.module.xchange;

import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.knowm.xchange.bitmex.BitmexPrompt;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.Order.OrderType;

@SuppressWarnings("UnusedDeclaration")
public class BitmexHelper extends XchangeHelperBase {

	@Override
	public OrderType getOrderType(SpecificOrder specificOrder) {
		if (specificOrder.getPositionEffect() == null || specificOrder.getPositionEffect() == PositionEffect.OPEN)
			return specificOrder.isBid() ? Order.OrderType.BID : Order.OrderType.ASK;
		else if (specificOrder.getPositionEffect() == PositionEffect.CLOSE)
			return specificOrder.isAsk() ? Order.OrderType.EXIT_BID : Order.OrderType.EXIT_ASK;
		return null;
	}

	@Override
	public BitmexPrompt getContractForListing(Listing listing) {

		return BitmexPrompt.valueOfIgnoreCase(BitmexPrompt.class, listing.getPrompt().getSymbol());
	}

	@Override
	public Object[] getTradesParameters(Listing listing, final long lastTradeTime, long lastTradeId) {
		BitmexPrompt contract = getContractForListing(listing);
		return new BitmexPrompt[] { getContractForListing(listing) };

	}

	@Override
	public Object[] getOrderBookParameters(Listing listing) {
		BitmexPrompt contract = getContractForListing(listing);
		return new BitmexPrompt[] { getContractForListing(listing) };
	}

	@Override
	public SpecificOrder adjustOrder(SpecificOrder specificOrder) {

		return specificOrder;
	}

}

package org.cryptocoinpartners.module;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.cryptocoinpartners.enumeration.OrderState;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Offer;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderUpdate;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.service.QuoteService;
import org.slf4j.Logger;

/**
 * MockOrderService simulates the Filling of Orders by looking at broadcast Book data for price and volume information.
 *
 * @author Tim Olson
 */
@Singleton
@SuppressWarnings("UnusedDeclaration")
public class MockOrderService extends BaseOrderService {

	@Override
	protected void handleSpecificOrder(SpecificOrder specificOrder) {
		if (specificOrder.getStopPrice() != null)
			reject(specificOrder, "Stop prices unsupported");

		pendingOrders.add(specificOrder);
		updateOrderState(specificOrder, OrderState.PLACED);
		specificOrder.setEntryTime(context.getTime());
	}

	@SuppressWarnings("ConstantConditions")
	@When("select * from Book")
	private void handleBook(Book b) {
		List<Fill> fills = Collections.synchronizedList(new ArrayList<Fill>());

		// todo multiple Orders may be filled with the same Offer.  We should deplete the Offers as we fill
		for (SpecificOrder order : pendingOrders) {
			if (order.getMarket().equals(b.getMarket())) {
				if (order.isBid()) {
					long remainingVolume = order.getUnfilledVolumeCount();
					for (Offer ask : b.getAsks()) {
						if (order.getLimitPrice() != null && order.getLimitPrice().getCount() < ask.getPriceCount())
							break;
						long fillVolume = Math.min(Math.abs(ask.getVolumeCount()), remainingVolume);
						Fill fill = new Fill(order, ask.getTime(), ask.getMarket(), ask.getPriceCount(), fillVolume);
						fills.add(fill);
						remainingVolume -= fillVolume;
						logFill(order, ask, fill);
						if (remainingVolume == 0)
							break;
					}
				}
				if (order.isAsk()) {
					long remainingVolume = order.getUnfilledVolumeCount(); // this will be negative
					for (Offer bid : b.getBids()) {
						if (order.getLimitPrice() != null && order.getLimitPrice().getCount() > bid.getPriceCount())
							break;
						long fillVolume = -Math.min(bid.getVolumeCount(), Math.abs(remainingVolume));

						Fill fill = new Fill(order, bid.getTime(), bid.getMarket(), bid.getPriceCount(), fillVolume);

						fills.add(fill);
						remainingVolume -= fillVolume;
						logFill(order, bid, fill);
						if (remainingVolume == 0)
							break;

					}
				}
			}
		}
		for (Fill fill : fills) {
			fill.getOrder().addFill(fill);
			context.publish(fill);
		}
	}

	private void removeOrder(Order order) {
		synchronized (pendingOrders) {
			pendingOrders.remove(order);
		}

	}

	@When("select * from OrderUpdate where state.open=false and NOT (OrderUpdate.state = OrderState.CANCELLED)")
	private void completeOrder(OrderUpdate update) {
		OrderState orderState = update.getState();
		Order order = update.getOrder();

		switch (orderState) {
			case CANCELLING:
				removeOrder(order);
				updateOrderState(order, OrderState.CANCELLED);
			default:
				removeOrder(order);
				break;
		}

	}

	private void logFill(SpecificOrder order, Offer offer, Fill fill) {
		if (log.isDebugEnabled())
			log.debug("Mock fill of Order " + order + " with Offer " + offer + ": " + fill);
	}

	@Inject
	private Logger log;

	private final Collection<SpecificOrder> pendingOrders = Collections.synchronizedList(new ArrayList<SpecificOrder>());
	private QuoteService quotes;

	@Override
	public Collection<SpecificOrder> getPendingOrders(Portfolio portfolio) {
		Iterator<SpecificOrder> it = pendingOrders.iterator();
		Collection<SpecificOrder> portfolioPendingOrders = new ArrayList<>();
		synchronized (pendingOrders) {

			while (it.hasNext()) {
				SpecificOrder pendingOrder = it.next();
				if (pendingOrder.getPortfolio().equals(portfolio)) {

					portfolioPendingOrders.add(pendingOrder);
				}
			}
		}
		return portfolioPendingOrders;
	}

	@Override
	public void handleCancelSpecificOrder(SpecificOrder specificOrder) {
		Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
		synchronized (pendingOrders) {
			for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
				SpecificOrder cancelledOrder = it.next();
				if (cancelledOrder.equals(specificOrder))
					cancelledOrders.add(cancelledOrder);

			}

			pendingOrders.removeAll(cancelledOrders);
			updateOrderState(specificOrder, OrderState.CANCELLED);

		}

	}

	@Override
	public void handleCancelAllSpecificOrders(Portfolio portfolio, Market market) {
		Collection<SpecificOrder> cancelledOrders = new ArrayList<>();
		synchronized (pendingOrders) {
			for (Iterator<SpecificOrder> it = pendingOrders.iterator(); it.hasNext();) {
				SpecificOrder specificOrder = it.next();
				if (specificOrder.getMarket().equals(market))
					//cancelledOrders.add(specificOrder);
					updateOrderState(specificOrder, OrderState.CANCELLING);
			}
			//			for (Iterator<SpecificOrder> it = cancelledOrders.iterator(); it.hasNext();) {
			//				SpecificOrder specificOrder = it.next();
			//
			//				
			//			}
		}
	}
}

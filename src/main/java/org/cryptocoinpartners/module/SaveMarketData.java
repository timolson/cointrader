package org.cryptocoinpartners.module;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.MarketData;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.util.PersistUtil;
import org.slf4j.Logger;

/**
 * @author Tim Olson
 */
@Singleton
public class SaveMarketData {

	@When("select * from MarketData")
	public void handleMarketData(MarketData m) {
		PersistUtil persistUtil = new PersistUtil();

		if (m instanceof Trade) {
			Trade trade = (Trade) m;
			final Trade duplicate = persistUtil.queryZeroOne(Trade.class, "select t from Trade t where market=?1 and remoteKey=?2", trade.getMarket(),
					trade.getRemoteKey());
			if (duplicate == null)
				persistUtil.insert(trade);
			//else
			//log.warn("dropped duplicate Trade " + trade);
			//	} else if (m instanceof Book) {

		}

		else if (m instanceof Book) {
			Book book = (Book) m;
			//if (book.getParent() != null)
			//PersistUtil.insert(book.getParent());
			persistUtil.insert(book);

		} else { // if not a Trade, persist unconditionally
			try {
				persistUtil.insert(m);
			} catch (Throwable e) {
				throw new Error("Could not insert " + m, e);
			}
		}
	}

	@Inject
	private Logger log;
}

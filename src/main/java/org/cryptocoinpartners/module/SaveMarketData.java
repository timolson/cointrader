package org.cryptocoinpartners.module;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.inject.Singleton;

import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.BarFactory;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.MarketData;
import org.cryptocoinpartners.schema.Trade;
import org.cryptocoinpartners.schema.dao.BarJpaDao;
import org.cryptocoinpartners.schema.dao.BookJpaDao;
import org.cryptocoinpartners.schema.dao.MarketDataJpaDao;
import org.cryptocoinpartners.schema.dao.TradeJpaDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * @author Tim Olson
 */
@Singleton
public class SaveMarketData {

	private static ExecutorService tradeService;
	private static ExecutorService bookService;
	private static ExecutorService barService;
	static Future future;
	private static MarketData lastMktData = null;

	@Inject
	protected BarFactory barFactory;

	@Inject
	protected BookJpaDao bookDao;

	@Inject
	protected TradeJpaDao tradeDao;

	@Inject
	protected MarketDataJpaDao marketDataDao;

	@Inject
	protected BarJpaDao barDao;
	@Inject
	protected transient Context context;

	/*static {
		tradeService = Executors.newFixedThreadPool(ConfigUtil.combined().getInt("db.trade.writer.threads", 1));
		bookService = Executors.newFixedThreadPool(ConfigUtil.combined().getInt("db.book.writer.threads", 1));
	
		barService = Executors.newFixedThreadPool(ConfigUtil.combined().getInt("db.bar.writer.threads", 1));
	}
	*/
	//@When("select * from MarketData")
	// @When("select * from MarketData")
	@When("@Priority(1) @Audit  select * from Book")
	public void handleBook(Book m) {

		//  if (future == null || future.isDone()) {
		//Future future = 
		log.trace("book recieved: " + m.getUuid() + " thread: " + Thread.currentThread().getName());
		try {
			m.persit();
		} catch (Error | Exception ex) {
			log.debug("SaveTradeRunnable:saveData - Book " + m + " not persisted");

		}

	}

	@When("@Priority(1) @Audit select * from Trade")
	public void handleTrade(Trade m) {

		log.trace("trade recieved: " + m.getUuid() + " thread: " + Thread.currentThread().getName());
		try {
			m.persit();
		} catch (Error | Exception ex) {
			log.debug("SaveTradeRunnable:saveData - Trade " + m + " not persisted");

		}

	}

	@When("@Priority(1) @Audit select * from LastBarWindow")
	public void handleBar(Bar m) {

		log.trace("bar recieved: " + m.getUuid() + " thread: " + Thread.currentThread().getName());
		try {
			if (m.getDao() == null)
				context.getInjector().injectMembers(m);
			m.persit();
		} catch (Error | Exception ex) {
			log.debug("SaveTradeRunnable:saveData - Bar " + m + " not persisted");

		}

	}

	protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.saveMarketData");

}

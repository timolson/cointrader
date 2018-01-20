package org.cryptocoinpartners.command;

import java.math.BigDecimal;

import javax.inject.Inject;

import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.GeneralOrderFactory;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.util.Injector;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Provider;

/**
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class OrderCommand extends AntlrCommandBase {

	private Portfolio portfolio;

	@Override
	public String getUsageHelp() {
		return (isSell ? "sell" : "buy") + " {volume} {exchange}:{base}.{quote} [limit {price}] [stop {price}] [position {open|close}]";
	}

	@Override
	public String getExtraHelp() {
		String help = "Places an order for the given volume on the specified exchange." + "If a limit price is supplied, a limit order will be generated."
				+ "Stop and stop-limit orders are not currently supported but no " + "error will be given.";
		if (isSell)
			help += "Selling is the same as buying with a negative volume.";
		return help;
	}

	@Override
	public Object call() {
		//  PortfolioManager strategy = context.getInjector().getInstance(PortfolioManager.class);
		for (Portfolio port : portfolioService.getPortfolios())
			portfolio = port;
		if (market != null)
			return (placeSpecificOrder());
		else
			return (placeGeneralOrder());

	}

	public Boolean placeSpecificOrder() {
		//FillType.STOP_LOSS
		volume = (isSell && volume.compareTo(BigDecimal.ZERO) > 0) ? volume.negate() : volume;
		GeneralOrder order = generalOrderFactory.create(context.getTime(), portfolio, market, volume, FillType.MARKET);
		if (limit != null) {
			long limitCount = DiscreteAmount.roundedCountForBasis(limit, market.getPriceBasis());
			order.withLimitPrice(limit);
			order.withFillType(FillType.LIMIT);

		}
		if (position != null)
			order.withPositionEffect(position);

		try {
			orderService.placeOrder(order);
			return true;
		} catch (Throwable e) {

			// TODO Auto-generated catch block
			out.println("Unable to place order " + order + ". Stack Trace " + e.getStackTrace().toString());
			return false;
		}

	}

	public boolean placeGeneralOrder() {
		volume = isSell ? volume.negate() : volume;
		// GeneralOrder longOrder = generalOrderFactory.create(context.getTime(), portfolio, market, orderDiscrete.asBigDecimal(), FillType.STOP_LOSS);

		GeneralOrder order = generalOrderFactory.create(context.getTime(), portfolio, listing, volume, FillType.MARKET);

		if (limit != null) {
			order.withFillType(FillType.LIMIT);

			order.withLimitPrice(limit);
		}

		if (stop != null) {
			order.withFillType(FillType.STOP_LOSS);
			order.withStopAmount(stop);
		}

		if (target != null) {
			order.withFillType(FillType.STOP_LOSS);
			order.withTargetAmount(target);
		}
		if (comment != null) {
			order.withComment(comment);
		}
		if (position != null)
			order.withPositionEffect(position);

		if (timeToLive != 0)
			order.withTimeToLive(timeToLive);
		if (execInst != null)
			order.withExecutionInstruction(execInst);

		try {
			orderService.placeOrder(order);
			return true;
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			out.println("Unable to place order " + order + ". Stack Trace " + e.getStackTrace().toString());
			return false;
		}

	}

	@Override
	protected void initCommandArgs() {
		// clear optional args
		stop = null;
		limit = null;
		position = null;
	}

	public OrderCommand(boolean isSell) {
		super("org.cryptocoinpartners.command.Order");
		this.isSell = isSell;
	}

	@Override
	protected Injector getListenerInjector(Injector parentInjector) {
		return parentInjector.createChildInjector(new Module() {
			@Override
			public void configure(Binder binder) {
				binder.bind(OrderCommand.class).toProvider(new Provider<OrderCommand>() {
					@Override
					public OrderCommand get() {
						return OrderCommand.this;
					}
				});
			}
		});
	}

	public BigDecimal getVolume() {
		return volume;
	}

	public void setVolume(BigDecimal volume) {
		this.volume = volume;
	}

	public Market getMarket() {
		return market;
	}

	public void setMarket(Market market) {
		this.market = market;
		this.listing = market.getListing();
	}

	public Portfolio getPortfolio() {
		return portfolio;
	}

	public void setPortfolio(Portfolio portfolio) {
		this.portfolio = portfolio;

	}

	public Listing getListing() {
		return listing;
	}

	public void setListing(Listing listing) {
		this.listing = listing;
		this.market = null;
	}

	public BigDecimal getLimit() {
		return limit;
	}

	public PositionEffect getPosition() {
		return position;
	}

	public void setLimit(BigDecimal limit) {
		this.limit = limit;
	}

	public void setPosition(PositionEffect position) {
		this.position = position;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public BigDecimal getStop() {
		return stop;
	}

	public void setStop(BigDecimal stop) {
		this.stop = stop;
	}

	public boolean isSell() {
		return isSell;
	}

	public void setSell(boolean isSell) {
		this.isSell = isSell;
	}

	@Inject
	OrderService orderService;
	@Inject
	private PortfolioService portfolioService;
	@Inject
	protected transient GeneralOrderFactory generalOrderFactory;

	// @Inject
	// private Portfolio portfolio;
	private BigDecimal volume;
	private Market market;
	private Listing listing;
	private BigDecimal limit;
	private PositionEffect position;
	private BigDecimal stop;
	private BigDecimal target;
	private String comment;
	private ExecutionInstruction execInst;
	private long timeToLive;

	private boolean isSell;
}

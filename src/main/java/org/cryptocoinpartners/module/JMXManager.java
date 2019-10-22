package org.cryptocoinpartners.module;

import java.math.BigDecimal;
import java.util.ArrayList;

import javax.inject.Singleton;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.cryptocoinpartners.command.BuyCommand;
import org.cryptocoinpartners.command.CancelCommand;
import org.cryptocoinpartners.command.OrderCommand;
import org.cryptocoinpartners.command.SellCommand;
import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.DecimalAmount;
import org.cryptocoinpartners.schema.DiscreteAmount;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.FillFactory;
import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.GeneralOrderFactory;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderUpdateFactory;
import org.cryptocoinpartners.schema.Portfolio;
import org.cryptocoinpartners.schema.SpecificOrder;
import org.cryptocoinpartners.schema.SpecificOrderFactory;
import org.cryptocoinpartners.schema.Tradeable;
import org.cryptocoinpartners.schema.TransactionFactory;
import org.cryptocoinpartners.service.OrderService;
import org.cryptocoinpartners.service.PortfolioService;
import org.cryptocoinpartners.util.Remainder;

import com.google.inject.Inject;

@Singleton
public class JMXManager implements JMXManagerMBean {
	@Inject
	OrderService orderService;
	@Inject
	private PortfolioService portfolioService;
	@Inject
	protected Context context;
	@Inject
	protected transient GeneralOrderFactory generalOrderFactory;
	@Inject
	protected transient SpecificOrderFactory specificOrderFactory;
	@Inject
	protected transient OrderUpdateFactory orderUpdateFactory;
	@Inject
	protected transient TransactionFactory transactionFactory;
	@Inject
	protected transient FillFactory fillFactory;
	private Portfolio portfolio;

	@Inject
	JMXManager(MBeanServer server) {
		try {
			server.registerMBean(this, new ObjectName(this.getClass().getCanonicalName() + ":type=OrderManagement"));

		} catch (InstanceAlreadyExistsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MBeanRegistrationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NotCompliantMBeanException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedObjectNameException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void createGeneralOrder(String marketSymbol, String volume, String limitPrice) {

		for (Portfolio port : portfolioService.getPortfolios())
			portfolio = port;

		Market market = (Market) Market.forSymbol(marketSymbol);
		DiscreteAmount volumeDiscrete = DecimalAmount.of(volume).toBasis(market.getVolumeBasis(), Remainder.DISCARD);
		DiscreteAmount priceDiscrete = DecimalAmount.of(limitPrice).toBasis(market.getPriceBasis(), Remainder.DISCARD);
		// DiscreteAmount stopPriceDiscrete = DecimalAmount.of(stopPrice).toBasis(market.getPriceBasis(), Remainder.DISCARD);
		OrderCommand orderCommand;
		if (volumeDiscrete.isNegative())
			orderCommand = context.getInjector().getInstance(SellCommand.class);
		else
			orderCommand = context.getInjector().getInstance(BuyCommand.class);
		orderCommand.setPortfolio(portfolio);
		orderCommand.setLimit(new BigDecimal(limitPrice));
		orderCommand.setMarket(market);
		orderCommand.setVolume(new BigDecimal(volume));

		orderCommand.placeGeneralOrder();
	}

	@Override
	public void createStopLoss(String marketSymbol, String volume, String limitPrice, String comment, String openClose, String stopAmount) {

		for (Portfolio port : portfolioService.getPortfolios())
			portfolio = port;

		Market market = (Market) Market.forSymbol(marketSymbol);
		PositionEffect positionEffect = (openClose.equals("close") || openClose.equals("Close") || openClose.equals("CLOSE")) ? PositionEffect.CLOSE
				: PositionEffect.OPEN;
		DiscreteAmount volumeDiscrete = DecimalAmount.of(volume).toBasis(market.getVolumeBasis(), Remainder.DISCARD);
		DiscreteAmount priceDiscrete = DecimalAmount.of(limitPrice).toBasis(market.getPriceBasis(), Remainder.DISCARD);
		// DiscreteAmount stopPriceDiscrete = DecimalAmount.of(stopPrice).toBasis(market.getPriceBasis(), Remainder.DISCARD);
		OrderCommand orderCommand;
		if (volumeDiscrete.isNegative())
			orderCommand = context.getInjector().getInstance(SellCommand.class);
		else
			orderCommand = context.getInjector().getInstance(BuyCommand.class);
		orderCommand.setPortfolio(portfolio);
		orderCommand.setLimit(new BigDecimal(limitPrice));
		orderCommand.setStop(new BigDecimal(stopAmount));
		orderCommand.setPosition(positionEffect);
		orderCommand.setComment(comment);
		orderCommand.setMarket(market);
		orderCommand.setVolume(new BigDecimal(volume));
		orderCommand.placeGeneralOrder();
	}

	@Override
	public ArrayList<SpecificOrder> pendingOrders() {
		ArrayList<SpecificOrder> pendingOrders = new ArrayList<SpecificOrder>();
		for (Portfolio port : portfolioService.getPortfolios())
			for (Tradeable tradeable : port.getMarkets())
				if (!tradeable.isSynthetic()) {
					Market market = (Market) tradeable;
					pendingOrders.addAll(orderService.getPendingOrders(market, portfolio));
				}
		return pendingOrders;

	}

	@Override
	public void createSpecificOrder(String marketSymbol, String volume, String limitPrice) {
		for (Portfolio port : portfolioService.getPortfolios())
			portfolio = port;

		Market market = (Market) Market.forSymbol(marketSymbol);
		DiscreteAmount volumeDiscrete = DecimalAmount.of(volume).toBasis(market.getVolumeBasis(), Remainder.DISCARD);
		DiscreteAmount priceDiscrete = DecimalAmount.of(limitPrice).toBasis(market.getPriceBasis(), Remainder.DISCARD);
		// DiscreteAmount stopPriceDiscrete = DecimalAmount.of(stopPrice).toBasis(market.getPriceBasis(), Remainder.DISCARD);
		OrderCommand orderCommand;
		if (volumeDiscrete.isNegative())
			orderCommand = context.getInjector().getInstance(SellCommand.class);
		else
			orderCommand = context.getInjector().getInstance(BuyCommand.class);
		orderCommand.setPortfolio(portfolio);

		orderCommand.setLimit(new BigDecimal(limitPrice));
		orderCommand.setMarket(market);
		orderCommand.setVolume(new BigDecimal(volume));
		orderCommand.placeSpecificOrder();
	}

	@Override
	public Object cancelOrder(String orderId) {
		CancelCommand cancel = context.getInjector().getInstance(CancelCommand.class);
		cancel.parse(orderId);
		return (cancel.call());

	}

	//run createManualFill OKCOIN_THISWEEK:BTC.USD.THISWEEK 2 428.01 LongExistingPosition Open 
	//run createManualFill OKCOIN_THISWEEK:BTC.USD.THISWEEK "-3" 425.51 LongError Open
	// run createManualFill OKCOIN_THISWEEK:BTC.USD.THISWEEK "1" 470.16 ShortError Close
	@Override
	public String createManualFill(String marketSymbol, String volume, String price, String comment, String openClose) throws Throwable {
		for (Portfolio port : portfolioService.getPortfolios())
			portfolio = port;
		Market market = (Market) Market.forSymbol(marketSymbol);
		PositionEffect positionEffect = (openClose.equals("close") || openClose.equals("Close") || openClose.equals("CLOSE")) ? PositionEffect.CLOSE
				: PositionEffect.OPEN;

		DiscreteAmount volumeDiscrete = DecimalAmount.of(volume).toBasis(market.getVolumeBasis(), Remainder.DISCARD);
		DiscreteAmount priceDiscrete = DecimalAmount.of(price).toBasis(market.getPriceBasis(), Remainder.DISCARD);

		SpecificOrder exitOrder = specificOrderFactory.create(context.getTime(), portfolio, market, volumeDiscrete, comment);

		exitOrder.withLimitPrice(priceDiscrete).withPositionEffect(positionEffect).withExecutionInstruction(ExecutionInstruction.MANUAL).withComment(comment);
		try {
			orderService.placeOrder(exitOrder);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			throw e;

		}

		Fill fill = fillFactory.create(exitOrder, context.getTime(), context.getTime(), exitOrder.getMarket(), priceDiscrete.getCount(),
				volumeDiscrete.getCount(), context.getTime().toString());
		if (fill != null)
			orderService.handleFillProcessing(fill);
		return ("Created manual fill " + fill);

	}

	//run createStopLimitPercentageManualFill  OKCOIN_QUARTER:ETH.USD.QUARTER -2680 400 0.1128 TRAILING_STOP_LOSS "Missing Short Stop Order" Open True
	@Override
	public String createStopLimitPercentageManualFill(String marketSymbol, String volume, String price, String stopPercentage, String type, String comment,
			String openClose, String position) throws Throwable {
		for (Portfolio port : portfolioService.getPortfolios())
			portfolio = port;
		Market market = (Market) Market.forSymbol(marketSymbol);
		PositionEffect positionEffect = (openClose.equals("close") || openClose.equals("Close") || openClose.equals("CLOSE")) ? PositionEffect.CLOSE
				: PositionEffect.OPEN;
		Boolean usePosition = (position.equals("true") || openClose.equals("True") || openClose.equals("TRUE")) ? true : false;

		FillType fillType = FillType.STOP_LIMIT;
		switch (type) {
			case "TRAILING_STOP_LOSS":
				fillType = FillType.TRAILING_STOP_LOSS;
				break;
			case "REENTRANT_STOP_LOSS":
				fillType = FillType.REENTRANT_STOP_LOSS;
				break;
			case "REENTRANT_STOP_LIMIT":
				fillType = FillType.REENTRANT_STOP_LIMIT;
				break;
			case "REENTRANT_TRAILING_STOP_LOSS":
				fillType = FillType.REENTRANT_TRAILING_STOP_LOSS;
				break;
			case "REENTRANT_TRAILING_STOP_LIMIT":
				fillType = FillType.REENTRANT_TRAILING_STOP_LIMIT;
				break;
			case "TRAILING_UNREALISED_STOP_LOSS":
				fillType = FillType.TRAILING_UNREALISED_STOP_LOSS;
				break;
			default:
				fillType = FillType.STOP_LIMIT;
				break;
		}

		DiscreteAmount volumeDiscrete = DecimalAmount.of(volume).toBasis(market.getVolumeBasis(), Remainder.DISCARD);
		DiscreteAmount priceDiscrete = DecimalAmount.of(price).toBasis(market.getPriceBasis(), Remainder.DISCARD);
		//Create general Order
		GeneralOrder generalManualOrder = generalOrderFactory.create(context.getTime(), portfolio, market, volumeDiscrete.asBigDecimal(), fillType);
		// Collections.sort(orderList,org.apache.commons.collections15.ComparatorUtils.nullLowComparator(comparator))

		generalManualOrder.withComment(comment).withPositionEffect(positionEffect).withExecutionInstruction(ExecutionInstruction.MANUAL)
				.withUsePosition(usePosition).withLimitPrice(priceDiscrete.asBigDecimal()).withStopPercentage(Double.parseDouble(stopPercentage))
				.withTargetAmount(priceDiscrete.times(10, Remainder.ROUND_EVEN).asBigDecimal());

		try {
			orderService.placeOrder(generalManualOrder);
		} catch (Throwable e) {
			throw e;

		}
		//get child order
		SpecificOrder specificManualOrder = null;
		for (Order childOrder : generalManualOrder.getOrderChildren()) {
			if (childOrder instanceof SpecificOrder) {
				specificManualOrder = (SpecificOrder) childOrder;
				break;
			}
		}
		if (specificManualOrder == null)
			return ("Unable to create stop limit percentage manual fill with general order" + generalManualOrder);

		Fill fill = fillFactory.create(specificManualOrder, context.getTime(), context.getTime(), specificManualOrder.getMarket(), priceDiscrete.getCount(),
				volumeDiscrete.getCount(), context.getTime().toString());
		if (fill != null)
			orderService.handleFillProcessing(fill);
		return ("Created stop limit percentage manual fill " + fill);

	}

	@Override
	public String getOrderService() {
		return orderService.getClass().getSimpleName();

	}

	@Override
	public String getPortfolioService() {
		return portfolioService.getClass().getSimpleName();

	}

	// run createStopLimitPercentageManualFill OKCOIN_THISWEEK:BTC.USD.THISWEEK "1" 47000.16 4700 TRAILING_STOP_LOSS ShortError Close True

	@Override
	public String createStopLimitAmountManualFill(String marketSymbol, String volume, String price, String stopAmount, String type, String comment,
			String openClose, String position) throws Throwable {
		for (Portfolio port : portfolioService.getPortfolios())
			portfolio = port;
		Market market = (Market) Market.forSymbol(marketSymbol);
		PositionEffect positionEffect = (openClose.equals("close") || openClose.equals("Close") || openClose.equals("CLOSE")) ? PositionEffect.CLOSE
				: PositionEffect.OPEN;

		Boolean usePosition = (position.equals("true") || openClose.equals("True") || openClose.equals("TRUE")) ? true : false;

		FillType fillType = FillType.STOP_LIMIT;
		switch (type) {
			case "TRAILING_STOP_LOSS":
				fillType = FillType.TRAILING_STOP_LOSS;
				break;
			case "REENTRANT_STOP_LOSS":
				fillType = FillType.REENTRANT_STOP_LOSS;
				break;
			case "REENTRANT_STOP_LIMIT":
				fillType = FillType.REENTRANT_STOP_LIMIT;
				break;
			case "REENTRANT_TRAILING_STOP_LOSS":
				fillType = FillType.REENTRANT_TRAILING_STOP_LOSS;
				break;
			case "REENTRANT_TRAILING_STOP_LIMIT":
				fillType = FillType.REENTRANT_TRAILING_STOP_LIMIT;
				break;
			case "TRAILING_UNREALISED_STOP_LOSS":
				fillType = FillType.TRAILING_UNREALISED_STOP_LOSS;
				break;
			default:
				fillType = FillType.STOP_LIMIT;
				break;
		}

		DiscreteAmount volumeDiscrete = DecimalAmount.of(volume).toBasis(market.getVolumeBasis(), Remainder.DISCARD);
		DiscreteAmount priceDiscrete = DecimalAmount.of(price).toBasis(market.getPriceBasis(), Remainder.DISCARD);
		DiscreteAmount stopAmountDiscrete = DecimalAmount.of(stopAmount).toBasis(market.getPriceBasis(), Remainder.DISCARD);

		//Create general Order
		GeneralOrder generalManualOrder = generalOrderFactory.create(context.getTime(), portfolio, market, volumeDiscrete.asBigDecimal(), fillType);
		// Collections.sort(orderList,org.apache.commons.collections15.ComparatorUtils.nullLowComparator(comparator))

		generalManualOrder.withComment(comment).withPositionEffect(positionEffect).withExecutionInstruction(ExecutionInstruction.MANUAL)
				.withUsePosition(usePosition).withLimitPrice(priceDiscrete.asBigDecimal()).withStopAmount(stopAmountDiscrete.asBigDecimal())
				.withTargetAmount(priceDiscrete.times(10, Remainder.ROUND_EVEN).asBigDecimal());

		try {
			orderService.placeOrder(generalManualOrder);
		} catch (Throwable e) {
			throw e;

		}
		//get child order
		SpecificOrder specificManualOrder = null;
		for (Order childOrder : generalManualOrder.getOrderChildren()) {
			if (childOrder instanceof SpecificOrder) {
				specificManualOrder = (SpecificOrder) childOrder;
				break;
			}
		}
		if (specificManualOrder == null)
			return ("Unable to create stop limit amount manual fill with general order" + generalManualOrder);

		Fill fill = fillFactory.create(specificManualOrder, context.getTime(), context.getTime(), specificManualOrder.getMarket(), priceDiscrete.getCount(),
				volumeDiscrete.getCount(), context.getTime().toString());
		if (fill != null)
			orderService.handleFillProcessing(fill);
		return ("Created stop limit amount manual fill " + fill);

	}

}

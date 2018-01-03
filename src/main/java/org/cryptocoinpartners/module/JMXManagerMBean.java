package org.cryptocoinpartners.module;

import java.util.Collection;

import org.cryptocoinpartners.schema.SpecificOrder;

public interface JMXManagerMBean {

	String getOrderService();

	String getPortfolioService();

	void createSpecificOrder(String marketSymbol, String volume, String limitPrice);

	void createGeneralOrder(String marketSymbol, String volume, String limitPrice);

	Collection<SpecificOrder> pendingOrders();

	String createManualFill(String marketSymbol, String volume, String price, String comment, String openClose) throws Throwable;

	Object cancelOrder(String orderId);

	void createStopLoss(String marketSymbol, String volume, String limitPrice, String comment, String openClose, String stopAmount);

	String createStopLimitPercentageManualFill(String marketSymbol, String volume, String price, String stopPercentage, String type, String comment,
			String openClose, String position) throws Throwable;

	String createStopLimitAmountManualFill(String marketSymbol, String volume, String price, String stopAmount, String type, String comment, String openClose,
			String position) throws Throwable;

}

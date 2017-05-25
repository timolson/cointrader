package org.cryptocoinpartners.module;

import java.util.Collection;

import org.cryptocoinpartners.schema.SpecificOrder;

public interface JMXManagerMBean {
    public String sayHello(String name);

    public void start();

    public void stop();

    String getOrderService();

    String getPortfolioService();

    void createSpecificOrder(String marketSymbol, String volume, String limitPrice);

    void createGeneralOrder(String marketSymbol, String volume, String limitPrice);

    Collection<SpecificOrder> pendingOrders();

    String createManualFill(String marketSymbol, String volume, String price, String comment, String openClose) throws Throwable;

    Object cancelOrder(String orderId);

    void createStopLoss(String marketSymbol, String volume, String limitPrice, String comment, String openClose, String stopAmount);

}

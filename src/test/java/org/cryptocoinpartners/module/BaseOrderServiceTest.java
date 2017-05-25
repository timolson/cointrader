package org.cryptocoinpartners.module;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.cryptocoinpartners.enumeration.ExecutionInstruction;
import org.cryptocoinpartners.enumeration.FillType;
import org.cryptocoinpartners.enumeration.PositionEffect;
import org.cryptocoinpartners.schema.Asset;
import org.cryptocoinpartners.schema.Currency;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.GeneralOrder;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.Portfolio;
import org.joda.time.Instant;
import org.junit.Test;

public class BaseOrderServiceTest {

    // Replay replay = new Replay(false);

    //   Context context = Context.create(new EventTimeManager());
    /*
        protected Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(MockOrderService.class);
            }
        });

        //  @Before
        // public void setup() {
        //   injector.injectMembers(this);
        // }

        @Inject
        BaseOrderService orderSerivce;
    */
    @Test
    public final void test() {

        //replay.getContext().attach(c)

        // replay.getContext().attach(MockOrderService.class);
        //  context.attach(JMXManager.class);
        //

        //..  BaseOrderService orderService = new MockOrderService();

        // orderSerivce.descendingAmountComparator

        // so we create an array lists of order
        // then we sort them
        List<Order> trailingStopOrders = new ArrayList<Order>();
        List<Order> stopOrders = new ArrayList<Order>();
        // Object BaseOrderService;;
        //BaseOrderService orderService = new MockOrderService();
        ;
        //Object BaseOrderService();
        // orderService=new BaseOrderService();
        // String marketSymbol = ("OKCOIN_THISWEEK:BTC.USD.THISWEEK");
        Exchange exchange = new Exchange("OKCOIN_THISWEEK");
        Asset base = new Currency(false, "USD", 0.01);
        Asset quote = new Currency(false, "BTC", 0.01);

        Listing listing = new Listing(base, quote);
        Market market = new Market(exchange, listing, 0.01, 0.01);
        // Market market = Market.forSymbol(marketSymbol);
        GeneralOrder targetOrder1 = (new GeneralOrder(new Instant(System.currentTimeMillis() - 2000), new Portfolio(), market, BigDecimal.ONE,
                FillType.TRAILING_STOP_LOSS));
        targetOrder1.withComment("targetOrder1").withTargetPrice(BigDecimal.valueOf(1)).withPositionEffect(PositionEffect.OPEN)
                .withExecutionInstruction(ExecutionInstruction.MAKER).withLimitPrice(BigDecimal.valueOf(3));

        GeneralOrder targetOrder2 = (new GeneralOrder(new Instant(System.currentTimeMillis() - 2000), new Portfolio(), market, BigDecimal.ONE,
                FillType.TRAILING_STOP_LOSS));
        targetOrder2.withComment("targetOrder2").withTargetPrice(BigDecimal.valueOf(2)).withPositionEffect(PositionEffect.OPEN)
                .withExecutionInstruction(ExecutionInstruction.MAKER).withLimitPrice(BigDecimal.valueOf(4));

        GeneralOrder stopTargetOrder3 = (new GeneralOrder(new Instant(System.currentTimeMillis()), new Portfolio(), market, BigDecimal.ONE,
                FillType.TRAILING_STOP_LOSS));
        stopTargetOrder3.withComment("stopTargetOrder3").withTargetPrice(BigDecimal.valueOf(3)).withStopPrice(BigDecimal.valueOf(3))
                .withPositionEffect(PositionEffect.OPEN).withExecutionInstruction(ExecutionInstruction.MAKER).withLimitPrice(BigDecimal.valueOf(5));

        GeneralOrder stopTargetOrder4 = (new GeneralOrder(new Instant(System.currentTimeMillis()), new Portfolio(), market, BigDecimal.ONE,
                FillType.TRAILING_STOP_LOSS));
        stopTargetOrder4.withComment("stopTargetOrder4").withTargetPrice(BigDecimal.valueOf(4)).withStopPrice(BigDecimal.valueOf(4))
                .withPositionEffect(PositionEffect.OPEN).withExecutionInstruction(ExecutionInstruction.MAKER).withLimitPrice(BigDecimal.valueOf(6));

        GeneralOrder stopOrder1 = (new GeneralOrder(new Instant(System.currentTimeMillis()), new Portfolio(), market, BigDecimal.ONE,
                FillType.TRAILING_STOP_LOSS));
        stopOrder1.withComment("stopOrder1").withStopPrice(BigDecimal.valueOf(1)).withPositionEffect(PositionEffect.OPEN)
                .withExecutionInstruction(ExecutionInstruction.MAKER).withLimitPrice(BigDecimal.valueOf(3));

        GeneralOrder stopOrder2 = (new GeneralOrder(new Instant(System.currentTimeMillis()), new Portfolio(), market, BigDecimal.ONE,
                FillType.TRAILING_STOP_LOSS));
        stopOrder2.withComment("stopOrder2").withStopPrice(BigDecimal.valueOf(2)).withPositionEffect(PositionEffect.OPEN)
                .withExecutionInstruction(ExecutionInstruction.MAKER).withLimitPrice(BigDecimal.valueOf(4));

        GeneralOrder order1 = (new GeneralOrder(new Instant(System.currentTimeMillis()), new Portfolio(), market, BigDecimal.ONE, FillType.TRAILING_STOP_LOSS));
        order1.withComment("order1").withPositionEffect(PositionEffect.OPEN).withExecutionInstruction(ExecutionInstruction.MAKER);

        GeneralOrder trailingStopOrder3 = (new GeneralOrder(new Instant(System.currentTimeMillis()), new Portfolio(), market, BigDecimal.ONE,
                FillType.TRAILING_STOP_LOSS));
        trailingStopOrder3.withComment("trailingStopOrder3").withStopPrice(BigDecimal.valueOf(6)).withStopAmount(BigDecimal.valueOf(1))
                .withPositionEffect(PositionEffect.OPEN).withExecutionInstruction(ExecutionInstruction.MAKER).withLimitPrice(BigDecimal.valueOf(8));

        GeneralOrder trailingStopOrder2 = (new GeneralOrder(new Instant(System.currentTimeMillis()), new Portfolio(), market, BigDecimal.ONE,
                FillType.TRAILING_STOP_LOSS));
        trailingStopOrder2.withComment("trailingStopOrder2").withStopPrice(BigDecimal.valueOf(8)).withStopAmount(BigDecimal.valueOf(2))
                .withPositionEffect(PositionEffect.OPEN).withExecutionInstruction(ExecutionInstruction.MAKER).withLimitPrice(BigDecimal.valueOf(4));

        GeneralOrder trailingStopOrder1 = (new GeneralOrder(new Instant(System.currentTimeMillis()), new Portfolio(), market, BigDecimal.ONE,
                FillType.TRAILING_STOP_LOSS));
        trailingStopOrder1.withComment("trailingStopOrder1").withStopPrice(BigDecimal.valueOf(10)).withStopAmount(BigDecimal.valueOf(3))
                .withPositionEffect(PositionEffect.OPEN).withExecutionInstruction(ExecutionInstruction.MAKER).withLimitPrice(BigDecimal.valueOf(5));

        stopOrders.add(order1);
        stopOrders.add(stopOrder1);

        stopOrders.add(stopOrder2);
        stopOrders.add(targetOrder1);
        stopOrders.add(targetOrder2);
        stopOrders.add(stopTargetOrder4);
        stopOrders.add(stopTargetOrder3);

        // orders.add(stopOrder1);
        // orders.add(targetOrder1);
        // orders.add(targetOrder2);

        // so why is this sometimes failing?

        Comparator<Order> smallestToLargestPrice = MockOrderService.ascendingPriceComparator;
        Collections.sort(stopOrders, smallestToLargestPrice);
        assertEquals(order1, stopOrders.get(0));
        assertEquals(targetOrder1, stopOrders.get(1));
        assertEquals(stopOrder1, stopOrders.get(2));
        assertEquals(targetOrder2, stopOrders.get(3));
        assertEquals(stopOrder2, stopOrders.get(4));
        assertEquals(stopTargetOrder3, stopOrders.get(5));
        assertEquals(stopTargetOrder4, stopOrders.get(6));

        Comparator<Order> largestToSmallestPrice = MockOrderService.descendingPriceComparator;

        Collections.sort(stopOrders, largestToSmallestPrice);
        assertEquals(order1, stopOrders.get(0));
        assertEquals(stopTargetOrder4, stopOrders.get(1));
        assertEquals(stopTargetOrder3, stopOrders.get(2));
        assertEquals(targetOrder2, stopOrders.get(3));
        assertEquals(stopOrder2, stopOrders.get(4));
        assertEquals(targetOrder1, stopOrders.get(5));
        assertEquals(stopOrder1, stopOrders.get(6));

        Comparator<Order> smallestToLargestStopPrice = MockOrderService.ascendingStopPriceComparator;
        Collections.sort(stopOrders, smallestToLargestStopPrice);

        assertEquals(targetOrder1, stopOrders.get(0));
        assertEquals(stopOrder1, stopOrders.get(1));
        assertEquals(targetOrder2, stopOrders.get(2));
        assertEquals(stopOrder2, stopOrders.get(3));
        assertEquals(stopTargetOrder3, stopOrders.get(4));
        assertEquals(stopTargetOrder4, stopOrders.get(5));
        assertEquals(order1, stopOrders.get(6));

        Comparator<Order> largestToSmallestStopPrice = MockOrderService.descendingStopPriceComparator;

        Collections.sort(stopOrders, largestToSmallestStopPrice);
        assertEquals(stopTargetOrder4, stopOrders.get(0));
        assertEquals(stopTargetOrder3, stopOrders.get(1));
        assertEquals(targetOrder2, stopOrders.get(2));
        assertEquals(stopOrder2, stopOrders.get(3));
        assertEquals(targetOrder1, stopOrders.get(4));
        assertEquals(stopOrder1, stopOrders.get(5));

        assertEquals(order1, stopOrders.get(6));

        trailingStopOrders.add(trailingStopOrder1);
        trailingStopOrders.add(trailingStopOrder2);
        trailingStopOrders.add(trailingStopOrder3);

        Comparator<Order> smallestToLargestTrailingStopPrice = MockOrderService.ascendingTrailingStopPriceComparator;
        Collections.sort(trailingStopOrders, smallestToLargestTrailingStopPrice);
        assertEquals(trailingStopOrder3, trailingStopOrders.get(0));
        assertEquals(trailingStopOrder2, trailingStopOrders.get(1));
        assertEquals(trailingStopOrder1, trailingStopOrders.get(2));

        Comparator<Order> largestToSmallestTrailingStopPrice = MockOrderService.descendingTrailingStopPriceComparator;
        Collections.sort(trailingStopOrders, largestToSmallestTrailingStopPrice);
        assertEquals(trailingStopOrder1, trailingStopOrders.get(0));
        assertEquals(trailingStopOrder2, trailingStopOrders.get(1));
        assertEquals(trailingStopOrder3, trailingStopOrders.get(2));

    }

}

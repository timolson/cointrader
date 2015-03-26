package org.cryptocoinpartners.module;

import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.enumeration.PositionType;
import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Fill;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Order;
import org.cryptocoinpartners.schema.OrderBuilder.CommonOrderBuilder;
import org.cryptocoinpartners.schema.Position;
import org.cryptocoinpartners.schema.PositionUpdate;

/**
 * This simple Strategy first waits for Book data to arrive about the target Market, then it places a buy order
 * at demostrategy.spread below the current bestAsk.  Once it enters the trade, it places a sell order at
 * demostrategy.spread above the current bestBid.
 * This strategy ignores the available Positions in the Portfolio and always trades the amount set by demostrategy.volume on
 * the Market specified by demostrategy.market
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class ConsoleStrategy extends SimpleStatefulStrategy {
    private static Object lock = new Object();

    private final ConcurrentHashMap<Market, ConcurrentHashMap<PositionType, Position>> positionMap = new ConcurrentHashMap<Market, ConcurrentHashMap<PositionType, Position>>();

    @Inject
    public ConsoleStrategy(Context context, Configuration config) {

    }

    @When("@Priority(9) select * from PositionUpdate(PositionUpdate.market=TestStrategy.getMarket())")
    void handlePositionUpdate(PositionUpdate positionUpdate) {
        synchronized (lock) {
            if (positionUpdate.getPosition() != null)
                updatePositionMap(positionUpdate.getMarket(), positionUpdate.getType(), positionUpdate.getPosition());
            else
                updatePositionMap(positionUpdate.getMarket(), positionUpdate.getType());
        }

    }

    public boolean updatePositionMap(Market market, PositionType type, Position position) {

        ConcurrentHashMap<PositionType, Position> newPosition = new ConcurrentHashMap<PositionType, Position>();
        newPosition.put(type, position);
        positionMap.put(market, newPosition);
        return true;

    }

    public Position getPosition(Market market) {
        //    synchronized (lock) {
        // Need to get existing position
        // Position mergedPosition = new Position(portfolio, market.getExchange(), market, market.getTradedCurrency(), DecimalAmount.ZERO, DecimalAmount.ZERO);
        if (positionMap.get(market) == null)
            return null;
        for (PositionType positionType : positionMap.get(market).keySet()) {
            // if (positionType.equals(PositionType.ENTERING) || positionType.equals(PositionType.EXITING))
            //    return null;
            return (positionMap.get(market).get(positionType));
        }
        return null;
    }

    public boolean updatePositionMap(Market market, PositionType type) {
        // Need to get existing position
        synchronized (lock) {
            if (positionMap.get(market) == null) {
                ConcurrentHashMap<PositionType, Position> newPosition = new ConcurrentHashMap<PositionType, Position>();
                Position position = new Position();
                newPosition.put(type, position);
                positionMap.put(market, newPosition);
                return true;
            }

            for (PositionType positionType : positionMap.get(market).keySet())

            {
                ConcurrentHashMap<PositionType, Position> position = new ConcurrentHashMap<PositionType, Position>();
                position.put(type, positionMap.get(market).get(positionType));
                positionMap.put(market, position);
                return true;
            }
            return false;
        }
    }

    @Override
    @Nullable
    protected CommonOrderBuilder buildEntryOrder() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    @Nullable
    protected CommonOrderBuilder buildStopOrder(Fill fill) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    @Nullable
    protected CommonOrderBuilder buildExitOrder(Order entryOrder) {
        // TODO Auto-generated method stub
        return null;
    }

    //	private static double interval = 86400;

    //int counter = 0;

}

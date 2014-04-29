package com.cryptocoinpartners.module.tickwindow;

import com.cryptocoinpartners.module.*;
import com.cryptocoinpartners.schema.*;
import org.apache.commons.configuration.Configuration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;


/**
 * @author Tim Olson
 */
public class TickWindow extends ModuleListenerBase {


    public void initModule( Esper esper, Configuration config )
    {
        super.initModule(esper, config);
        accumulatingTickMap = new HashMap<UUID, AccumulatingTick>();
    }


    @When("select current_timestamp() from pattern [every timer:interval(60 sec)]")
    public void publishTick(long now) {
        for( AccumulatingTick accumulatingTick : accumulatingTickMap.values() ) {
            Tick tick = accumulatingTick.flushTick(now);
            esper.publish(tick);
            log.trace("published tick " + tick);
        }
    }


    @When("select * from Trade")
    public void handleTrade(Trade t) {
        getAccumulatingTick(t.getMarketListing()).updateTrade(t);
    }


    @When("select * from Book")
    public void handleBook(Book t) {
        getAccumulatingTick(t.getMarketListing()).updateBook(t);
    }


    private AccumulatingTick getAccumulatingTick( MarketListing ml ) {
        AccumulatingTick at = accumulatingTickMap.get(ml.getId());
        if( at == null ) {
            at = new AccumulatingTick(ml);
            accumulatingTickMap.put(ml.getId(),at);
        }
        return at;
    }


    private static class AccumulatingTick extends Tick {
        private AccumulatingTick( MarketListing ml )
        {
            super(ml, null, null, null, BigDecimal.ZERO, null, null);
        }


        private void updateTrade( Trade t ) {
            setLastPrice(t.getPrice());
            final BigDecimal oldAmount = getAmount();
            if( oldAmount == null )
                setAmount(t.getAmount());
            else
                setAmount(oldAmount.add(t.getAmount()));
        }


        private void updateBook( Book b ) {
            setBestBid(b.getBestBid());
            setBestAsk(b.getBestAsk());
        }


        private Tick flushTick( long now )
        {
            long startTime = getTime() == null ? now : getTime().getMillis();
            final Instant endInstant = new Instant(now);
            final BigDecimal amount = getAmount() == null ? BigDecimal.ZERO : getAmount();
            Tick tick = new Tick( getMarketListing(), new Instant(startTime), endInstant,
                                  getLastPrice(), amount, getBestBid(), getBestAsk());
            setStartInstant(endInstant);
            setAmount(null);
            return tick;
        }
    }


    private Map<UUID,AccumulatingTick> accumulatingTickMap;
    private static Logger log = LoggerFactory.getLogger(TickWindow.class);
}

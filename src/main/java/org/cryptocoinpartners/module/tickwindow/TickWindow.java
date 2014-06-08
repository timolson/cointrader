package org.cryptocoinpartners.module.tickwindow;

import org.apache.commons.configuration.Configuration;
import org.cryptocoinpartners.module.Esper;
import org.cryptocoinpartners.module.ModuleListenerBase;
import org.cryptocoinpartners.module.When;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.MarketListing;
import org.cryptocoinpartners.schema.Tick;
import org.cryptocoinpartners.schema.Trade;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * This class generates Ticks by listening for Trades and Books
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class TickWindow extends ModuleListenerBase {


    public void initModule( Esper esper, Configuration config )
    {
        super.initModule(esper, config);
        accumulatingTickMap = new HashMap<>();
    }


    // Every minute at five seconds after the minute
    @When("select current_timestamp() from pattern [every timer:at(*, *, *, *, *, 5)]")
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
    public void handleBook(Book b) {
        getAccumulatingTick(b.getMarketListing()).updateBook(b);
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
        private AccumulatingTick( MarketListing ml ) {
            super(ml, null, null, null, 0L, null);
        }


        @SuppressWarnings("ConstantConditions")
        private void updateTrade( Trade t ) {
            setPriceCount(t.getPriceCount());
            final Long oldAmount = getVolumeCount();
            if( oldAmount == null )
                setVolumeCount(t.getVolumeCount());
            else
                setVolumeCount(oldAmount+t.getVolumeCount());
        }


        private void updateBook( Book b ) { setLastBook(b); }


        private Tick flushTick( long now )
        {
            long startTime = getTime() == null ? now : getTime().getMillis();
            final Instant endInstant = new Instant(now);
            final long amount = getVolumeCount() == null ? 0 : getVolumeCount();
            Tick tick = new Tick( getMarketListing(), new Instant(startTime), endInstant,
                                  getPriceCount(), amount, getLastBook() );
            setStartInstant(endInstant);
            setVolumeCount(0L);
            return tick;
        }
    }


    private Map<UUID,AccumulatingTick> accumulatingTickMap;
    private static Logger log = LoggerFactory.getLogger(TickWindow.class);
}

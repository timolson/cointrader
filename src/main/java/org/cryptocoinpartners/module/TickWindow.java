package org.cryptocoinpartners.module;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.cryptocoinpartners.esper.annotation.When;
import org.cryptocoinpartners.schema.Book;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Tick;
import org.cryptocoinpartners.schema.Trade;
import org.joda.time.Instant;
import org.slf4j.Logger;

/**
 * This class generates Ticks by listening for Trades and Books
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
@Singleton
public class TickWindow {

    @Inject
    private TickWindow(Context context) {
        this.context = context;
        accumulatingTickMap = new HashMap<>();
    }

    // Every minute at five seconds after the minute
    @When("select current_timestamp() from pattern [every timer:at(*, *, *, *, *, 0)]")
    public void publishTick(long now) {
        for (AccumulatingTick accumulatingTick : accumulatingTickMap.values()) {
            Tick tick = accumulatingTick.flushTick(now);
            context.publish(tick);
            log.trace("published tick " + tick);
        }
    }

    @When("select * from Trade")
    public void handleTrade(Trade t) {
        getAccumulatingTick(t.getMarket()).updateTrade(t);
    }

    @When("select * from Book")
    public void handleBook(Book b) {
        getAccumulatingTick(b.getMarket()).updateBook(b);
    }

    private AccumulatingTick getAccumulatingTick(Market ml) {
        AccumulatingTick at = accumulatingTickMap.get(ml.getId());
        if (at == null) {
            at = new AccumulatingTick(ml);
            accumulatingTickMap.put(ml.getId(), at);
        }
        return at;
    }

    private static class AccumulatingTick extends Tick {
        private AccumulatingTick(Market ml) {
            super(ml, null, null, null, 0L, null);
        }

        @SuppressWarnings("ConstantConditions")
        private void updateTrade(Trade t) {
            setPriceCount(t.getPriceCount());
            final Long oldVolume = getVolumeCount();
            if (oldVolume == null)
                setVolumeCount(t.getVolumeCount());
            else
                setVolumeCount(oldVolume + t.getVolumeCount());
        }

        private void updateBook(Book b) {
            setLastBook(b);
        }

        private Tick flushTick(long now) {
            long startTime = getTime() == null ? now : getTime().getMillis();
            final Instant endInstant = new Instant(now);
            final long volume = getVolumeCount() == null ? 0 : getVolumeCount();
            Tick tick = new Tick(getMarket(), new Instant(startTime), endInstant, getPriceCount(), volume, getLastBook());
            setStartInstant(endInstant);
            setVolumeCount(0L);
            return tick;
        }
    }

    @Inject
    private Context context;
    @Inject
    private Logger log;
    private final Map<UUID, AccumulatingTick> accumulatingTickMap;
}

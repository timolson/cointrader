package org.cryptocoinpartners.esper;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;

import org.cryptocoinpartners.schema.Bar;
import org.cryptocoinpartners.schema.Tradeable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.EventType;
import com.espertech.esper.core.context.util.AgentInstanceViewFactoryChainContext;
import com.espertech.esper.core.service.EPStatementHandleCallback;
import com.espertech.esper.core.service.ExtensionServicesContext;
import com.espertech.esper.epl.expression.ExprNode;
import com.espertech.esper.event.EventAdapterService;
import com.espertech.esper.schedule.ScheduleHandleCallback;
import com.espertech.esper.schedule.ScheduleSlot;
import com.espertech.esper.view.CloneableView;
import com.espertech.esper.view.View;
import com.espertech.esper.view.ViewSupport;

/**
 * Custom view to compute minute OHLC bars for double values and based on the event's timestamps.
 * <p>
 * Assumes events arrive in the order of timestamps, i.e. event 1 timestamp is always less or equal event 2 timestamp.
 * <p>
 * Implemented as a custom plug-in view rather then a series of EPL statements for the following reasons:
 *   - Custom output result mixing aggregation (min/max) and first/last values
 *   - No need for a data window retaining events if using a custom view
 *   - Unlimited number of groups (minute timestamps) makes the group-by clause hard to use
 */
public class OHLCBarPlugInView extends ViewSupport implements CloneableView {
    private final static int LATE_EVENT_SLACK_SECONDS = 5;
    protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.OHLCBarPlugInView");

    private final AgentInstanceViewFactoryChainContext agentInstanceViewFactoryContext;
    private final ScheduleSlot scheduleSlot;
    private final ExprNode timestampExpression;
    private final ExprNode valueExpression;
    private ExprNode volumeExpression;
    private ExprNode marketExpression;
    private ExprNode intervalExpression;
    private final EventBean[] eventsPerStream = new EventBean[1];

    private EPStatementHandleCallback handle;
    private final HashMap<Tradeable, Long> cutoffTimestampMinute = new HashMap<Tradeable, Long>();
    private final HashMap<Tradeable, Long> currentTimestampMinute = new HashMap<Tradeable, Long>();
    private final HashMap<Tradeable, Double> first = new HashMap<Tradeable, Double>();
    private final HashMap<Tradeable, Double> last = new HashMap<Tradeable, Double>();
    private final HashMap<Tradeable, Double> max = new HashMap<Tradeable, Double>();
    private final HashMap<Tradeable, Double> min = new HashMap<Tradeable, Double>();
    private final HashMap<Tradeable, Double> vol = new HashMap<Tradeable, Double>();

    // private Market market;
    //  private Double interval;
    private final HashMap<Tradeable, EventBean> lastEvent = new HashMap<Tradeable, EventBean>();

    public OHLCBarPlugInView(AgentInstanceViewFactoryChainContext agentInstanceViewFactoryContext, ExprNode timestampExpression, ExprNode valueExpression) {
        this.agentInstanceViewFactoryContext = agentInstanceViewFactoryContext;
        this.timestampExpression = timestampExpression;
        this.valueExpression = valueExpression;
        this.scheduleSlot = agentInstanceViewFactoryContext.getStatementContext().getScheduleBucket().allocateSlot();
    }

    public OHLCBarPlugInView(AgentInstanceViewFactoryChainContext agentInstanceViewFactoryContext, ExprNode timestampExpression, ExprNode valueExpression,
            ExprNode volumeExpression, ExprNode marketExpression, ExprNode intervalExpression) {
        this.agentInstanceViewFactoryContext = agentInstanceViewFactoryContext;
        this.timestampExpression = timestampExpression;
        this.valueExpression = valueExpression;
        this.volumeExpression = volumeExpression;
        this.marketExpression = marketExpression;
        this.intervalExpression = intervalExpression;
        this.scheduleSlot = agentInstanceViewFactoryContext.getStatementContext().getScheduleBucket().allocateSlot();
    }

    @Override
    public void update(EventBean[] newData, EventBean[] oldData) {
        if (newData == null) {
            return;
        }

        for (EventBean theEvent : newData) {
            eventsPerStream[0] = theEvent;
            // log.trace(this.getClass().getSimpleName() + ":updaate recieved new event" + eventsPerStream[0].toString());

            Double interval = (Double) intervalExpression.getExprEvaluator().evaluate(eventsPerStream, true, agentInstanceViewFactoryContext);

            Long timestamp = (Long) timestampExpression.getExprEvaluator().evaluate(eventsPerStream, true, agentInstanceViewFactoryContext);
            Long timestampMinute = removeSeconds(timestamp, interval);
            double value = (Double) valueExpression.getExprEvaluator().evaluate(eventsPerStream, true, agentInstanceViewFactoryContext);
            double volume = (Double) volumeExpression.getExprEvaluator().evaluate(eventsPerStream, true, agentInstanceViewFactoryContext);

            Tradeable market = null;
            if (marketExpression != null)
                market = (Tradeable) marketExpression.getExprEvaluator().evaluate(eventsPerStream, true, agentInstanceViewFactoryContext);

            // test if this minute has already been published, the event is too late
            if (interval == null || interval == 0 || timestamp == null || timestamp == 0 || timestampMinute == null || timestampMinute == 0
                    || (marketExpression != null && market == null)) {
                log.error(this.getClass().getSimpleName() + ":unable to create bar with interval: " + interval + " timestamp: " + timestamp
                        + " timestampMinute:" + timestampMinute + " market: " + market + " cutoffTimestampMinute: " + cutoffTimestampMinute);

                return;
            }
            //  log.trace(this.getClass().getSimpleName() + ":update determing bar for interval: " + interval + " timestamp: " + timestamp + " timestampMinute:"
            //        + timestampMinute + " value: " + value + " market: " + market + " cutoffTimestampMinute: " + cutoffTimestampMinute);

            if ((cutoffTimestampMinute.get(market) != null) && (timestampMinute <= cutoffTimestampMinute.get(market))) {
                continue;
            }

            // if the same minute, aggregate
            if (timestampMinute.equals(getCurrentTimestampMinute(market))) {
                //  log.trace(this.getClass().getSimpleName() + ":apply value for " + value);
                applyValue(market, value, volume);
            }
            // first time we see an event for this minute
            else {
                // there is data to post
                if (getCurrentTimestampMinute(market) != null) {
                    //   log.trace(this.getClass().getSimpleName() + " update: posing data for bar for market: " + market + " with timestamp:"
                    //         + currentTimestampMinute + " first:" + first + " high: " + max + " low: " + min + " close: " + last);

                    postData(market, interval);
                }
                setCurrentTimestampMinute(market, timestampMinute);

                // currentTimestampMinute = timestampMinute;
                //     log.trace(this.getClass().getSimpleName() + ":apply value for " + value);

                applyValue(market, value, volume);

                // schedule a callback to fire in case no more events arrive
                //  log.trace(this.getClass().getSimpleName() + ":scheduleCallback for interval: " + interval.longValue() + " slack " + LATE_EVENT_SLACK_SECONDS);

                scheduleCallback(market);
            }
        }
    }

    public Long getCurrentTimestampMinute(Tradeable market) {
        return currentTimestampMinute.get(market);

    }

    protected void setCurrentTimestampMinute(Tradeable market, Long timestamp) {
        if ((timestamp == null || currentTimestampMinute.get(market) == null) || (timestamp > 0 && timestamp > currentTimestampMinute.get(market))) {
            currentTimestampMinute.put(market, timestamp);
        } else {
            log.error("setCurrentTimestampMinute: unable to set current time stamp minute as currnet currentTimestampMinute "
                    + currentTimestampMinute.get(market) + " is greater than new time stamp " + timestamp);

        }
    }

    public Long getCutoffTimestampMinute(Tradeable market) {
        return cutoffTimestampMinute.get(market);
    }

    protected void setCutoffTimestampMinute(Tradeable market, Long cutoff) {
        if (cutoffTimestampMinute.get(market) == null || (cutoff != null && cutoff > cutoffTimestampMinute.get(market))) {
            cutoffTimestampMinute.put(market, cutoff);

        } else {
            log.error("setCutoffTimestampMinute: unable to set cut off timestamp as currnet cutoffTimestampMinute " + cutoffTimestampMinute
                    + " is greater than new cut off time stamp " + cutoff);

        }
    }

    @Override
    public EventType getEventType() {
        return getEventType(agentInstanceViewFactoryContext.getStatementContext().getEventAdapterService());
    }

    @Override
    public Iterator<EventBean> iterator() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public View cloneView() {
        return new OHLCBarPlugInView(agentInstanceViewFactoryContext, timestampExpression, valueExpression, volumeExpression, marketExpression,
                intervalExpression);
    }

    private void applyValue(Tradeable market, double value, double volume) {
        if (first.get(market) == null) {
            first.put(market, value);

        }
        last.put(market, value);
        if (min.get(market) == null) {
            min.put(market, value);

        } else if (min.get(market).compareTo(value) > 0) {
            min.put(market, value);
        }
        if (max.get(market) == null) {
            max.put(market, value);
        } else if (max.get(market).compareTo(value) < 0) {
            max.put(market, value);

        }
        if (vol.get(market) == null) {
            vol.put(market, volume);
        } else {
            vol.put(market, volume + vol.get(market));

        }

    }

    protected static EventType getEventType(EventAdapterService eventAdapterService) {
        return eventAdapterService.addBeanType(Bar.class.getName(), Bar.class, false, false, false);
    }

    private long removeSeconds(long timestamp, double interval) {
        Calendar cal = GregorianCalendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        //TODO: need to support bars for mulitiple days
        if ((interval / 86400) > 1) {
            int days = (int) Math.round(interval / 86400);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            int modulo = cal.get(Calendar.DAY_OF_YEAR) % days;
            if (modulo > 0) {

                cal.add(Calendar.DAY_OF_YEAR, -modulo);
            }
            //cal.set(Calendar.DAY_OF_YEAR, 0);
            // round interval to nearest day
            interval = ((double) Math.round(interval / 86400)) * 86400;
        }

        if ((interval / 3600) > 1) {
            int hours = (int) Math.round(interval / 3600);
            cal.set(Calendar.MINUTE, 0);
            int modulo = cal.get(Calendar.HOUR_OF_DAY) % hours;
            if (modulo > 0) {

                cal.add(Calendar.HOUR_OF_DAY, -modulo);
            }

            // cal.set(Calendar.HOUR_OF_DAY, 0);
            // round interval to nearest hour
            interval = ((double) Math.round(interval / 3600)) * 3600;
        }

        if ((interval / 60) > 1) {
            int mins = (int) (Math.round(interval / 60));

            int modulo = cal.get(Calendar.MINUTE) % mins;
            if (modulo > 0) {

                cal.add(Calendar.MINUTE, -modulo);
            }
            interval = ((double) Math.round(interval / 60)) * 60;
        }
        return cal.getTimeInMillis();
    }

    private void scheduleCallback(final Tradeable market) {
        if (handle != null) {
            // remove old schedule
            agentInstanceViewFactoryContext.getStatementContext().getSchedulingService().remove(handle, scheduleSlot);
            handle = null;
        }
        final double interval = (Double) intervalExpression.getExprEvaluator().evaluate(eventsPerStream, true, agentInstanceViewFactoryContext);

        long currentTime = agentInstanceViewFactoryContext.getStatementContext().getSchedulingService().getTime();
        long currentRemoveSeconds = removeSeconds(currentTime, interval);
        //long targetTime = currentRemoveSeconds + (86400 + LATE_EVENT_SLACK_SECONDS) * 1000; // leave some seconds for late comers

        long targetTime = (long) (currentRemoveSeconds + ((interval + LATE_EVENT_SLACK_SECONDS) * 1000)); // leave some seconds for late comers

        long scheduleAfterMSec = targetTime - currentTime;
        log.trace(this.getClass().getSimpleName() + ":scheduling Callback after : " + scheduleAfterMSec + " for currentTime " + currentTime + " targetTime "
                + targetTime);

        ScheduleHandleCallback callback = new ScheduleHandleCallback() {
            @Override
            public void scheduledTrigger(ExtensionServicesContext extensionServicesContext) {
                handle = null; // clear out schedule handle
                OHLCBarPlugInView.this.postData(market, interval);
            }
        };

        handle = new EPStatementHandleCallback(agentInstanceViewFactoryContext.getEpStatementAgentInstanceHandle(), callback);
        agentInstanceViewFactoryContext.getStatementContext().getSchedulingService().add(scheduleAfterMSec, handle, scheduleSlot);
        log.trace(this.getClass().getSimpleName() + ":scheduledCallback after : " + scheduleAfterMSec + " for handle " + handle + " scheduleSlot "
                + scheduleSlot);

    }

    private void postData(Tradeable market, double interval) {
        Bar barValue;
        if (currentTimestampMinute.get(market) != null && first.get(market) != null && last.get(market) != null && max.get(market) != null
                && min.get(market) != null && market != null) {
            try {
                barValue = new Bar(currentTimestampMinute.get(market), interval, first.get(market), last.get(market), max.get(market), min.get(market),
                        vol.get(market), market);
            } catch (Exception | Error ex) {
                log.error("PostData: Unable to generate bar for market: " + market + " with timestamp:" + currentTimestampMinute.get(market) + " first:"
                        + first.get(market) + " high: " + max.get(market) + " low: " + min.get(market) + " close: " + last.get(market));
                return;
            }
            EventBean outgoing = agentInstanceViewFactoryContext.getStatementContext().getEventAdapterService().adapterForBean(barValue);
            if (lastEvent == null) {
                log.trace("PostData: updating child " + outgoing.getUnderlying().toString());

                this.updateChildren(new EventBean[] { outgoing }, null);
            } else {

                //   log.trace("PostData: updating child outgoing event " + outgoing.getUnderlying().toString() + " last event "
                //         + ((lastEvent != null && lastEvent.get(market) != null) ? (lastEvent.get(market).getUnderlying().toString()) : ""));

                this.updateChildren(new EventBean[] { outgoing }, new EventBean[] { lastEvent.get(market) });
            }

            lastEvent.put(market, outgoing);

            setCutoffTimestampMinute(market, currentTimestampMinute.get(market));
            // cutoffTimestampMinute = currentTimestampMinute;

            first.put(market, null);
            last.put(market, null);
            max.put(market, null);
            min.put(market, null);
            vol.put(market, null);

            setCurrentTimestampMinute(market, null);

            // currentTimestampMinute = null;

        } else
            log.error("PostData: Unable to generate bar for market: " + market + " with timestamp:" + currentTimestampMinute + " first:" + first + " high: "
                    + max + " low: " + min + " close: " + last);
    }
}

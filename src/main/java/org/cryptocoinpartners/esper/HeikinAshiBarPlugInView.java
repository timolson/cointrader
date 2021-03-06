package org.cryptocoinpartners.esper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.cryptocoinpartners.schema.HeikinAshiBar;
import org.cryptocoinpartners.schema.Tradeable;
import org.joda.time.Instant;
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
import com.espertech.esper.view.View;

/**
 * Custom view to compute minute OHLC bars for double values and based on the event's timestamps.
 *
 * <p>Assumes events arrive in the order of timestamps, i.e. event 1 timestamp is always less or
 * equal event 2 timestamp.
 *
 * <p>Implemented as a custom plug-in view rather then a series of EPL statements for the following
 * reasons: - Custom output result mixing aggregation (min/max) and first/last values - No need for
 * a data window retaining events if using a custom view - Unlimited number of groups (minute
 * timestamps) makes the group-by clause hard to use
 */
public class HeikinAshiBarPlugInView extends OHLCBarPlugInView {
  protected static final int LATE_EVENT_SLACK_SECONDS = 0;
  protected static Logger log =
      LoggerFactory.getLogger("org.cryptocoinpartners.HeikinAshiBarPlugInView");

  protected AgentInstanceViewFactoryChainContext agentInstanceViewFactoryContext;
  protected ScheduleSlot scheduleSlot;
  protected ExprNode timestampExpression;
  protected ExprNode valueExpression;
  protected ExprNode volumeExpression;
  protected ExprNode marketExpression;
  protected ExprNode intervalExpression;
  protected final HashMap<Tradeable, EventBean[]> eventsPerMarketPerStream =
      new HashMap<Tradeable, EventBean[]>();

  protected final Map<Tradeable, Map<Double, Long>> cutoffTimestampMinute =
      new ConcurrentHashMap<Tradeable, Map<Double, Long>>();
  protected final Map<Tradeable, Map<Double, Long>> currentTimestampMinute =
      new ConcurrentHashMap<Tradeable, Map<Double, Long>>();
  protected final Map<Double, Map<Tradeable, Double>> first =
      new ConcurrentHashMap<Double, Map<Tradeable, Double>>();
  protected final Map<Double, Map<Tradeable, Double>> last =
      new ConcurrentHashMap<Double, Map<Tradeable, Double>>();
  protected final Map<Double, Map<Tradeable, Double>> max =
      new ConcurrentHashMap<Double, Map<Tradeable, Double>>();
  protected final Map<Double, Map<Tradeable, Double>> min =
      new ConcurrentHashMap<Double, Map<Tradeable, Double>>();
  protected final Map<Double, Map<Tradeable, Double>> vol =
      new ConcurrentHashMap<Double, Map<Tradeable, Double>>();
  protected final Map<Double, Map<Tradeable, Double>> buyVol =
      new ConcurrentHashMap<Double, Map<Tradeable, Double>>();
  protected final Map<Double, Map<Tradeable, Double>> sellVol =
      new ConcurrentHashMap<Double, Map<Tradeable, Double>>();
  protected final Map<Double, Map<Tradeable, Double>> previousLast =
      new ConcurrentHashMap<Double, Map<Tradeable, Double>>();
  protected final HashMap<Tradeable, HashMap<Double, EventBean>> lastEvent =
      new HashMap<Tradeable, HashMap<Double, EventBean>>();

  protected final Map<Double, Map<Tradeable, HeikinAshiBar>> previousHeikinAshiBar =
      new ConcurrentHashMap<Double, Map<Tradeable, HeikinAshiBar>>();
  protected Map<Double, EPStatementHandleCallback> handle =
      new ConcurrentHashMap<Double, EPStatementHandleCallback>();

  public HeikinAshiBarPlugInView(
      AgentInstanceViewFactoryChainContext agentInstanceViewFactoryContext,
      ExprNode timestampExpression,
      ExprNode valueExpression) {
    this.agentInstanceViewFactoryContext = agentInstanceViewFactoryContext;
    this.timestampExpression = timestampExpression;
    this.valueExpression = valueExpression;
    this.scheduleSlot =
        agentInstanceViewFactoryContext.getStatementContext().getScheduleBucket().allocateSlot();
  }

  public HeikinAshiBarPlugInView(
      AgentInstanceViewFactoryChainContext agentInstanceViewFactoryContext,
      ExprNode timestampExpression,
      ExprNode valueExpression,
      ExprNode volumeExpression,
      ExprNode marketExpression,
      ExprNode intervalExpression) {
    this.agentInstanceViewFactoryContext = agentInstanceViewFactoryContext;
    this.timestampExpression = timestampExpression;
    this.valueExpression = valueExpression;
    this.volumeExpression = volumeExpression;
    this.marketExpression = marketExpression;
    this.intervalExpression = intervalExpression;
    this.scheduleSlot =
        agentInstanceViewFactoryContext.getStatementContext().getScheduleBucket().allocateSlot();
  }

  @Override
  public View cloneView() {
    return new HeikinAshiBarPlugInView(
        agentInstanceViewFactoryContext,
        timestampExpression,
        valueExpression,
        volumeExpression,
        marketExpression,
        intervalExpression);
  }

  @Override
  public void update(EventBean[] newData, EventBean[] oldData) {
    if (newData == null) {
      return;
    }

    EventBean[] eventsPerStream = new EventBean[1];

    for (EventBean theEvent : newData) {

      eventsPerStream[0] = theEvent;
      log.trace(
          this.getClass().getSimpleName()
              + ":update recieved new event"
              + eventsPerStream[0].toString());

      Double interval =
          (Double)
              intervalExpression
                  .getExprEvaluator()
                  .evaluate(eventsPerStream, true, agentInstanceViewFactoryContext);

      Long timestamp =
          (Long)
              timestampExpression
                  .getExprEvaluator()
                  .evaluate(eventsPerStream, true, agentInstanceViewFactoryContext);
      Long timestampMinute = removeSeconds(timestamp, interval);
      double value =
          (Double)
              valueExpression
                  .getExprEvaluator()
                  .evaluate(eventsPerStream, true, agentInstanceViewFactoryContext);
      double volume =
          (Double)
              volumeExpression
                  .getExprEvaluator()
                  .evaluate(eventsPerStream, true, agentInstanceViewFactoryContext);

      Tradeable market = null;
      if (marketExpression != null)
        market =
            (Tradeable)
                marketExpression
                    .getExprEvaluator()
                    .evaluate(eventsPerStream, true, agentInstanceViewFactoryContext);

      eventsPerMarketPerStream.put(market, eventsPerStream);

      // test if this minute has already been published, the event is too late
      if (interval == null
          || interval == 0
          || timestamp == null
          || timestamp == 0
          || timestampMinute == null
          || timestampMinute == 0
          || (marketExpression != null && market == null)) {
        log.error(
            this.getClass().getSimpleName()
                + ":unable to create bar with interval: "
                + interval
                + " timestamp: "
                + timestamp
                + " timestampMinute:"
                + timestampMinute
                + " market: "
                + market
                + " cutoffTimestampMinute: "
                + cutoffTimestampMinute);

        return;
      }

      log.trace(
          this.getClass().getSimpleName()
              + ":update determing bar for interval: "
              + interval
              + " timestamp: "
              + timestamp
              + " timestampMinute:"
              + timestampMinute
              + " value: "
              + value
              + " market: "
              + market
              + " cutoffTimestampMinute: "
              + cutoffTimestampMinute);
      if (timestamp <= getCutoffTimestampMinute(market, interval)) {
        continue;
      }
      setCurrentTimestampMinute(market, interval, timestampMinute);

      // currentTimestampMinute = timestampMinute;
      log.trace(
          this.getClass().getSimpleName() + ":update - apply value for " + market + ", " + value);

      applyValue(market, interval, value, volume);

      // schedule a callback to fire in case no more events arrive
      log.trace(
          this.getClass().getSimpleName()
              + ":update - scheduleCallback for interval: "
              + interval.longValue()
              + " slack "
              + LATE_EVENT_SLACK_SECONDS);

      scheduleCallback(interval);
    }
  }

  @Override
  protected void setCurrentTimestampMinute(Tradeable market, Double interval, Long timestamp) {
    if ((timestamp == null
            || currentTimestampMinute.get(market) == null
            || currentTimestampMinute.get(interval) == null)
        || (timestamp > 0 && timestamp > currentTimestampMinute.get(market).get(interval))) {
      if (currentTimestampMinute.get(market) == null) {
        Map<Double, Long> intervalTimestampMinute = new ConcurrentHashMap<Double, Long>();
        intervalTimestampMinute.put(interval, timestamp);
        currentTimestampMinute.put(market, intervalTimestampMinute);
      } else currentTimestampMinute.get(market).put(interval, timestamp);

    } else {
      log.error(
          "setCurrentTimestampMinute: unable to set current time stamp minute as currnet currentTimestampMinute "
              + currentTimestampMinute.get(market).get(interval)
              + " is greater than new time stamp "
              + timestamp);
    }
  }

  @Override
  public Long getCutoffTimestampMinute(Tradeable market, Double interval) {
    if ((cutoffTimestampMinute == null
            || cutoffTimestampMinute.get(market) == null
            || cutoffTimestampMinute.get(market).get(interval) == null)
        && getCurrentTimestampMinute(market, interval) != 0) {
      long cutOff =
          removeSeconds(
              agentInstanceViewFactoryContext
                  .getStatementContext()
                  .getSchedulingService()
                  .getTime(),
              interval);
      setCutoffTimestampMinute(market, interval, cutOff);
    }

    if (cutoffTimestampMinute != null
        && cutoffTimestampMinute.get(market) != null
        && cutoffTimestampMinute.get(market).get(interval) != null)
      return cutoffTimestampMinute.get(market).get(interval);
    else return 0L;
  }

  @Override
  protected void setCutoffTimestampMinute(Tradeable market, Double interval, Long cutoff) {
    if (cutoffTimestampMinute.get(market) == null
        || cutoffTimestampMinute.get(market).get(interval) == null
        || (cutoffTimestampMinute.get(market) != null
            && cutoffTimestampMinute.get(market).get(interval) != null
            && (cutoff != null && cutoff > cutoffTimestampMinute.get(market).get(interval)))) {

      if (cutoffTimestampMinute.get(market) == null) {
        Map<Double, Long> intervalTimestampMinute = new ConcurrentHashMap<Double, Long>();
        intervalTimestampMinute.put(interval, cutoff);
        cutoffTimestampMinute.put(market, intervalTimestampMinute);
      } else cutoffTimestampMinute.get(market).put(interval, cutoff);
    }
  }

  @Override
  protected void scheduleCallback(final Double interval) {
    // TODO we need to check this handle, as if it is nmight be there with wrong cutoff.
    // xif (handle.get(interval) == null) {
    // remove old schedule

    long currentTime =
        agentInstanceViewFactoryContext.getStatementContext().getSchedulingService().getTime();
    long currentRemoveSeconds = removeSeconds(currentTime, interval);
    // long targetTime = currentRemoveSeconds + (86400 + LATE_EVENT_SLACK_SECONDS) * 1000; // leave
    // some seconds for late comers

    long targetTime =
        (long)
            (currentRemoveSeconds
                + ((interval + LATE_EVENT_SLACK_SECONDS)
                    * 1000)); // leave some seconds for late comers

    long scheduleAfterMSec = targetTime - currentTime;
    if (scheduleAfterMSec <= 0) return;
    log.trace(
        this.getClass().getSimpleName()
            + ":scheduleCallback - scheduling Callback after : "
            + scheduleAfterMSec
            + " for currentTime "
            + (new Instant(currentTime))
            + " targetTime "
            + (new Instant(targetTime))
            + " for interval "
            + interval);

    ScheduleHandleCallback callback =
        new ScheduleHandleCallback() {
          /*			private void scheduleCallback(final Tradeable market, final Double interval) {
          	class ScheduleHandleCallback {
          		Tradeable market;
          		Double interval;

          		public ScheduleHandleCallback(Tradeable market, Double interval) {
          			this.market=market;
          			this.interval=interval;
          		}

          	}
          }*/
          @Override
          public void scheduledTrigger(ExtensionServicesContext extensionServicesContext) {
            //        if (extensionServicesContext == null)
            //        return;
            log.trace(
                this.getClass().getSimpleName()
                    + ":scheduledTrigger - triggered at "
                    + (new Instant(
                        agentInstanceViewFactoryContext
                            .getStatementContext()
                            .getSchedulingService()
                            .getTime()))
                    + " :  for interval "
                    + interval
                    + " with handle "
                    + handle);
            if (handle.get(interval) != null) {
              log.trace(
                  this.getClass().getSimpleName()
                      + ":scheduledTrigger at "
                      + (new Instant(
                          agentInstanceViewFactoryContext
                              .getStatementContext()
                              .getSchedulingService()
                              .getTime()))
                      + " :  for interval "
                      + interval
                      + " removing handle from  "
                      + handle);
              agentInstanceViewFactoryContext
                  .getStatementContext()
                  .getSchedulingService()
                  .remove(handle.get(interval), scheduleSlot);
              handle.remove(interval);
            }
            //	handle.get(interval).get(interval).getAgentInstanceHandle().get
            //	handle.remove(market); // clear out schedule handle
            if (previousLast.get(interval) != null) {
              for (Tradeable market : previousLast.get(interval).keySet()) {
                log.trace(
                    this.getClass().getSimpleName()
                        + ":scheduledTrigger at "
                        + (new Instant(
                            agentInstanceViewFactoryContext
                                .getStatementContext()
                                .getSchedulingService()
                                .getTime()))
                        + " :  for interval "
                        + interval
                        + " posting data as existing previousLast value of "
                        + previousLast);

                HeikinAshiBarPlugInView.this.postData(
                    interval,
                    market,
                    agentInstanceViewFactoryContext
                        .getStatementContext()
                        .getSchedulingService()
                        .getTime());
              }
            }
            // once we have been triggered we should add oursleves back.

            //	long currentTime =
            // agentInstanceViewFactoryContext.getStatementContext().getSchedulingService().getTime();
            // long currentRemoveSeconds = removeSeconds(currentTime, interval);
            // long targetTime = currentRemoveSeconds + (86400 + LATE_EVENT_SLACK_SECONDS) * 1000;
            // // leave some seconds for late comers

            // long targetTime = (long) (currentRemoveSeconds + ((scheduleAfterMSec +
            // LATE_EVENT_SLACK_SECONDS) * 1000)); // leave some seconds for late comers

            long scheduleAfterMSec = (long) (interval * 1000);
            handle.put(
                interval,
                new EPStatementHandleCallback(
                    agentInstanceViewFactoryContext.getEpStatementAgentInstanceHandle(), this));

            agentInstanceViewFactoryContext
                .getStatementContext()
                .getSchedulingService()
                .add(scheduleAfterMSec, handle.get(interval), scheduleSlot);
            log.trace(
                this.getClass().getSimpleName()
                    + ":scheduledTrigger at "
                    + (new Instant(
                        agentInstanceViewFactoryContext
                            .getStatementContext()
                            .getSchedulingService()
                            .getTime()))
                    + " :  for interval "
                    + interval
                    + " with handle "
                    + handle
                    + " scheduled call back after "
                    + scheduleAfterMSec
                    + " at "
                    + new Instant(
                            agentInstanceViewFactoryContext
                                .getStatementContext()
                                .getSchedulingService()
                                .getTime())
                        .plus(scheduleAfterMSec));
          }
        };
    if (handle.get(interval) != null) {
      log.trace(
          this.getClass().getSimpleName()
              + ":scheduleCallback at "
              + (new Instant(
                  agentInstanceViewFactoryContext
                      .getStatementContext()
                      .getSchedulingService()
                      .getTime()))
              + " :  for interval "
              + interval
              + " removing handle from  "
              + handle);

      agentInstanceViewFactoryContext
          .getStatementContext()
          .getSchedulingService()
          .remove(handle.get(interval), scheduleSlot);
      handle.remove(interval);
    }

    handle.put(
        interval,
        new EPStatementHandleCallback(
            agentInstanceViewFactoryContext.getEpStatementAgentInstanceHandle(), callback));

    agentInstanceViewFactoryContext
        .getStatementContext()
        .getSchedulingService()
        .add(scheduleAfterMSec, handle.get(interval), scheduleSlot);
    log.trace(
        this.getClass().getSimpleName()
            + ":scheduleCallback at "
            + (new Instant(
                agentInstanceViewFactoryContext
                    .getStatementContext()
                    .getSchedulingService()
                    .getTime()))
            + " :  for interval "
            + interval
            + " with handle "
            + handle
            + " handle count "
            + agentInstanceViewFactoryContext
                .getStatementContext()
                .getSchedulingService()
                .getTimeHandleCount()
            + " scheduled call back after "
            + scheduleAfterMSec
            + " at "
            + new Instant(
                    agentInstanceViewFactoryContext
                        .getStatementContext()
                        .getSchedulingService()
                        .getTime())
                .plus(scheduleAfterMSec));

    // } else {
    // EPStatementHandleCallback scheduleHandle = handle.get(interval);

    //			log.trace(this.getClass().getSimpleName() + ":scheduledCallback call back already scheduled
    // with handle " + scheduleHandle);

    // }

  }

  @Override
  protected void applyValue(Tradeable market, double interval, double value, double volume) {

    synchronized (
        first.get(interval) == null || first.get(interval).get(market) == null
            ? new Object()
            : first.get(interval).get(market)) {
      if (first.get(interval) == null) {
        Map<Tradeable, Double> intervalFirst = new ConcurrentHashMap<Tradeable, Double>();
        intervalFirst.put(market, value);

        first.put(interval, intervalFirst);
      } else if (first.get(interval).get(market) == null) {
        first.get(interval).put(market, value);
      }

      if (last.get(interval) == null) {
        Map<Tradeable, Double> intervalLast = new ConcurrentHashMap<Tradeable, Double>();
        intervalLast.put(market, value);
        last.put(interval, intervalLast);
      } else {
        last.get(interval).put(market, value);
      }
      if (previousLast.get(interval) == null) {
        Map<Tradeable, Double> intervalLast = new ConcurrentHashMap<Tradeable, Double>();
        intervalLast.put(market, value);
        previousLast.put(interval, intervalLast);
      } else {
        previousLast.get(interval).put(market, value);
      }

      if (min.get(interval) == null) {
        Map<Tradeable, Double> intervalMin = new ConcurrentHashMap<Tradeable, Double>();
        intervalMin.put(market, value);
        min.put(interval, intervalMin);
      } else if (min.get(interval).get(market) == null) {
        min.get(interval).put(market, value);
      } else if (min.get(interval).get(market).compareTo(value) > 0) {
        min.get(interval).put(market, value);
      }

      if (max.get(interval) == null) {
        Map<Tradeable, Double> intervalMax = new ConcurrentHashMap<Tradeable, Double>();
        intervalMax.put(market, value);
        max.put(interval, intervalMax);
      } else if (max.get(interval).get(market) == null) {
        max.get(interval).put(market, value);
      } else if (max.get(interval).get(market).compareTo(value) < 0) {
        max.get(interval).put(market, value);
      }

      if (vol.get(interval) == null) {
        Map<Tradeable, Double> intervalVol = new ConcurrentHashMap<Tradeable, Double>();
        intervalVol.put(market, volume);
        vol.put(interval, intervalVol);
      } else if (vol.get(interval).get(market) == null) {
        vol.get(interval).put(market, volume);
      } else {
        vol.get(interval).put(market, volume + vol.get(interval).get(market));
      }
      if (volume < 0) {
        if (sellVol.get(interval) == null) {
          Map<Tradeable, Double> intervalVol = new ConcurrentHashMap<Tradeable, Double>();
          intervalVol.put(market, volume);
          sellVol.put(interval, intervalVol);
        } else if (sellVol.get(interval).get(market) == null) {
          sellVol.get(interval).put(market, volume);
        } else {
          sellVol.get(interval).put(market, volume + sellVol.get(interval).get(market));
        }
      }
      if (volume > 0) {
        if (buyVol.get(interval) == null) {
          Map<Tradeable, Double> intervalVol = new ConcurrentHashMap<Tradeable, Double>();
          intervalVol.put(market, volume);
          buyVol.put(interval, intervalVol);
        } else if (buyVol.get(interval).get(market) == null) {
          buyVol.get(interval).put(market, volume);
        } else {
          buyVol.get(interval).put(market, volume + buyVol.get(interval).get(market));
        }
      }
    }
  }

  protected static EventType getEventType(EventAdapterService eventAdapterService) {
    return eventAdapterService.addBeanType(
        HeikinAshiBar.class.getName(), HeikinAshiBar.class, false, false, false);
  }

  protected void postData(double interval, Tradeable market, long currentTime) {
    HeikinAshiBar barValue;

    //		if (first.get(interval) != null && first.get(interval).get(market) != null &&
    // last.get(interval) != null && last.get(interval).get(market) != null
    //			&& min.get(interval) != null && min.get(interval).get(market) != null && max.get(interval)
    // != null && max.get(interval).get(market) != null
    //		&& market != null) {
    if (market != null
        && previousLast.get(interval) != null
        && previousLast.get(interval).get(market) != null) {

      try {
        long currentRemoveSeconds = removeSeconds(currentTime, interval);
        log.trace(
            this.getClass().getSimpleName()
                + ":PostData: generating bar at "
                + new Instant(currentTime)
                + " with  currenttimestamp "
                + (new Instant(getCurrentTimestampMinute(market, interval)))
                + " and "
                + (new Instant(getCurrentTimestampMinute(market, interval))));
        Double open =
            (first.get(interval) != null && first.get(interval).get(market) != null)
                ? first.get(interval).get(market)
                : previousLast.get(interval).get(market);
        Double high =
            (max.get(interval) != null && max.get(interval).get(market) != null)
                ? max.get(interval).get(market)
                : previousLast.get(interval).get(market);
        Double low =
            (min.get(interval) != null && min.get(interval).get(market) != null)
                ? min.get(interval).get(market)
                : previousLast.get(interval).get(market);
        Double close =
            (last.get(interval) != null && last.get(interval).get(market) != null)
                ? last.get(interval).get(market)
                : previousLast.get(interval).get(market);
        Double volume =
            (vol.get(interval) != null && vol.get(interval).get(market) != null)
                ? vol.get(interval).get(market)
                : 0d;
        Double buyVolume =
            (buyVol.get(interval) != null && buyVol.get(interval).get(market) != null)
                ? buyVol.get(interval).get(market)
                : 0d;
        Double sellVolume =
            (sellVol.get(interval) != null && sellVol.get(interval).get(market) != null)
                ? sellVol.get(interval).get(market)
                : 0d;
        Double heikinAshiOpen;
        if (previousHeikinAshiBar.get(interval) == null
            || previousHeikinAshiBar.get(interval).get(market) == null)
          heikinAshiOpen = (open + close) / 2;
        else
          heikinAshiOpen =
              (previousHeikinAshiBar.get(interval).get(market).getOpen()
                      + previousHeikinAshiBar.get(interval).get(market).getClose())
                  / 2;

        if (volume != 0
            && currentTimestampMinute.get(market) == null
            && currentTimestampMinute.get(market).get(interval) == null) log.debug("error");
        Long timestamp =
            (currentTimestampMinute.get(market) != null
                    && currentTimestampMinute.get(market).get(interval) != null)
                ? currentTimestampMinute.get(market).get(interval)
                : (currentRemoveSeconds - (long) (interval * 1000));

        // long targetTime = currentRemoveSeconds + (86400 + LATE_EVENT_SLACK_SECONDS) * 1000; //
        // leave some seconds for late comers

        barValue =
            new HeikinAshiBar(
                timestamp,
                interval,
                heikinAshiOpen,
                (heikinAshiOpen + high + low + close) / 4,
                Math.max(Math.max(high, heikinAshiOpen), close),
                Math.min(Math.min(low, heikinAshiOpen), close),
                volume,
                buyVolume,
                sellVolume,
                market);
        if (previousHeikinAshiBar.get(interval) == null) {
          Map<Tradeable, HeikinAshiBar> intervalBar =
              new ConcurrentHashMap<Tradeable, HeikinAshiBar>();
          intervalBar.put(market, barValue);
          previousHeikinAshiBar.put(interval, intervalBar);
        } else {
          previousHeikinAshiBar.get(interval).put(market, barValue);
        }

      } catch (Exception | Error ex) {
        log.error(
            this.getClass().getSimpleName()
                + ":PostData: Unable to generate "
                + interval
                + " heikinAshiBar for market: "
                + market
                + " with timestamp:"
                + currentTimestampMinute.get(market)
                + " first:"
                + first.get(interval)
                + " high: "
                + max.get(interval)
                + " low: "
                + min.get(interval)
                + " close: "
                + last.get(interval));
        return;
      }

      EventBean outgoing =
          agentInstanceViewFactoryContext
              .getStatementContext()
              .getEventAdapterService()
              .adapterForBean(barValue);
      if (lastEvent.get(market) == null) {
        log.trace(
            this.getClass().getSimpleName()
                + ": PostData -  updating child "
                + outgoing.getUnderlying().toString());

        this.updateChildren(new EventBean[] {outgoing}, null);
      } else {

        log.trace(
            this.getClass().getSimpleName()
                + ": PostData - updating child outgoing event "
                + outgoing.getUnderlying().toString()
                + " last event "
                + ((lastEvent != null
                        && lastEvent.get(market) != null
                        && lastEvent.get(market).get(interval) != null)
                    ? (lastEvent.get(market).get(interval).getUnderlying().toString())
                    : ""));

        this.updateChildren(
            new EventBean[] {outgoing}, new EventBean[] {lastEvent.get(market).get(interval)});
      }

      if (lastEvent.get(market) == null) {
        HashMap<Double, EventBean> intervalLastEvent = new HashMap<Double, EventBean>();
        intervalLastEvent.put(interval, outgoing);
        lastEvent.put(market, intervalLastEvent);
      } else lastEvent.get(market).put(interval, outgoing);

      Long timestampMinute = removeSeconds(currentTime, interval);

      // if (cutoffTimestampMinute.containsKey(market))
      setCutoffTimestampMinute(market, interval, timestampMinute);
      // cutoffTimestampMinute = currentTimestampMinute;
      log.trace(
          this.getClass().getSimpleName()
              + ":postData - removing values for market "
              + market
              + ", interval"
              + interval);

      if (first.get(interval) != null && first.get(interval).get(market) != null)
        first.get(interval).remove(market);

      if (last.get(interval) != null && last.get(interval).get(market) != null)
        last.get(interval).remove(market);
      if (max.get(interval) != null && max.get(interval).get(market) != null)
        max.get(interval).remove(market);
      if (min.get(interval) != null && min.get(interval).get(market) != null)
        min.get(interval).remove(market);
      if (vol.get(interval) != null && vol.get(interval).get(market) != null)
        vol.get(interval).remove(market);
      if (sellVol.get(interval) != null && sellVol.get(interval).get(market) != null)
        sellVol.get(interval).remove(market);
      if (buyVol.get(interval) != null && buyVol.get(interval).get(market) != null)
        buyVol.get(interval).remove(market);
      if (currentTimestampMinute.get(market) != null
          && currentTimestampMinute.get(market).get(interval) != null)
        currentTimestampMinute.get(market).remove(interval);
      // if (currentTimestampMinute.get(market) != null &&
      // currentTimestampMinute.get(market).get(interval) != null)

      // currentTimestampMinute = null;

    } else
      log.error(
          this.getClass().getSimpleName()
              + ":PostData: Unable to generate heikinAshiBar for interval "
              + interval
              + "  with market:"
              + market
              + "and first: "
              + first
              + " and last: "
              + last
              + " and min:"
              + min
              + " and max: "
              + max);

    // }

  }
}

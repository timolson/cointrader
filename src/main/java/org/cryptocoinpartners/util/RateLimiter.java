package org.cryptocoinpartners.util;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.joda.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;

/**
 * Implements an Executor which delays execution until a rate limit is fulfilled.
 * 
 * @author Tim Olson
 */
@SuppressWarnings("NullableProblems")
public class RateLimiter implements Executor {

  /**
   * Constructs a RateLimiter with a single-threaded executor
   * 
   * @see #RateLimiter(java.util.concurrent.Executor, int, org.joda.time.Duration)
   */
  public RateLimiter(final int invocations, final Duration per) {
    this(null, invocations, per);
  }

  /**
   * Implements the Token Bucket algorithm to provide a maximum number of invocations within each fixed time window. Useful for rate-limiting. If
   * given a non-null executor, the scheduled runnables are passed to that executor for execution at the rate limit. If executor is null, a
   * single-threaded executor is used
   * 
   * @param executor the Executor which executes the Runnables. the executor is not called with the runnable until the rate limit has been fulfilled
   * @param invocations number of queries allowed during each time window
   * @param per the duration of each time window
   */
  public RateLimiter(Executor executor, final int invocations, final Duration per) {
    this.per = per;
    if (executor != null) {
      this.executor = executor;

      this.limiter = new SimpleTimeLimiter();
    } else {
      this.executor = Executors.newSingleThreadExecutor();
      this.limiter = new SimpleTimeLimiter();
    }

    // This thread fills the TokenBucket with available requests every time window
    ScheduledThreadPoolExecutor replenisher = new ScheduledThreadPoolExecutor(1);
    replenisher.scheduleAtFixedRate(new Runnable() {
      @Override
      public void run() {
        int permitsToCreate = invocations - requestsAvailable.availablePermits();
        if (permitsToCreate > 0) {
          synchronized (requestsAvailable) {
            // bring the number of requests up to the maximum size per time window
            requestsAvailable.release(permitsToCreate);
          }
        }
      }
    }, 0, per.getMillis(), TimeUnit.MILLISECONDS);

    pump = new RunnablePump();
    pump.start();
  }

  @Override
  public void execute(Runnable runnable) {
    waitingRunnables.add(runnable);
    runnableCount.release();
  }

  public void execute(final Callable callable) {
    waitingCallables.add(callable);
    callableCount.release();

    Runnable run = new Runnable() {
      @Override
      public void run() {
        try {
          Object o = callable.call();
          log.info(this.getClass().getSimpleName() + "execute - " + callable.getClass().getSimpleName() + " returned " + o);

        } catch (Exception e) {
          log.error(this.getClass().getSimpleName() + "execute - " + callable.getClass().getSimpleName() + " returned " + e);
          try {
            throw e;
          } catch (Exception e1) {
            // TODO Auto-generated catch block
            log.error(this.getClass().getSimpleName() + "execute - " + callable.getClass().getSimpleName() + " returned " + e1);

          }

        }
      }
    };
    execute(run);
  }

  public boolean remove(Runnable runnable) throws Throwable {
    if (waitingRunnables.remove(runnable)) {
      finalize();
      // runnableCount.release();
      // requestsAvailable.release();
      return true;
    }
    return false;
  }

  public Collection<Runnable> getRunnables() {
    return waitingRunnables;
  }

  public void stopRunnablePump() {
    pump.interrupt();
  }

  //}
  // This thread waits for a Runnable to be put on the waitingRunnables queue, then waits for the rate limit
  // to be available, then pushes the Runnable to the executor for execution.
  private class RunnablePump extends Thread {
    private RunnablePump() {
      setDaemon(true);
    }

    @Override
    public void run() {
      boolean running = true;
      while (running && !isInterrupted()) {
        try {
          runnableCount.acquire(); // wait until there are any scheduled runnables available
          Runnable runnable;
          Callable callable;
          runnable = waitingRunnables.poll();
          callable = waitingCallables.poll();
          //TODO we need to override this somehow for when we rate quering the latest trade time.
          if (callable != null)
            try {
              limiter.callWithTimeout(callable, 3000, TimeUnit.MILLISECONDS, false);
            } catch (UncheckedTimeoutException ute) {
              log.error(this.getClass().getSimpleName() + ":run - unable to complete " + callable.getClass().getSimpleName() + " within "
                  + per.getMillis() + " " + TimeUnit.MILLISECONDS);

            } catch (Exception e) {
              log.error(
                  this.getClass().getSimpleName() + ":run - unable to complete " + callable.getClass().getSimpleName() + " within  "
                      + per.getMillis() + " " + TimeUnit.MILLISECONDS + "  stack trace", e);
              // TODO Auto-generated catch block

            }
          else
            executor.execute(runnable);

          requestsAvailable.acquire(); // wait until there is request limit available, issue here with acquring the lock
        } catch (InterruptedException e) {

          running = false;
          break;
        }
      }
    }
  }

  @Override
  public void finalize() throws Throwable {
    pump.interrupt();
    super.finalize();
  }

  private final RunnablePump pump;
  private final Semaphore runnableCount = new Semaphore(0);
  private final Semaphore callableCount = new Semaphore(0);

  private final Semaphore requestsAvailable = new Semaphore(0);
  private final Queue<Runnable> waitingRunnables = new ConcurrentLinkedQueue<>();
  private final Queue<Callable> waitingCallables = new ConcurrentLinkedQueue<>();
  protected static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.util.ratelimiter");

  private final Executor executor;
  private final TimeLimiter limiter;
  private final Duration per;

}

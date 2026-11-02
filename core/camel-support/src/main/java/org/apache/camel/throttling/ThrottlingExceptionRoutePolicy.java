/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.throttling;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Route;
import org.apache.camel.RouteAware;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.TimeSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modeled after the circuit breaker {@link ThrottlingInflightRoutePolicy} this {@link RoutePolicy} will stop consuming
 * from an endpoint based on the type of exceptions that are thrown and the threshold setting.
 *
 * The scenario: if a route cannot process data from an endpoint due to problems with resources used by the route (ie
 * database down) then it will stop consuming new messages from the endpoint by stopping the consumer. The
 * implementation is comparable to the Circuit Breaker pattern. After a set amount of time, it will move to a half open
 * state and attempt to determine if the consumer can be started. There are two ways to determine if a route can be
 * closed after being opened (1) start the consumer and let the next exchange decide: a success closes the circuit and a
 * failure opens it again (2) call the {@link ThrottlingExceptionHalfOpenHandler} The second option allows a custom
 * check to be performed without having to take on the possibility of multiple messages from the endpoint. The idea is
 * that a handler could run a simple test (ie select 1 from dual) to determine if the processes that cause the route to
 * be open are now available
 */
@Metadata(label = "bean",
          description = "A throttle based RoutePolicy which is modelled after the circuit breaker and will stop consuming"
                        + " from an endpoint based on the type of exceptions that are thrown and the threshold settings.",
          annotations = { "interfaceName=org.apache.camel.spi.RoutePolicy" })
@Configurer(metadataOnly = true)
public class ThrottlingExceptionRoutePolicy extends RoutePolicySupport implements CamelContextAware, RouteAware {

    private static final Logger LOG = LoggerFactory.getLogger(ThrottlingExceptionRoutePolicy.class);

    private static final int STATE_CLOSED = 0;
    private static final int STATE_HALF_OPEN = 1;
    private static final int STATE_OPEN = 2;

    private CamelContext camelContext;
    private Route route;
    private final Lock lock = new ReentrantLock();
    private CamelLogger stateLogger;

    // configuration
    @Metadata(description = "How many failed messages within the window would trigger the circuit breaker to open",
              defaultValue = "50")
    private volatile int failureThreshold = 50;
    @Metadata(description = "Sliding window for how long time to go back (in millis) when counting number of failures",
              defaultValue = "60000")
    private volatile long failureWindow = 60000;
    @Metadata(description = "Interval (in millis) for how often to check whether a currently open circuit breaker may work again",
              defaultValue = "30000")
    private volatile long halfOpenAfter = 30000;
    @Metadata(description = "Whether to always keep the circuit breaker open (never closes). This is only intended for development and testing purposes.")
    private boolean keepOpen;
    @Metadata(description = "Allows to only throttle based on certain types of exceptions. Multiple exceptions (use FQN class name) can be separated by comma.")
    private String exceptions;
    @Metadata(description = "Logging level for state changes", defaultValue = "DEBUG")
    private LoggingLevel stateLoggingLevel = LoggingLevel.DEBUG;
    private List<Class<?>> throttledExceptions;
    // handler for half open circuit can be used instead of resuming route to check on resources
    @Metadata(label = "advanced",
              description = "Custom check to perform whether the circuit breaker can move to half-open state."
                            + " If set then this is used instead of resuming the route.")
    private ThrottlingExceptionHalfOpenHandler halfOpenHandler;

    // stateful information
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicInteger success = new AtomicInteger();
    private final AtomicInteger state = new AtomicInteger(STATE_CLOSED);
    private final AtomicBoolean keepOpenBool = new AtomicBoolean();
    // the half open check: one at a time, on a scheduler created through the ExecutorServiceManager when the circuit
    // opens the first time, and shut down when the last route of the policy is removed or the policy is stopped;
    // guarded by schedulerLock (not by lock, which a running check holds while it suspends or resumes the consumer)
    private final Lock schedulerLock = new ReentrantLock();
    private final Set<String> routeIds = new HashSet<>();
    private volatile ScheduledExecutorService halfOpenScheduler;
    private volatile ScheduledFuture<?> halfOpenCheck;
    // set when the scheduler is shut down, so that a check that is running then does not create a new one; cleared
    // when the policy or a route of it is started again
    private boolean halfOpenStopped;
    private volatile long lastFailure;
    private volatile long openedAt;
    // the times of the most recent failures (at most failureThreshold of them), oldest first, guarded by failureLock
    // (not by lock, which is held while the consumer is suspended or resumed)
    private final Lock failureLock = new ReentrantLock();
    private final Deque<Long> failureTimes = new ArrayDeque<>();

    public ThrottlingExceptionRoutePolicy() {
    }

    public ThrottlingExceptionRoutePolicy(int threshold, long failureWindow, long halfOpenAfter,
                                          List<Class<?>> handledExceptions) {
        this(threshold, failureWindow, halfOpenAfter, handledExceptions, false);
    }

    public ThrottlingExceptionRoutePolicy(int threshold, long failureWindow, long halfOpenAfter,
                                          List<Class<?>> handledExceptions, boolean keepOpen) {
        this.throttledExceptions = handledExceptions;
        this.failureWindow = failureWindow;
        this.halfOpenAfter = halfOpenAfter;
        this.failureThreshold = threshold;
        this.keepOpenBool.set(keepOpen);
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public Route getRoute() {
        return route;
    }

    @Override
    public void setRoute(Route route) {
        this.route = route;
    }

    public List<Class<?>> getThrottledExceptions() {
        return throttledExceptions;
    }

    public String getExceptions() {
        return exceptions;
    }

    public void setExceptions(String exceptions) {
        this.exceptions = exceptions;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        this.stateLogger = new CamelLogger(LOG, stateLoggingLevel);

        // with 0 or less, a check that keeps the circuit open (keepOpen, or a halfOpenHandler that is not ready)
        // would schedule the next check without delay, a busy loop on the scheduler thread
        checkHalfOpenAfter(halfOpenAfter);

        if (exceptions != null && throttledExceptions == null) {
            var list = new ArrayList<Class<?>>();
            for (String fqn : exceptions.split(",")) {
                Class<?> clazz = camelContext.getClassResolver().resolveMandatoryClass(fqn);
                list.add(clazz);
            }
            this.throttledExceptions = list;
        }
    }

    @Override
    public void onInit(Route route) {
        LOG.debug("Initializing ThrottlingExceptionRoutePolicy route policy");
        // creates the exception handler that logs a failure to suspend or resume the consumer
        super.onInit(route);
        if (camelContext == null) {
            camelContext = route.getCamelContext();
        }
        addRoute(route);
        logState();
    }

    @Override
    public void onStart(Route route) {
        // also here, not only in onInit, so that a started route is always tracked (a CamelContext that is stopped
        // calls onRemove for each route; when started again it creates the routes again and calls onInit too)
        addRoute(route);
        // if keepOpen then start w/ the circuit open
        if (keepOpenBool.get()) {
            // the circuit may still be open from before the route was stopped, and then the started consumer must
            // be suspended again
            reopenCircuit(route);
        }
    }

    @Override
    public void onRemove(Route route) {
        // the policy is a service of the CamelContext, which does not stop it when its routes are removed
        schedulerLock.lock();
        try {
            routeIds.remove(route.getRouteId());
            if (routeIds.isEmpty()) {
                shutdownHalfOpenScheduler();
            }
        } finally {
            schedulerLock.unlock();
        }
    }

    @Override
    protected void doStart() throws Exception {
        allowHalfOpenChecks();
    }

    @Override
    protected void doStop() throws Exception {
        shutdownHalfOpenScheduler();
    }

    private void addRoute(Route route) {
        schedulerLock.lock();
        try {
            routeIds.add(route.getRouteId());
            halfOpenStopped = false;
        } finally {
            schedulerLock.unlock();
        }
    }

    private void allowHalfOpenChecks() {
        schedulerLock.lock();
        try {
            halfOpenStopped = false;
        } finally {
            schedulerLock.unlock();
        }
    }

    private void shutdownHalfOpenScheduler() {
        schedulerLock.lock();
        try {
            halfOpenStopped = true;
            ScheduledFuture<?> check = halfOpenCheck;
            if (check != null) {
                check.cancel(false);
                halfOpenCheck = null;
            }
            ScheduledExecutorService scheduler = halfOpenScheduler;
            if (scheduler != null) {
                halfOpenScheduler = null;
                // does not wait for a check that is running
                camelContext.getExecutorServiceManager().shutdown(scheduler);
            }
        } finally {
            schedulerLock.unlock();
        }
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        if (keepOpenBool.get()) {
            if (state.get() != STATE_OPEN) {
                LOG.debug("Opening circuit (keepOpen is true)");
                openCircuit(route);
            }
        } else {
            boolean failed = hasFailed(exchange);
            if (failed) {
                // record the failure
                failures.incrementAndGet();
                long now = TimeSource.currentTimeMillis();
                lastFailure = now;
                recordFailureTime(now);
            } else {
                success.incrementAndGet();
            }

            // check for state change
            calculateState(route, failed);
        }
    }

    /**
     * Uses similar approach as circuit breaker if the exchange has an exception that we are watching then we count that
     * as a failure otherwise we ignore it
     */
    private boolean hasFailed(Exchange exchange) {
        if (exchange == null) {
            return false;
        }

        boolean answer = false;

        if (exchange.getException() != null) {
            if (throttledExceptions == null || throttledExceptions.isEmpty()) {
                // if no exceptions defined then always fail
                // (ie) assume we throttle on all exceptions
                answer = true;
            } else {
                answer = matchesException(exchange.getException());
            }
        }

        if (LOG.isDebugEnabled()) {
            String exceptionName
                    = exchange.getException() == null ? "none" : exchange.getException().getClass().getSimpleName();
            LOG.debug("hasFailed ({}) with Throttled Exception: {} for exchangeId: {}", answer, exceptionName,
                    exchange.getExchangeId());
        }
        return answer;
    }

    /**
     * Whether the exception, or one of its causes, is one of the exceptions to throttle on. Suppressed exceptions (such
     * as an exception thrown in a doFinally block) are not matched, the same as onException and doCatch.
     */
    private boolean matchesException(Throwable exception) {
        // walk the caused by hierarchy
        for (Throwable throwable : ObjectHelper.createExceptionIterable(exception)) {
            for (Class<?> type : throttledExceptions) {
                if (type.isInstance(throwable)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void recordFailureTime(long time) {
        failureLock.lock();
        try {
            failureTimes.addLast(time);
            trimFailureTimes();
        } finally {
            failureLock.unlock();
        }
    }

    private void clearFailureTimes() {
        failureLock.lock();
        try {
            failureTimes.clear();
        } finally {
            failureLock.unlock();
        }
    }

    private void trimFailureTimes() {
        // only the most recent failureThreshold failures decide whether the threshold is exceeded
        int max = Math.max(failureThreshold, 1);
        while (failureTimes.size() > max) {
            failureTimes.removeFirst();
        }
    }

    /**
     * Calculates the state of the circuit.
     *
     * @param route          the route
     * @param exchangeFailed whether the exchange that is done failed, or null when there is no exchange (the check of
     *                       an open circuit after halfOpenAfter)
     */
    private void calculateState(Route route, Boolean exchangeFailed) {

        // have we reached the failure limit?
        boolean failureLimitReached = isThresholdExceeded();

        if (state.get() == STATE_CLOSED) {
            if (failureLimitReached) {
                LOG.debug("Opening circuit...");
                openCircuit(route);
            }
        } else if (state.get() == STATE_HALF_OPEN) {
            // the exchange done in the half open state decides: a failure opens the circuit again, and a success
            // closes it. The failures that opened the circuit must not count against it again: they may still be within
            // the failure window (with the defaults the circuit is half opened after 30 seconds, and the window is 60)
            if (exchangeFailed == null) {
                // the half open check of the timer, after an exchange that was done at the same time has already half
                // opened the circuit: the next exchange decides
                LOG.debug("Keeping circuit half open (waiting for an exchange)...");
            } else if (exchangeFailed) {
                LOG.debug("Opening circuit...");
                openCircuit(route);
            } else {
                LOG.debug("Closing circuit...");
                closeCircuit(route);
            }
        } else if (state.get() == STATE_OPEN) {
            if (!keepOpenBool.get()) {
                long elapsedTimeSinceOpened = TimeSource.currentTimeMillis() - openedAt;
                // read once, as halfOpenAfter can be changed at runtime (such as over JMX)
                long openFor = halfOpenAfter;
                if (openFor <= elapsedTimeSinceOpened) {
                    LOG.debug("Checking an open circuit...");
                    if (halfOpenHandler != null) {
                        if (isReadyToBeClosed()) {
                            LOG.debug("Closing circuit...");
                            closeCircuit(route);
                        } else {
                            LOG.debug("Opening circuit...");
                            // keep the circuit open, and check again after halfOpenAfter
                            reopenCircuit(route);
                        }
                    } else {
                        LOG.debug("Half opening circuit...");
                        halfOpenCircuit(route);
                    }
                } else if (exchangeFailed == null) {
                    LOG.debug("Keeping circuit open (time not elapsed)...");
                    // the half open timer fired before halfOpenAfter, as it was raised after the timer was scheduled:
                    // the consumer is suspended, so no exchange comes to check again. Check when the time is up
                    addHalfOpenTimer(route, openFor - elapsedTimeSinceOpened);
                } else {
                    // an exchange still in flight when the circuit opened: the half open timer does the check
                    LOG.debug("Keeping circuit open (time not elapsed)...");
                }
            } else {
                LOG.debug("Keeping circuit open (keepOpen is true)...");
                this.addHalfOpenTimer(route);
            }
        }

    }

    private boolean isReadyToBeClosed() {
        try {
            return halfOpenHandler.isReadyToBeClosed();
        } catch (Exception e) {
            // the circuit is kept open, and checked again after halfOpenAfter
            handleException(e);
            return false;
        }
    }

    protected boolean isThresholdExceeded() {
        boolean output = false;
        logState();
        // at least failureThreshold failures occurred within the window, that is the oldest of the most recent
        // failureThreshold failures occurred within the window
        long since = TimeSource.currentTimeMillis() - failureWindow;
        failureLock.lock();
        try {
            trimFailureTimes();
            Long oldest = failureTimes.peekFirst();
            if (failureTimes.size() >= failureThreshold && (oldest != null ? oldest : lastFailure) >= since) {
                output = true;
            }
        } finally {
            failureLock.unlock();
        }

        return output;
    }

    protected void openCircuit(Route route) {
        try {
            lock.lock();
            if (state.get() == STATE_OPEN) {
                return;
            }
            suspendOrStopConsumer(route.getConsumer());
            state.set(STATE_OPEN);
            openedAt = TimeSource.currentTimeMillis();
            this.addHalfOpenTimer(route);
            logState();
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Opens the circuit, also when it is already open: suspends the consumer and schedules the next half open check.
     * Unlike {@link #openCircuit(Route)}, which only acts when the circuit is not open yet, this is used to keep an
     * open circuit open.
     */
    protected void reopenCircuit(Route route) {
        try {
            lock.lock();
            suspendOrStopConsumer(route.getConsumer());
            state.set(STATE_OPEN);
            openedAt = TimeSource.currentTimeMillis();
            this.addHalfOpenTimer(route);
            logState();
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Schedules the check of the open circuit after {@link #getHalfOpenAfter()} milliseconds, and cancels the check
     * scheduled before, if any.
     */
    protected void addHalfOpenTimer(Route route) {
        addHalfOpenTimer(route, halfOpenAfter);
    }

    /**
     * Schedules the half open check after the given delay, replacing the one that is scheduled.
     */
    protected void addHalfOpenTimer(Route route, long delay) {
        lock.lock();
        schedulerLock.lock();
        try {
            if (halfOpenStopped) {
                LOG.debug("Not scheduling a half open check (the route is removed or the policy is stopped)");
                return;
            }
            ScheduledExecutorService scheduler = halfOpenScheduler;
            if (scheduler == null || scheduler.isShutdown()) {
                // one thread for the half open checks of this policy, named and managed by Camel
                scheduler = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                        "ThrottlingExceptionRoutePolicy");
                halfOpenScheduler = scheduler;
            }
            ScheduledFuture<?> previous = halfOpenCheck;
            halfOpenCheck = scheduler.schedule(new HalfOpenTask(route, scheduler), delay, TimeUnit.MILLISECONDS);
            if (previous != null) {
                // only one half open check at a time (a check that is running is not interrupted)
                previous.cancel(false);
            }
        } finally {
            schedulerLock.unlock();
            lock.unlock();
        }
    }

    protected void halfOpenCircuit(Route route) {
        try {
            lock.lock();
            // do not resume the consumer if the route controller has suspended or stopped the route in the meantime
            if (isResumeOrStartConsumerAllowed(route)) {
                resumeOrStartConsumer(route.getConsumer());
            }
            state.set(STATE_HALF_OPEN);
            logState();
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    protected void closeCircuit(Route route) {
        try {
            lock.lock();
            // only resume the consumer when the circuit is open, as then this policy has suspended the consumer
            // (when half open the consumer has already been resumed, so if it is suspended now, then by the route
            // controller), and not if the route controller has suspended or stopped the route in the meantime
            if (state.get() == STATE_OPEN && isResumeOrStartConsumerAllowed(route)) {
                resumeOrStartConsumer(route.getConsumer());
            }
            failures.set(0);
            success.set(0);
            clearFailureTimes();
            lastFailure = 0;
            openedAt = 0;
            state.set(STATE_CLOSED);
            logState();
        } catch (Exception e) {
            handleException(e);
        } finally {
            lock.unlock();
        }
    }

    private void logState() {
        if (stateLogger != null) {
            stateLogger.log(dumpState());
        }
    }

    public String getStateAsString() {
        return stateAsString(state.get());
    }

    public String dumpState() {
        String routeState = getStateAsString();
        if (failures.get() > 0) {
            return String.format("State %s, failures %d, last failure %d ms ago", routeState, failures.get(),
                    TimeSource.currentTimeMillis() - lastFailure);
        } else {
            return String.format("State %s, failures %d", routeState, failures.get());
        }
    }

    private static String stateAsString(int num) {
        if (num == STATE_CLOSED) {
            return "closed";
        } else if (num == STATE_HALF_OPEN) {
            return "half opened";
        } else {
            return "opened";
        }
    }

    class HalfOpenTask implements Runnable {
        private final Route route;
        private final ScheduledExecutorService scheduler;

        HalfOpenTask(Route route, ScheduledExecutorService scheduler) {
            this.route = route;
            this.scheduler = scheduler;
        }

        @Override
        public void run() {
            if (scheduler != halfOpenScheduler) {
                // the route was removed or the policy stopped since this check was scheduled
                return;
            }
            try {
                calculateState(route, null);
            } catch (Exception e) {
                // the scheduler would keep it in the future of this check, where no one sees it
                handleException(e);
            }
        }
    }

    public ThrottlingExceptionHalfOpenHandler getHalfOpenHandler() {
        return halfOpenHandler;
    }

    public void setHalfOpenHandler(ThrottlingExceptionHalfOpenHandler halfOpenHandler) {
        this.halfOpenHandler = halfOpenHandler;
    }

    public boolean getKeepOpen() {
        return this.keepOpenBool.get();
    }

    /**
     * Sets whether to keep the circuit breaker always open (never closes). This is only intended for development and
     * testing purposes.
     * <p>
     * When set to {@code true}, the circuit is opened immediately and synchronously (the consumer is suspended before
     * this method returns). Setting it back to {@code false} is deferred: the half-open timer will attempt to close the
     * circuit on its next tick (after {@link #getHalfOpenAfter()} milliseconds).
     * <p>
     * Note: a single {@link ThrottlingExceptionRoutePolicy} instance is designed for one route. When attached to
     * multiple routes, only the last route bound via {@link #setRoute(Route)} is acted upon by this setter.
     */
    public void setKeepOpen(boolean keepOpen) {
        this.keepOpenBool.set(keepOpen);
        // Immediately act on the new value so callers do not have to send a
        // trigger message and wait for the next onExchangeDone() callback:
        //  - keepOpen=true  → open the circuit right away (openCircuit is idempotent under its lock)
        //  - keepOpen=false → the half-open timer will close the circuit on its next tick
        if (keepOpen && route != null) {
            LOG.debug("Opening circuit (keepOpen set to true)");
            openCircuit(route);
        }
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public void setFailureThreshold(int failureThreshold) {
        this.failureThreshold = failureThreshold;
    }

    public long getFailureWindow() {
        return failureWindow;
    }

    public void setFailureWindow(long failureWindow) {
        this.failureWindow = failureWindow;
    }

    public long getHalfOpenAfter() {
        return halfOpenAfter;
    }

    /**
     * Sets the interval (in millis) for how often to check whether a currently open circuit breaker may work again.
     *
     * @throws IllegalArgumentException if the interval is 0 or less
     */
    public void setHalfOpenAfter(long halfOpenAfter) {
        checkHalfOpenAfter(halfOpenAfter);
        this.halfOpenAfter = halfOpenAfter;
    }

    private static void checkHalfOpenAfter(long halfOpenAfter) {
        if (halfOpenAfter <= 0) {
            throw new IllegalArgumentException("halfOpenAfter must be greater than 0, was: " + halfOpenAfter);
        }
    }

    public int getFailures() {
        return failures.get();
    }

    public int getSuccess() {
        return success.get();
    }

    public long getLastFailure() {
        return lastFailure;
    }

    public long getOpenedAt() {
        return openedAt;
    }

    public LoggingLevel getStateLoggingLevel() {
        return stateLoggingLevel;
    }

    public void setStateLoggingLevel(LoggingLevel stateLoggingLevel) {
        this.stateLoggingLevel = stateLoggingLevel;
        if (stateLogger != null) {
            stateLogger.setLevel(stateLoggingLevel);
        }
    }

    public void setStateLoggingLevel(String stateLoggingLevel) {
        setStateLoggingLevel(LoggingLevel.valueOf(stateLoggingLevel));
    }

}

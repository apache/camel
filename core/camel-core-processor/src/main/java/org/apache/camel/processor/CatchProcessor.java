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
package org.apache.camel.processor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeExtension;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.RollbackExchangeException;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.InterceptableProcessor;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.StepIdAware;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A processor which catches exceptions.
 */
public class CatchProcessor extends BaseDelegateProcessorSupport
        implements Traceable, IdAware, RouteIdAware, StepIdAware, InterceptableProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CatchProcessor.class);

    private final CamelContext camelContext;
    private String id;
    private String routeId;
    private String stepId;
    private final List<Class<? extends Throwable>> exceptions;
    private boolean extendedStatistics;
    // to capture how many different exceptions has been caught
    private ConcurrentMap<String, AtomicLong> exceptionMatches;
    private final Predicate onWhen;
    private transient long matches;

    public CatchProcessor(CamelContext camelContext, List<Class<? extends Throwable>> exceptions, Processor processor,
                          Predicate onWhen) {
        super(processor);
        this.camelContext = camelContext;
        this.exceptions = exceptions;
        this.onWhen = onWhen;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (onWhen != null) {
            onWhen.init(camelContext);
        }
        // only if JMX is enabled
        if (camelContext.getManagementStrategy() != null && camelContext.getManagementStrategy().getManagementAgent() != null) {
            this.extendedStatistics
                    = camelContext.getManagementStrategy().getManagementAgent().getStatisticsLevel().isExtended();
            this.exceptionMatches = new ConcurrentHashMap<>();
        } else {
            this.extendedStatistics = false;
        }
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public String getStepId() {
        return stepId;
    }

    @Override
    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    @Override
    public String getTraceLabel() {
        return "catch";
    }

    @Override
    public boolean canIntercept() {
        return false;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        final Exception e = exchange.getException();
        // If a previous catch clause handled the exception, exit (before matching so this clause is not counted)
        if (exchange.getProperty(ExchangePropertyKey.EXCEPTION_HANDLED) != null) {
            callback.done(true);
            return true;
        }
        Throwable caught;
        try {
            caught = catches(exchange, e);
        } catch (Exception onWhenException) {
            // the onWhen predicate failed, so the exchange fails with this exception (and the original as suppressed)
            if (e != null && e != onWhenException) {
                onWhenException.addSuppressed(e);
            }
            exchange.setException(onWhenException);
            callback.done(true);
            return true;
        }
        // If this clause does not match, exit
        if (caught == null) {
            callback.done(true);
            return true;
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("This CatchProcessor catches the exception: {} caused by: {}", caught.getClass().getName(),
                    e.getMessage());
        }

        // must remember some properties which we cannot use during doCatch processing
        final boolean stop = exchange.isRouteStop();
        exchange.setRouteStop(false);
        final boolean rollbackOnly = exchange.isRollbackOnly();
        exchange.setRollbackOnly(false);
        final boolean rollbackOnlyLast = exchange.isRollbackOnlyLast();
        exchange.setRollbackOnlyLast(false);

        // store the last to endpoint as the failure endpoint
        if (exchange.getProperty(ExchangePropertyKey.FAILURE_ENDPOINT) == null) {
            exchange.setProperty(ExchangePropertyKey.FAILURE_ENDPOINT, exchange.getProperty(ExchangePropertyKey.TO_ENDPOINT));
        }
        // give the rest of the pipeline another chance
        exchange.setProperty(ExchangePropertyKey.EXCEPTION_HANDLED, true);
        exchange.setProperty(ExchangePropertyKey.EXCEPTION_CAUGHT, e);
        exchange.setException(null);
        // and we should not be regarded as exhausted as we are in a try .. catch block
        exchange.getExchangeExtension().setRedeliveryExhausted(false);

        // a route called from the doTry (such as with direct) that failed unhandled has marked the exchange as failure
        // handled, and as not handled by the error handler, before the exception came back here. A failure in a route
        // that the doCatch block calls must be handled by that route's error handler as usual (which does not handle a
        // new failure of an exchange that is failure handled, and keeps a "not handled" that is already set), as when
        // the doTry failed in this route. So both marks are cleared when the doCatch takes over the exception
        final ExchangeExtension ext = exchange.getExchangeExtension();
        final boolean failureHandled = ext.isFailureHandled();
        // a primitive "set and true" snapshot, as the doFinally does: a "not handled" of the caught failure is not set
        // again, as the doCatch handled that failure (as when the doTry failed in this route). A true mark is kept only
        // to be safe, as a failure that an error handler handled has no exception left for a doCatch to catch
        final boolean errorHandlerHandled = ext.isErrorHandlerHandledSet() && ext.isErrorHandlerHandled();
        final boolean restore = failureHandled || ext.isErrorHandlerHandledSet();
        if (restore) {
            ext.setFailureHandled(false);
            ext.setErrorHandlerHandled(null);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("The exception is handled for the exception: {} caused by: {}",
                    e.getClass().getName(), e.getMessage());
        }

        // emit event that the failure is being handled
        EventHelper.notifyExchangeFailureHandling(exchange.getContext(), exchange, processor, false, null);

        boolean sync = processor.process(exchange, new AsyncCallback() {
            public void done(boolean doneSync) {
                // emit event that the failure was handled
                EventHelper.notifyExchangeFailureHandled(exchange.getContext(), exchange, processor, false, null);

                // always clear redelivery exhausted in a catch clause
                exchange.getExchangeExtension().setRedeliveryExhausted(false);

                if (restore) {
                    // set the marks of the earlier failure again, unless a route called from the block set the handled
                    // mark itself (such as its dead letter channel that handled its failure); failure handled is set
                    // again but never cleared, so a failure that the block handled itself stays marked
                    if (errorHandlerHandled && !ext.isErrorHandlerHandledSet()) {
                        ext.setErrorHandlerHandled(true);
                    }
                    if (failureHandled) {
                        ext.setFailureHandled(true);
                    }
                }

                if (rollbackOnly || rollbackOnlyLast || stop) {
                    exchange.setRouteStop(stop);
                    exchange.setRollbackOnly(rollbackOnly);
                    exchange.setRollbackOnlyLast(rollbackOnlyLast);
                    // special for rollback as we need to restore that a rollback was triggered
                    if (e instanceof RollbackExchangeException) {
                        exchange.setException(e);
                    }
                }

                if (!doneSync) {
                    // signal callback to continue routing async
                    ExchangeHelper.prepareOutToIn(exchange);
                }

                callback.done(doneSync);
            }
        });

        return sync;
    }

    /**
     * Returns with the exception that is caught by this processor.
     *
     * This method traverses exception causes, so sometimes the exception returned from this method might be one of
     * causes of the parameter passed.
     *
     * @param  exchange  the current exchange
     * @param  exception the thrown exception
     * @return           Throwable that this processor catches. <tt>null</tt> if nothing matches.
     */
    protected Throwable catches(Exchange exchange, Throwable exception) {
        // use the exception iterator to walk the caused by hierarchy
        for (final Throwable e : ObjectHelper.createExceptionIterable(exception)) {
            // see if we catch this type
            for (final Class<?> type : exceptions) {
                if (type.isInstance(e) && matchesWhen(exchange)) {
                    if (extendedStatistics) {
                        String fqn = exception.getClass().getName();
                        AtomicLong match = exceptionMatches.computeIfAbsent(fqn, k -> new AtomicLong());
                        match.incrementAndGet();
                    }
                    matches++;
                    return e;
                }
            }
        }

        // not found
        return null;
    }

    /**
     * Gets the total number of Exchanges that was caught (also matched the onWhen predicate).
     */
    public long getCaughtCount() {
        return matches;
    }

    /**
     * Gets the number of Exchanges that was caught by the given exception class name (also matched the onWhen
     * predicate). This requires to have extended statistics enabled on management statistics level.
     */
    public long getCaughtCount(String className) {
        AtomicLong cnt = exceptionMatches.get(className);
        return cnt != null ? cnt.get() : 0;
    }

    /**
     * Reset counters.
     */
    public void reset() {
        matches = 0;
        exceptionMatches.values().forEach(c -> c.set(0));
    }

    /**
     * Set of the caught exception fully qualified class names
     */
    public Set<String> getCaughtExceptionClassNames() {
        return exceptionMatches.keySet();
    }

    /**
     * Strategy method for matching the exception type with the current exchange.
     * <p/>
     * This default implementation will match as:
     * <ul>
     * <li>Always true if no when predicate on the exception type
     * <li>Otherwise the when predicate is matches against the current exchange
     * </ul>
     *
     * @param  exchange the current {@link org.apache.camel.Exchange}
     * @return          <tt>true</tt> if matched, <tt>false</tt> otherwise.
     */
    protected boolean matchesWhen(Exchange exchange) {
        if (onWhen == null) {
            // if no predicate then it's always a match
            return true;
        }
        return onWhen.matches(exchange);
    }
}

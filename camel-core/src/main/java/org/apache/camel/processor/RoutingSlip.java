/**
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

import java.util.Iterator;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducerCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Message;
import org.apache.camel.Producer;
import org.apache.camel.Traceable;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.EmptyProducerCache;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.processor.PipelineHelper.continueProcessing;
import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Implements a <a href="http://camel.apache.org/routing-slip.html">Routing Slip</a>
 * pattern where the list of actual endpoints to send a message exchange to are
 * dependent on the value of a message header.
 * <p/>
 * This implementation mirrors the logic from the {@link org.apache.camel.processor.Pipeline} in the async variation
 * as the failover load balancer is a specialized pipeline. So the trick is to keep doing the same as the
 * pipeline to ensure it works the same and the async routing engine is flawless.
 */
public class RoutingSlip extends ServiceSupport implements AsyncProcessor, Traceable, IdAware {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected String id;
    protected ProducerCache producerCache;
    protected int cacheSize;
    protected boolean ignoreInvalidEndpoints;
    protected String header;
    protected Expression expression;
    protected String uriDelimiter;
    protected final CamelContext camelContext;
    protected AsyncProcessor errorHandler;
    protected SendDynamicProcessor sendDynamicProcessor;

    /**
     * The iterator to be used for retrieving the next routing slip(s) to be used.
     */
    protected interface RoutingSlipIterator {

        /**
         * Are the more routing slip(s)?
         *
         * @param exchange the current exchange
         * @return <tt>true</tt> if more slips, <tt>false</tt> otherwise.
         */
        boolean hasNext(Exchange exchange);

        /**
         * Returns the next routing slip(s).
         *
         * @param exchange the current exchange
         * @return the slip(s).
         */
        Object next(Exchange exchange);

    }

    public RoutingSlip(CamelContext camelContext) {
        notNull(camelContext, "camelContext");
        this.camelContext = camelContext;
    }

    public RoutingSlip(CamelContext camelContext, Expression expression, String uriDelimiter) {
        notNull(camelContext, "camelContext");
        notNull(expression, "expression");
        
        this.camelContext = camelContext;
        this.expression = expression;
        this.uriDelimiter = uriDelimiter;
        this.header = null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Expression getExpression() {
        return expression;
    }

    public String getUriDelimiter() {
        return uriDelimiter;
    }

    public void setDelimiter(String delimiter) {
        this.uriDelimiter = delimiter;
    }
    
    public boolean isIgnoreInvalidEndpoints() {
        return ignoreInvalidEndpoints;
    }
    
    public void setIgnoreInvalidEndpoints(boolean ignoreInvalidEndpoints) {
        this.ignoreInvalidEndpoints = ignoreInvalidEndpoints;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }

    public AsyncProcessor getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(AsyncProcessor errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public String toString() {
        return "RoutingSlip[expression=" + expression + " uriDelimiter=" + uriDelimiter + "]";
    }

    public String getTraceLabel() {
        return "routingSlip[" + expression + "]";
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (!isStarted()) {
            exchange.setException(new IllegalStateException("RoutingSlip has not been started: " + this));
            callback.done(true);
            return true;
        }

        return doRoutingSlipWithExpression(exchange, this.expression, callback);
    }

    public boolean doRoutingSlip(Exchange exchange, Object routingSlip, AsyncCallback callback) {
        if (routingSlip instanceof Expression) {
            return doRoutingSlipWithExpression(exchange, (Expression) routingSlip, callback);
        } else {
            return doRoutingSlipWithExpression(exchange, ExpressionBuilder.constantExpression(routingSlip), callback);
        }
    }

    /**
     * Creates the route slip iterator to be used.
     *
     * @param exchange the exchange
     * @param expression the expression
     * @return the iterator, should never be <tt>null</tt>
     */
    protected RoutingSlipIterator createRoutingSlipIterator(final Exchange exchange, final Expression expression) throws Exception {
        Object slip = expression.evaluate(exchange, Object.class);
        if (exchange.getException() != null) {
            // force any exceptions occurred during evaluation to be thrown
            throw exchange.getException();
        }

        final Iterator<Object> delegate = ObjectHelper.createIterator(slip, uriDelimiter);

        return new RoutingSlipIterator() {
            public boolean hasNext(Exchange exchange) {
                return delegate.hasNext();
            }

            public Object next(Exchange exchange) {
                return delegate.next();
            }
        };
    }

    private boolean doRoutingSlipWithExpression(final Exchange exchange, final Expression expression, final AsyncCallback originalCallback) {
        Exchange current = exchange;
        RoutingSlipIterator iter;
        try {
            iter = createRoutingSlipIterator(exchange, expression);
        } catch (Exception e) {
            exchange.setException(e);
            originalCallback.done(true);
            return true;
        }

        // ensure the slip is empty when we start
        if (current.hasProperties()) {
            current.setProperty(Exchange.SLIP_ENDPOINT, null);
        }

        while (iter.hasNext(current)) {
            Endpoint endpoint;
            try {
                endpoint = resolveEndpoint(iter, exchange);
                // if no endpoint was resolved then try the next
                if (endpoint == null) {
                    continue;
                }
            } catch (Exception e) {
                // error resolving endpoint so we should break out
                current.setException(e);
                break;
            }

            //process and prepare the routing slip
            boolean sync = processExchange(endpoint, current, exchange, originalCallback, iter);
            current = prepareExchangeForRoutingSlip(current, endpoint);
            
            if (!sync) {
                log.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                // the remainder of the routing slip will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            log.trace("Processing exchangeId: {} is continued being processed synchronously", exchange.getExchangeId());

            // we ignore some kind of exceptions and allow us to continue
            if (isIgnoreInvalidEndpoints()) {
                FailedToCreateProducerException e = current.getException(FailedToCreateProducerException.class);
                if (e != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Endpoint uri is invalid: " + endpoint + ". This exception will be ignored.", e);
                    }
                    current.setException(null);
                }
            }

            // Decide whether to continue with the recipients or not; similar logic to the Pipeline
            // check for error if so we should break out
            if (!continueProcessing(current, "so breaking out of the routing slip", log)) {
                break;
            }
        }

        // logging nextExchange as it contains the exchange that might have altered the payload and since
        // we are logging the completion if will be confusing if we log the original instead
        // we could also consider logging the original and the nextExchange then we have *before* and *after* snapshots
        log.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), current);

        // copy results back to the original exchange
        ExchangeHelper.copyResults(exchange, current);

        // okay we are completely done with the routing slip
        // so we need to signal done on the original callback so it can continue
        originalCallback.done(true);
        return true;
    }

    protected Endpoint resolveEndpoint(RoutingSlipIterator iter, Exchange exchange) throws Exception {
        Object nextRecipient = iter.next(exchange);
        Endpoint endpoint = null;
        try {
            endpoint = ExchangeHelper.resolveEndpoint(exchange, nextRecipient);
        } catch (Exception e) {
            if (isIgnoreInvalidEndpoints()) {
                log.info("Endpoint uri is invalid: " + nextRecipient + ". This exception will be ignored.", e);
            } else {
                throw e;
            }
        }
        return endpoint;
    }

    protected Exchange prepareExchangeForRoutingSlip(Exchange current, Endpoint endpoint) {
        Exchange copy = new DefaultExchange(current);
        // we must use the same id as this is a snapshot strategy where Camel copies a snapshot
        // before processing the next step in the pipeline, so we have a snapshot of the exchange
        // just before. This snapshot is used if Camel should do redeliveries (re try) using
        // DeadLetterChannel. That is why it's important the id is the same, as it is the *same*
        // exchange being routed.
        copy.setExchangeId(current.getExchangeId());
        copyOutToIn(copy, current);

        // ensure stream caching is reset
        MessageHelper.resetStreamCache(copy.getIn());

        return copy;
    }

    protected AsyncProcessor createErrorHandler(RouteContext routeContext, Exchange exchange, AsyncProcessor processor, Endpoint endpoint) {
        AsyncProcessor answer = processor;

        boolean tryBlock = exchange.getProperty(Exchange.TRY_ROUTE_BLOCK, false, boolean.class);

        // do not wrap in error handler if we are inside a try block
        if (!tryBlock && routeContext != null && errorHandler != null) {
            // wrap the producer in error handler so we have fine grained error handling on
            // the output side instead of the input side
            // this is needed to support redelivery on that output alone and not doing redelivery
            // for the entire routingslip/dynamic-router block again which will start from scratch again
            answer = errorHandler;
        }

        return answer;
    }

    protected boolean processExchange(final Endpoint endpoint, final Exchange exchange, final Exchange original,
                                      final AsyncCallback originalCallback, final RoutingSlipIterator iter) {

        // this does the actual processing so log at trace level
        log.trace("Processing exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);

        // routing slip callback which are used when
        // - routing slip was routed asynchronously
        // - and we are completely done with the routing slip
        // so we need to signal done on the original callback so it can continue
        AsyncCallback callback = new AsyncCallback() {
            @Override
            public void done(boolean doneSync) {
                if (!doneSync) {
                    originalCallback.done(false);
                }
            }
        };
        boolean sync = producerCache.doInAsyncProducer(endpoint, exchange, null, callback, new AsyncProducerCallback() {
            public boolean doInAsyncProducer(Producer producer, AsyncProcessor asyncProducer, final Exchange exchange,
                                             ExchangePattern exchangePattern, final AsyncCallback callback) {

                // rework error handling to support fine grained error handling
                RouteContext routeContext = exchange.getUnitOfWork() != null ? exchange.getUnitOfWork().getRouteContext() : null;
                AsyncProcessor target = createErrorHandler(routeContext, exchange, asyncProducer, endpoint);

                // set property which endpoint we send to and the producer that can do it
                exchange.setProperty(Exchange.TO_ENDPOINT, endpoint.getEndpointUri());
                exchange.setProperty(Exchange.SLIP_ENDPOINT, endpoint.getEndpointUri());
                exchange.setProperty(Exchange.SLIP_PRODUCER, asyncProducer);

                boolean answer = target.process(exchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        // cleanup producer after usage
                        exchange.removeProperty(Exchange.SLIP_PRODUCER);

                        // we only have to handle async completion of the routing slip
                        if (doneSync) {
                            callback.done(true);
                            return;
                        }

                        try {
                            // continue processing the routing slip asynchronously
                            Exchange current = prepareExchangeForRoutingSlip(exchange, endpoint);

                            while (iter.hasNext(current)) {

                                // we ignore some kind of exceptions and allow us to continue
                                if (isIgnoreInvalidEndpoints()) {
                                    FailedToCreateProducerException e = current.getException(FailedToCreateProducerException.class);
                                    if (e != null) {
                                        if (log.isDebugEnabled()) {
                                            log.debug("Endpoint uri is invalid: " + endpoint + ". This exception will be ignored.", e);
                                        }
                                        current.setException(null);
                                    }
                                }

                                // Decide whether to continue with the recipients or not; similar logic to the Pipeline
                                // check for error if so we should break out
                                if (!continueProcessing(current, "so breaking out of the routing slip", log)) {
                                    break;
                                }

                                Endpoint endpoint;
                                try {
                                    endpoint = resolveEndpoint(iter, exchange);
                                    // if no endpoint was resolved then try the next
                                    if (endpoint == null) {
                                        continue;
                                    }
                                } catch (Exception e) {
                                    // error resolving endpoint so we should break out
                                    exchange.setException(e);
                                    break;
                                }

                                // prepare and process the routing slip
                                boolean sync = processExchange(endpoint, current, original, callback, iter);
                                current = prepareExchangeForRoutingSlip(current, endpoint);

                                if (!sync) {
                                    log.trace("Processing exchangeId: {} is continued being processed asynchronously", original.getExchangeId());
                                    return;
                                }
                            }

                            // logging nextExchange as it contains the exchange that might have altered the payload and since
                            // we are logging the completion if will be confusing if we log the original instead
                            // we could also consider logging the original and the nextExchange then we have *before* and *after* snapshots
                            log.trace("Processing complete for exchangeId: {} >>> {}", original.getExchangeId(), current);

                            // copy results back to the original exchange
                            ExchangeHelper.copyResults(original, current);
                        } catch (Throwable e) {
                            exchange.setException(e);
                        }

                        // okay we are completely done with the routing slip
                        // so we need to signal done on the original callback so it can continue
                        originalCallback.done(false);
                    }
                });

                return answer;
            }
        });

        return sync;
    }

    protected void doStart() throws Exception {
        if (producerCache == null) {
            if (cacheSize < 0) {
                producerCache = new EmptyProducerCache(this, camelContext);
                log.debug("RoutingSlip {} is not using ProducerCache", this);
            } else if (cacheSize == 0) {
                producerCache = new ProducerCache(this, camelContext);
                log.debug("RoutingSlip {} using ProducerCache with default cache size", this);
            } else {
                producerCache = new ProducerCache(this, camelContext, cacheSize);
                log.debug("RoutingSlip {} using ProducerCache with cacheSize={}", this, cacheSize);
            }
        }

        ServiceHelper.startServices(producerCache, errorHandler);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(producerCache, errorHandler);
    }

    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(producerCache, errorHandler);
    }

    public EndpointUtilizationStatistics getEndpointUtilizationStatistics() {
        return producerCache.getEndpointUtilizationStatistics();
    }

    /**
     * Returns the outbound message if available. Otherwise return the inbound message.
     */
    private Message getResultMessage(Exchange exchange) {
        if (exchange.hasOut()) {
            return exchange.getOut();
        } else {
            // if this endpoint had no out (like a mock endpoint) just take the in
            return exchange.getIn();
        }
    }

    /**
     * Copy the outbound data in 'source' to the inbound data in 'result'.
     */
    private void copyOutToIn(Exchange result, Exchange source) {
        result.setException(source.getException());
        result.setIn(getResultMessage(source));

        result.getProperties().clear();
        result.getProperties().putAll(source.getProperties());
    }

    /**
     * Creates the embedded processor to use when wrapping this routing slip in an error handler.
     */
    public AsyncProcessor newRoutingSlipProcessorForErrorHandler() {
        return new RoutingSlipProcessor();
    }

    /**
     * Embedded processor that routes to the routing slip that has been set via the
     * exchange property {@link Exchange#SLIP_PRODUCER}.
     */
    private final class RoutingSlipProcessor implements AsyncProcessor {

        @Override
        public void process(Exchange exchange) throws Exception {
            AsyncProcessorHelper.process(this, exchange);
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            AsyncProcessor producer = exchange.getProperty(Exchange.SLIP_PRODUCER, AsyncProcessor.class);
            return producer.process(exchange, callback);
        }

        @Override
        public String toString() {
            return "RoutingSlipProcessor";
        }
    }
}

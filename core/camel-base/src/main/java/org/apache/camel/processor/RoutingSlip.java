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

import java.util.Iterator;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.FailedToCreateProducerException;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Route;
import org.apache.camel.Traceable;
import org.apache.camel.impl.engine.DefaultProducerCache;
import org.apache.camel.impl.engine.EmptyProducerCache;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.ObjectHelper;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.service.ServiceHelper;
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
public class RoutingSlip extends AsyncProcessorSupport implements Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(RoutingSlip.class);

    protected String id;
    protected String routeId;
    protected ProducerCache producerCache;
    protected int cacheSize;
    protected boolean ignoreInvalidEndpoints;
    protected String header;
    protected Expression expression;
    protected String uriDelimiter;
    protected final CamelContext camelContext;
    protected AsyncProcessor errorHandler;

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
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "routingSlip[" + expression + "]";
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (!isStarted()) {
            exchange.setException(new IllegalStateException("RoutingSlip has not been started: " + this));
            callback.done(true);
            return true;
        }

        Expression exp = expression;
        Object slip = exchange.removeProperty(Exchange.EVALUATE_EXPRESSION_RESULT);
        if (slip != null) {
            if (slip instanceof Expression) {
                exp = (Expression) slip;
            } else {
                exp = ExpressionBuilder.constantExpression(slip);
            }
        }

        return doRoutingSlipWithExpression(exchange, exp, callback);
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

        final Iterator<?> delegate = ObjectHelper.createIterator(slip, uriDelimiter);

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

            boolean prototype = cacheSize < 0;
            Endpoint endpoint;
            try {
                Object recipient = iter.next(exchange);
                recipient = prepareRecipient(exchange, recipient);
                Endpoint existing = getExistingEndpoint(exchange, recipient);
                if (existing == null) {
                    endpoint = resolveEndpoint(exchange, recipient, prototype);
                } else {
                    endpoint = existing;
                    // we have an existing endpoint then its not a prototype scope
                    prototype = false;
                }
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
            boolean sync = processExchange(endpoint, current, exchange, originalCallback, iter, prototype);
            current = prepareExchangeForRoutingSlip(current, endpoint);
            
            if (!sync) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", exchange.getExchangeId());
                }
                // the remainder of the routing slip will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing exchangeId: {} is continued being processed synchronously", exchange.getExchangeId());
            }

            // we ignore some kind of exceptions and allow us to continue
            if (isIgnoreInvalidEndpoints()) {
                FailedToCreateProducerException e = current.getException(FailedToCreateProducerException.class);
                if (e != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Endpoint uri is invalid: " + endpoint + ". This exception will be ignored.", e);
                    }
                    current.setException(null);
                }
            }

            // Decide whether to continue with the recipients or not; similar logic to the Pipeline
            // check for error if so we should break out
            if (!continueProcessing(current, "so breaking out of the routing slip", LOG)) {
                break;
            }
        }

        // logging nextExchange as it contains the exchange that might have altered the payload and since
        // we are logging the completion if will be confusing if we log the original instead
        // we could also consider logging the original and the nextExchange then we have *before* and *after* snapshots
        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), current);
        }

        // copy results back to the original exchange
        ExchangeHelper.copyResults(exchange, current);

        // okay we are completely done with the routing slip
        // so we need to signal done on the original callback so it can continue
        originalCallback.done(true);
        return true;
    }

    protected static Object prepareRecipient(Exchange exchange, Object recipient) throws NoTypeConversionAvailableException {
        if (recipient instanceof Endpoint || recipient instanceof NormalizedEndpointUri) {
            return recipient;
        } else if (recipient instanceof String) {
            // trim strings as end users might have added spaces between separators
            recipient = ((String) recipient).trim();
        }
        if (recipient != null) {
            ExtendedCamelContext ecc = (ExtendedCamelContext) exchange.getContext();
            String uri;
            if (recipient instanceof String) {
                uri = (String) recipient;
            } else {
                // convert to a string type we can work with
                uri = ecc.getTypeConverter().mandatoryConvertTo(String.class, exchange, recipient);
            }
            // optimize and normalize endpoint
            return ecc.normalizeUri(uri);
        }
        return null;
    }

    protected static Endpoint getExistingEndpoint(Exchange exchange, Object recipient) {
        if (recipient instanceof Endpoint) {
            return (Endpoint) recipient;
        }
        if (recipient != null) {
            if (recipient instanceof NormalizedEndpointUri) {
                NormalizedEndpointUri nu = (NormalizedEndpointUri) recipient;
                ExtendedCamelContext ecc = (ExtendedCamelContext) exchange.getContext();
                return ecc.hasEndpoint(nu);
            } else {
                String uri = recipient.toString();
                return exchange.getContext().hasEndpoint(uri);
            }
        }
        return null;
    }

    protected Endpoint resolveEndpoint(Exchange exchange, Object recipient, boolean prototype) throws Exception {
        Endpoint endpoint = null;
        try {
            endpoint = prototype ? ExchangeHelper.resolvePrototypeEndpoint(exchange, recipient) : ExchangeHelper.resolveEndpoint(exchange, recipient);
        } catch (Exception e) {
            if (isIgnoreInvalidEndpoints()) {
                LOG.debug("Endpoint uri is invalid: " + recipient + ". This exception will be ignored.", e);
            } else {
                throw e;
            }
        }
        return endpoint;
    }

    protected Exchange prepareExchangeForRoutingSlip(Exchange current, Endpoint endpoint) {
        // we must use the same id as this is a snapshot strategy where Camel copies a snapshot
        // before processing the next step in the pipeline, so we have a snapshot of the exchange
        // just before. This snapshot is used if Camel should do redeliveries (re try) using
        // DeadLetterChannel. That is why it's important the id is the same, as it is the *same*
        // exchange being routed.
        Exchange copy = ExchangeHelper.createCopy(current, true);

        // prepare for next run
        ExchangeHelper.prepareOutToIn(copy);

        // ensure stream caching is reset
        MessageHelper.resetStreamCache(copy.getIn());

        return copy;
    }

    protected AsyncProcessor createErrorHandler(Route route, Exchange exchange, AsyncProcessor processor, Endpoint endpoint) {
        AsyncProcessor answer = processor;

        boolean tryBlock = exchange.getProperty(Exchange.TRY_ROUTE_BLOCK, false, boolean.class);

        // do not wrap in error handler if we are inside a try block
        if (!tryBlock && route != null && errorHandler != null) {
            // wrap the producer in error handler so we have fine grained error handling on
            // the output side instead of the input side
            // this is needed to support redelivery on that output alone and not doing redelivery
            // for the entire routingslip/dynamic-router block again which will start from scratch again
            answer = errorHandler;
        }

        return answer;
    }

    protected boolean processExchange(final Endpoint endpoint, final Exchange exchange, final Exchange original,
                                      final AsyncCallback originalCallback, final RoutingSlipIterator iter, final boolean prototype) {

        // this does the actual processing so log at trace level
        if (LOG.isTraceEnabled()) {
            LOG.trace("Processing exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
        }

        // routing slip callback which are used when
        // - routing slip was routed asynchronously
        // - and we are completely done with the routing slip
        // so we need to signal done on the original callback so it can continue
        AsyncCallback callback = doneSync -> {
            if (!doneSync) {
                originalCallback.done(false);
            }
        };
        return producerCache.doInAsyncProducer(endpoint, exchange, callback, (p, ex, cb) -> {

            // rework error handling to support fine grained error handling
            Route route = ExchangeHelper.getRoute(ex);
            AsyncProcessor target = createErrorHandler(route, ex, p, endpoint);

            // set property which endpoint we send to and the producer that can do it
            ex.setProperty(Exchange.TO_ENDPOINT, endpoint.getEndpointUri());
            ex.setProperty(Exchange.SLIP_ENDPOINT, endpoint.getEndpointUri());
            ex.setProperty(Exchange.SLIP_PRODUCER, p);

            return target.process(ex, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // cleanup producer after usage
                    ex.removeProperty(Exchange.SLIP_PRODUCER);

                    // we only have to handle async completion of the routing slip
                    if (doneSync) {
                        // and stop prototype endpoints
                        if (prototype) {
                            ServiceHelper.stopAndShutdownService(endpoint);
                        }
                        cb.done(true);
                        return;
                    }

                    try {
                        // continue processing the routing slip asynchronously
                        Exchange current = prepareExchangeForRoutingSlip(ex, endpoint);

                        while (iter.hasNext(current)) {

                            // we ignore some kind of exceptions and allow us to continue
                            if (isIgnoreInvalidEndpoints()) {
                                FailedToCreateProducerException e = current.getException(FailedToCreateProducerException.class);
                                if (e != null) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Endpoint uri is invalid: " + endpoint + ". This exception will be ignored.", e);
                                    }
                                    current.setException(null);
                                }
                            }

                            // Decide whether to continue with the recipients or not; similar logic to the Pipeline
                            // check for error if so we should break out
                            if (!continueProcessing(current, "so breaking out of the routing slip", LOG)) {
                                break;
                            }

                            Endpoint nextEndpoint;
                            boolean prototype = cacheSize < 0;
                            try {
                                Object recipient = iter.next(ex);
                                recipient = prepareRecipient(exchange, recipient);
                                Endpoint existing = getExistingEndpoint(exchange, recipient);
                                if (existing == null) {
                                    nextEndpoint = resolveEndpoint(exchange, recipient, prototype);
                                } else {
                                    nextEndpoint = existing;
                                    // we have an existing endpoint then its not a prototype scope
                                    prototype = false;
                                }
                                // if no endpoint was resolved then try the next
                                if (nextEndpoint == null) {
                                    continue;
                                }
                            } catch (Exception e) {
                                // error resolving endpoint so we should break out
                                current.setException(e);
                                break;
                            }

                            // prepare and process the routing slip
                            final boolean prototypeEndpoint = prototype;
                            AsyncCallback cbNext = doneNext -> {
                                // and stop prototype endpoints
                                if (prototypeEndpoint) {
                                    ServiceHelper.stopAndShutdownService(nextEndpoint);
                                }
                                cb.done(doneNext);
                            };
                            boolean sync = processExchange(nextEndpoint, current, original, cbNext, iter, prototype);
                            current = prepareExchangeForRoutingSlip(current, nextEndpoint);

                            if (!sync) {
                                if (LOG.isTraceEnabled()) {
                                    LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", original.getExchangeId());
                                }
                                return;
                            }
                        }

                        // logging nextExchange as it contains the exchange that might have altered the payload and since
                        // we are logging the completion if will be confusing if we log the original instead
                        // we could also consider logging the original and the nextExchange then we have *before* and *after* snapshots
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Processing complete for exchangeId: {} >>> {}", original.getExchangeId(), current);
                        }

                        // copy results back to the original exchange
                        ExchangeHelper.copyResults(original, current);
                    } catch (Throwable e) {
                        ex.setException(e);
                    }

                    // okay we are completely done with the routing slip
                    // so we need to signal done on the original callback so it can continue
                    cb.done(false);
                }
            });
        });
    }

    @Override
    protected void doStart() throws Exception {
        if (producerCache == null) {
            if (cacheSize < 0) {
                producerCache = new EmptyProducerCache(this, camelContext);
                LOG.debug("RoutingSlip {} is not using ProducerCache", this);
            } else {
                producerCache = new DefaultProducerCache(this, camelContext, cacheSize);
                LOG.debug("RoutingSlip {} using ProducerCache with cacheSize={}", this, cacheSize);
            }
        }

        ServiceHelper.startService(producerCache, errorHandler);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache, errorHandler);
    }

    @Override
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
        return exchange.getMessage();
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
    private final class RoutingSlipProcessor extends AsyncProcessorSupport {

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

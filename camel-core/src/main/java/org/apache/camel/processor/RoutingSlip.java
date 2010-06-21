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
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
public class RoutingSlip extends ServiceSupport implements AsyncProcessor, Traceable {
    private static final transient Log LOG = LogFactory.getLog(RoutingSlip.class);
    private ProducerCache producerCache;
    private boolean ignoreInvalidEndpoints;
    private String header;
    private Expression expression;    
    private String uriDelimiter;
    private final CamelContext camelContext;
    
    public RoutingSlip(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    /**
     * Will be removed in Camel 2.5
     */
    @Deprecated
    public RoutingSlip(CamelContext camelContext, String header) {
        this(camelContext, header, RoutingSlipDefinition.DEFAULT_DELIMITER);
    }

    /**
     * Will be removed in Camel 2.5
     */
    @Deprecated
    public RoutingSlip(CamelContext camelContext, String header, String uriDelimiter) {
        notNull(camelContext, "camelContext");
        notNull(header, "header");
        notNull(uriDelimiter, "uriDelimiter");

        this.camelContext = camelContext;
        this.header = header;
        expression = ExpressionBuilder.headerExpression(header);
        this.uriDelimiter = uriDelimiter;
    }
    
    public RoutingSlip(CamelContext camelContext, Expression expression, String uriDelimiter) {
        notNull(camelContext, "camelContext");
        notNull(expression, "expression");
        
        this.camelContext = camelContext;
        this.expression = expression;
        this.uriDelimiter = uriDelimiter;
        this.header = null;
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
            throw new IllegalStateException("RoutingSlip has not been started: " + this);
        }

        Object routingSlip = expression.evaluate(exchange, Object.class);
        return doRoutingSlip(exchange, routingSlip, callback);
    }

    public boolean doRoutingSlip(Exchange exchange, Object routingSlip) {
        return doRoutingSlip(exchange, routingSlip, new AsyncCallback() {
            public void done(boolean doneSync) {
                // noop
            }
        });
    }

    public boolean doRoutingSlip(Exchange exchange, Object routingSlip, AsyncCallback callback) {
        Iterator<Object> iter = ObjectHelper.createIterator(routingSlip, uriDelimiter);
        Exchange current = exchange;

        while (iter.hasNext()) {
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
                return true;
            }

            // prepare and process the routing slip
            Exchange copy = prepareExchangeForRoutingSlip(current);
            boolean sync = processExchange(endpoint, copy, exchange, callback, iter);
            current = copy;

            if (!sync) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing exchangeId: " + exchange.getExchangeId() + " is continued being processed asynchronously");
                }
                // the remainder of the routing slip will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing exchangeId: " + exchange.getExchangeId() + " is continued being processed synchronously");
            }

            // we ignore some kind of exceptions and allow us to continue
            if (isIgnoreInvalidEndpoints()) {
                FailedToCreateProducerException e = current.getException(FailedToCreateProducerException.class);
                if (e != null) {
                    LOG.info("Endpoint uri is invalid: " + endpoint + ". This exception will be ignored.", e);
                    current.setException(null);
                }
            }

            // Decide whether to continue with the recipients or not; similar logic to the Pipeline
            boolean exceptionHandled = hasExceptionBeenHandledByErrorHandler(current);
            if (current.isFailed() || current.isRollbackOnly() || exceptionHandled) {
                // The Exchange.ERRORHANDLED_HANDLED property is only set if satisfactory handling was done
                // by the error handler. It's still an exception, the exchange still failed.
                if (LOG.isDebugEnabled()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Message exchange has failed so breaking out of the routing slip: ").append(current);
                    if (current.isRollbackOnly()) {
                        sb.append(" Marked as rollback only.");
                    }
                    if (current.getException() != null) {
                        sb.append(" Exception: ").append(current.getException());
                    }
                    if (current.hasOut() && current.getOut().isFault()) {
                        sb.append(" Fault: ").append(current.getOut());
                    }
                    if (exceptionHandled) {
                        sb.append(" Handled by the error handler.");
                    }
                    LOG.debug(sb.toString());
                }
                break;
            }
        }

        if (LOG.isTraceEnabled()) {
            // logging nextExchange as it contains the exchange that might have altered the payload and since
            // we are logging the completion if will be confusing if we log the original instead
            // we could also consider logging the original and the nextExchange then we have *before* and *after* snapshots
            LOG.trace("Processing complete for exchangeId: " + exchange.getExchangeId() + " >>> " + current);
        }

        // copy results back to the original exchange
        ExchangeHelper.copyResults(exchange, current);

        callback.done(true);
        return true;
    }

    protected Endpoint resolveEndpoint(Iterator<Object> iter, Exchange exchange) throws Exception {
        Object nextRecipient = iter.next();
        Endpoint endpoint = null;
        try {
            endpoint = ExchangeHelper.resolveEndpoint(exchange, nextRecipient);
        } catch (Exception e) {
            if (isIgnoreInvalidEndpoints()) {
                LOG.info("Endpoint uri is invalid: " + nextRecipient + ". This exception will be ignored.", e);
            } else {
                throw e;
            }
        }
        return endpoint;
    }

    protected Exchange prepareExchangeForRoutingSlip(Exchange current) {
        Exchange copy = new DefaultExchange(current);
        // we must use the same id as this is a snapshot strategy where Camel copies a snapshot
        // before processing the next step in the pipeline, so we have a snapshot of the exchange
        // just before. This snapshot is used if Camel should do redeliveries (re try) using
        // DeadLetterChannel. That is why it's important the id is the same, as it is the *same*
        // exchange being routed.
        copy.setExchangeId(current.getExchangeId());
        updateRoutingSlipHeader(current);
        copyOutToIn(copy, current);
        return copy;
    }

    protected boolean processExchange(final Endpoint endpoint, final Exchange exchange, final Exchange original,
                                      final AsyncCallback callback, final Iterator<Object> iter) {

        if (LOG.isTraceEnabled()) {
            // this does the actual processing so log at trace level
            LOG.trace("Processing exchangeId: " + exchange.getExchangeId() + " >>> " + exchange);
        }

        boolean sync = producerCache.doInAsyncProducer(endpoint, exchange, null, callback, new AsyncProducerCallback() {
            public boolean doInAsyncProducer(Producer producer, AsyncProcessor asyncProducer, final Exchange exchange,
                                             ExchangePattern exchangePattern, final AsyncCallback callback) {
                // set property which endpoint we send to
                exchange.setProperty(Exchange.TO_ENDPOINT, producer.getEndpoint().getEndpointUri());
                boolean sync = asyncProducer.process(exchange, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        // we only have to handle async completion of the pipeline
                        if (doneSync) {
                            return;
                        }

                        // continue processing the routing slip asynchronously
                        Exchange current = exchange;

                        while (iter.hasNext()) {

                            // we ignore some kind of exceptions and allow us to continue
                            if (isIgnoreInvalidEndpoints()) {
                                FailedToCreateProducerException e = current.getException(FailedToCreateProducerException.class);
                                if (e != null) {
                                    LOG.info("Endpoint uri is invalid: " + endpoint + ". This exception will be ignored.", e);
                                    current.setException(null);
                                }
                            }

                            // Decide whether to continue with the recipients or not; similar logic to the Pipeline
                            boolean exceptionHandled = hasExceptionBeenHandledByErrorHandler(current);
                            if (current.isFailed() || current.isRollbackOnly() || exceptionHandled) {
                                // The Exchange.ERRORHANDLED_HANDLED property is only set if satisfactory handling was done
                                // by the error handler. It's still an exception, the exchange still failed.
                                if (LOG.isDebugEnabled()) {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("Message exchange has failed so breaking out of the routing slip: ").append(current);
                                    if (current.isRollbackOnly()) {
                                        sb.append(" Marked as rollback only.");
                                    }
                                    if (current.getException() != null) {
                                        sb.append(" Exception: ").append(current.getException());
                                    }
                                    if (current.hasOut() && current.getOut().isFault()) {
                                        sb.append(" Fault: ").append(current.getOut());
                                    }
                                    if (exceptionHandled) {
                                        sb.append(" Handled by the error handler.");
                                    }
                                    LOG.debug(sb.toString());
                                }
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
                            Exchange copy = prepareExchangeForRoutingSlip(current);
                            boolean sync = processExchange(endpoint, copy, original, callback, iter);
                            current = copy;

                            if (!sync) {
                                if (LOG.isTraceEnabled()) {
                                    LOG.trace("Processing exchangeId: " + original.getExchangeId() + " is continued being processed asynchronously");
                                }
                                return;
                            }
                        }

                        if (LOG.isTraceEnabled()) {
                            // logging nextExchange as it contains the exchange that might have altered the payload and since
                            // we are logging the completion if will be confusing if we log the original instead
                            // we could also consider logging the original and the nextExchange then we have *before* and *after* snapshots
                            LOG.trace("Processing complete for exchangeId: " + original.getExchangeId() + " >>> " + current);
                        }

                        // copy results back to the original exchange
                        ExchangeHelper.copyResults(original, current);
                        callback.done(false);
                    }
                });

                return sync;
            }
        });

        return sync;
    }

    private static boolean hasExceptionBeenHandledByErrorHandler(Exchange nextExchange) {
        return Boolean.TRUE.equals(nextExchange.getProperty(Exchange.ERRORHANDLER_HANDLED));
    }

    protected void doStart() throws Exception {
        if (producerCache == null) {
            producerCache = new ProducerCache(this, camelContext);
            // add it as a service so we can manage it
            camelContext.addService(producerCache);
        }
        ServiceHelper.startService(producerCache);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);
    }

    private void updateRoutingSlipHeader(Exchange current) {
        // only update the header value which used as the routing slip
        if (header != null) {
            Message message = getResultMessage(current);
            String oldSlip = message.getHeader(header, String.class);
            if (oldSlip != null) {
                int delimiterIndex = oldSlip.indexOf(uriDelimiter);
                String newSlip = delimiterIndex > 0 ? oldSlip.substring(delimiterIndex + 1) : "";
                message.setHeader(header, newSlip);
            }
        }
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

        if (source.hasOut() && source.getOut().isFault()) {
            result.getOut().copyFrom(source.getOut());
        }

        result.setIn(getResultMessage(source));

        result.getProperties().clear();
        result.getProperties().putAll(source.getProperties());
    }
}

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
package org.apache.camel.impl;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This is an endpoint when sending to it, is intercepted and is routed in a detour
 *
 * @version $Revision$
 */
public class InterceptSendToEndpoint implements Endpoint {

    private static final transient Log LOG = LogFactory.getLog(InterceptSendToEndpoint.class);

    private final Endpoint delegate;
    private Producer producer;
    private Processor detour;
    private boolean skip;

    /**
     * Intercepts sending to the given endpoint
     *
     * @param destination  the original endpoint
     * @param skip <tt>true</tt> to skip sending after the detour to the original endpoint
     */
    public InterceptSendToEndpoint(final Endpoint destination, boolean skip) {
        this.delegate = destination;
        this.skip = skip;
    }

    public void setDetour(Processor detour) {
        this.detour = detour;
    }

    public Endpoint getDelegate() {
        return delegate;
    }

    public String getEndpointUri() {
        return delegate.getEndpointUri();
    }

    public String getEndpointKey() {
        return delegate.getEndpointKey();
    }

    public Exchange createExchange() {
        return delegate.createExchange();
    }

    public Exchange createExchange(ExchangePattern pattern) {
        return delegate.createExchange(pattern);
    }

    public Exchange createExchange(Exchange exchange) {
        return delegate.createExchange(exchange);
    }

    public CamelContext getCamelContext() {
        return delegate.getCamelContext();
    }

    public Producer createProducer() throws Exception {
        producer = delegate.createProducer();
        return new Producer() {

            public Endpoint getEndpoint() {
                return producer.getEndpoint();
            }

            public Exchange createExchange() {
                return producer.createExchange();
            }

            public Exchange createExchange(ExchangePattern pattern) {
                return producer.createExchange(pattern);
            }

            public Exchange createExchange(Exchange exchange) {
                return producer.createExchange(exchange);
            }

            public void process(Exchange exchange) throws Exception {
                // process the detour so we do the detour routing
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending to endpoint: " + getEndpointUri() + " is intercepted and detoured to: " + detour + " for exchange: " + exchange);
                }
                // add header with the real endpoint uri
                exchange.getIn().setHeader(Exchange.INTERCEPTED_ENDPOINT, delegate.getEndpointUri());

                try {
                    detour.process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                }

                // check for error if so we should break out
                boolean exceptionHandled = hasExceptionBeenHandledByErrorHandler(exchange);
                if (exchange.isFailed() || exchange.isRollbackOnly() || exceptionHandled) {
                    // The Exchange.ERRORHANDLED_HANDLED property is only set if satisfactory handling was done
                    // by the error handler. It's still an exception, the exchange still failed.
                    if (LOG.isDebugEnabled()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Message exchange has failed so skip sending to original intended destination: ").append(getEndpointUri());
                        sb.append(" for Exchange: ").append(exchange);
                        if (exchange.isRollbackOnly()) {
                            sb.append(" Marked as rollback only.");
                        }
                        if (exchange.getException() != null) {
                            sb.append(" Exception: ").append(exchange.getException());
                        }
                        if (exchange.hasOut() && exchange.getOut().isFault()) {
                            sb.append(" Fault: ").append(exchange.getOut());
                        }
                        if (exceptionHandled) {
                            sb.append(" Handled by the error handler.");
                        }
                        LOG.debug(sb.toString());
                    }
                    return;
                }

                if (!skip) {
                    if (exchange.hasOut()) {
                        // replace OUT with IN as detour changed something
                        exchange.setIn(exchange.getOut());
                        exchange.setOut(null);
                    }

                    // route to original destination
                    producer.process(exchange);
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Stop() means skip sending exchange to original intended destination: " + getEndpointUri() + " for exchange: " + exchange);
                    }
                }
            }

            public boolean isSingleton() {
                return producer.isSingleton();
            }

            public void start() throws Exception {
                ServiceHelper.startService(detour);
            }

            public void stop() throws Exception {
                ServiceHelper.stopService(detour);
            }
        };
    }

    private static boolean hasExceptionBeenHandledByErrorHandler(Exchange nextExchange) {
        return Boolean.TRUE.equals(nextExchange.getProperty(Exchange.ERRORHANDLER_HANDLED));
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return delegate.createConsumer(processor);
    }

    public PollingConsumer createPollingConsumer() throws Exception {
        return delegate.createPollingConsumer();
    }

    public void configureProperties(Map<String, Object> options) {
        delegate.configureProperties(options);
    }

    public void setCamelContext(CamelContext context) {
        delegate.setCamelContext(context);
    }

    public boolean isLenientProperties() {
        return delegate.isLenientProperties();
    }

    public boolean isSingleton() {
        return delegate.isSingleton();
    }

    public void start() throws Exception {
        delegate.start();
    }

    public void stop() throws Exception {
        delegate.stop();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}

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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducerCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Producer;
import org.apache.camel.impl.EmptyProducerCache;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor for forwarding exchanges to a dynamic endpoint destination.
 *
 * @see org.apache.camel.processor.SendProcessor
 */
public class SendDynamicProcessor extends ServiceSupport implements AsyncProcessor, IdAware, CamelContextAware {
    protected static final Logger LOG = LoggerFactory.getLogger(SendDynamicProcessor.class);
    protected CamelContext camelContext;
    protected final String uri;
    protected final Expression expression;
    protected ExchangePattern pattern;
    protected ProducerCache producerCache;
    protected String id;
    protected boolean ignoreInvalidEndpoint;
    protected int cacheSize;

    public SendDynamicProcessor(Expression expression) {
        this.uri = null;
        this.expression = expression;
    }

    public SendDynamicProcessor(String uri, Expression expression) {
        this.uri = uri;
        this.expression = expression;
    }

    @Override
    public String toString() {
        return "sendTo(" + getExpression() + ")";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void process(final Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        if (!isStarted()) {
            exchange.setException(new IllegalStateException("SendProcessor has not been started: " + this));
            callback.done(true);
            return true;
        }

        // we should preserve existing MEP so remember old MEP
        // if you want to permanently to change the MEP then use .setExchangePattern in the DSL
        final ExchangePattern existingPattern = exchange.getPattern();

        // which endpoint to send to
        final Endpoint endpoint;
        final ExchangePattern destinationExchangePattern;

        // use dynamic endpoint so calculate the endpoint to use
        Object recipient = null;
        try {
            recipient = expression.evaluate(exchange, Object.class);
            endpoint = resolveEndpoint(exchange, recipient);
            if (endpoint == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Send dynamic evaluated as null so cannot send to any endpoint");
                }
                // no endpoint to send to, so ignore
                callback.done(true);
                return true;
            }
            destinationExchangePattern = EndpointHelper.resolveExchangePatternFromUrl(endpoint.getEndpointUri());
        } catch (Throwable e) {
            if (isIgnoreInvalidEndpoint()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Endpoint uri is invalid: " + recipient + ". This exception will be ignored.", e);
                }
            } else {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }

        // send the exchange to the destination using the producer cache
        return producerCache.doInAsyncProducer(endpoint, exchange, pattern, callback, new AsyncProducerCallback() {
            public boolean doInAsyncProducer(Producer producer, AsyncProcessor asyncProducer, final Exchange exchange,
                                             ExchangePattern pattern, final AsyncCallback callback) {
                final Exchange target = configureExchange(exchange, pattern, destinationExchangePattern, endpoint);
                LOG.debug(">>>> {} {}", endpoint, exchange);
                return asyncProducer.process(target, new AsyncCallback() {
                    public void done(boolean doneSync) {
                        // restore previous MEP
                        target.setPattern(existingPattern);
                        // signal we are done
                        callback.done(doneSync);
                    }
                });
            }
        });
    }

    protected static Endpoint resolveEndpoint(Exchange exchange, Object recipient) throws NoTypeConversionAvailableException {
        // trim strings as end users might have added spaces between separators
        if (recipient instanceof String) {
            recipient = ((String) recipient).trim();
        } else if (recipient instanceof Endpoint) {
            return (Endpoint) recipient;
        } else if (recipient != null) {
            // convert to a string type we can work with
            recipient = exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, recipient);
        }

        if (recipient != null) {
            return ExchangeHelper.resolveEndpoint(exchange, recipient);
        } else {
            return null;
        }
    }

    protected Exchange configureExchange(Exchange exchange, ExchangePattern pattern, ExchangePattern destinationExchangePattern, Endpoint endpoint) {
        // destination exchange pattern overrides pattern
        if (destinationExchangePattern != null) {
            exchange.setPattern(destinationExchangePattern);
        } else if (pattern != null) {
            exchange.setPattern(pattern);
        }
        // set property which endpoint we send to
        exchange.setProperty(Exchange.TO_ENDPOINT, endpoint.getEndpointUri());
        return exchange;
    }

    protected void doStart() throws Exception {
        if (producerCache == null) {
            if (cacheSize < 0) {
                producerCache = new EmptyProducerCache(this, camelContext);
                LOG.debug("DynamicSendTo {} is not using ProducerCache", this);
            } else if (cacheSize == 0) {
                producerCache = new ProducerCache(this, camelContext);
                LOG.debug("DynamicSendTo {} using ProducerCache with default cache size", this);
            } else {
                producerCache = new ProducerCache(this, camelContext, cacheSize);
                LOG.debug("DynamicSendTo {} using ProducerCache with cacheSize={}", this, cacheSize);
            }
        }
        ServiceHelper.startService(producerCache);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(producerCache);
    }

    public EndpointUtilizationStatistics getEndpointUtilizationStatistics() {
        return producerCache.getEndpointUtilizationStatistics();
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getUri() {
        return uri;
    }

    public Expression getExpression() {
        return expression;
    }

    public ExchangePattern getPattern() {
        return pattern;
    }

    public void setPattern(ExchangePattern pattern) {
        this.pattern = pattern;
    }

    public boolean isIgnoreInvalidEndpoint() {
        return ignoreInvalidEndpoint;
    }

    public void setIgnoreInvalidEndpoint(boolean ignoreInvalidEndpoint) {
        this.ignoreInvalidEndpoint = ignoreInvalidEndpoint;
    }

    public int getCacheSize() {
        return cacheSize;
    }

    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }
}

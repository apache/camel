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
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.ProducerCache;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.support.AsyncProcessorHelper;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ServiceHelper;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.URISupport;

/**
 * Processor for forwarding exchanges to a dynamic endpoint destination.
 *
 * @see org.apache.camel.processor.SendProcessor
 */
public class SendDynamicProcessor extends ServiceSupport implements AsyncProcessor, IdAware, CamelContextAware {

    protected SendDynamicAware dynamicAware;
    protected CamelContext camelContext;
    protected final String uri;
    protected final Expression expression;
    protected ExchangePattern pattern;
    protected ProducerCache producerCache;
    protected String id;
    protected boolean ignoreInvalidEndpoint;
    protected int cacheSize;
    protected boolean allowOptimisedComponents = true;

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
        Processor preAwareProcessor = null;
        Processor postAwareProcessor = null;
        String staticUri = null;
        try {
            recipient = expression.evaluate(exchange, Object.class);
            if (dynamicAware != null) {
                // if its the same scheme as the pre-resolved dynamic aware then we can optimise to use it
                String uri = resolveUri(exchange, recipient);
                String scheme = resolveScheme(exchange, uri);
                if (dynamicAware.getScheme().equals(scheme)) {
                    SendDynamicAware.DynamicAwareEntry entry = dynamicAware.prepare(exchange, uri);
                    if (entry != null) {
                        staticUri = dynamicAware.resolveStaticUri(exchange, entry);
                        preAwareProcessor = dynamicAware.createPreProcessor(exchange, entry);
                        postAwareProcessor = dynamicAware.createPostProcessor(exchange, entry);
                        if (staticUri != null) {
                            if (log.isDebugEnabled()) {
                                log.debug("Optimising toD via SendDynamicAware component: {} to use static uri: {}", scheme, URISupport.sanitizeUri(staticUri));
                            }
                        }
                    }
                }
            }
            if (staticUri != null) {
                endpoint = resolveEndpoint(exchange, staticUri);
            } else {
                endpoint = resolveEndpoint(exchange, recipient);
            }
            if (endpoint == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Send dynamic evaluated as null so cannot send to any endpoint");
                }
                // no endpoint to send to, so ignore
                callback.done(true);
                return true;
            }
            destinationExchangePattern = EndpointHelper.resolveExchangePatternFromUrl(endpoint.getEndpointUri());
        } catch (Throwable e) {
            if (isIgnoreInvalidEndpoint()) {
                if (log.isDebugEnabled()) {
                    log.debug("Endpoint uri is invalid: " + recipient + ". This exception will be ignored.", e);
                }
            } else {
                exchange.setException(e);
            }
            callback.done(true);
            return true;
        }

        // send the exchange to the destination using the producer cache
        final Processor preProcessor = preAwareProcessor;
        final Processor postProcessor = postAwareProcessor;
        // destination exchange pattern overrides pattern
        final ExchangePattern pattern = destinationExchangePattern != null ? destinationExchangePattern : this.pattern;
        return producerCache.doInAsyncProducer(endpoint, exchange, callback, (p, e, c) -> {
            final Exchange target = configureExchange(e, pattern, endpoint);
            try {
                if (preProcessor != null) {
                    preProcessor.process(target);
                }
            } catch (Throwable t) {
                e.setException(t);
                // restore previous MEP
                target.setPattern(existingPattern);
                // we failed
                c.done(true);
            }

            log.debug(">>>> {} {}", endpoint, e);
            return p.process(target, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // restore previous MEP
                    target.setPattern(existingPattern);
                    try {
                        if (postProcessor != null) {
                            postProcessor.process(target);
                        }
                    } catch (Throwable e) {
                        target.setException(e);
                    }
                    // signal we are done
                    c.done(doneSync);
                }
            });
        });
    }

    protected static String resolveUri(Exchange exchange, Object recipient) throws NoTypeConversionAvailableException {
        if (recipient == null) {
            return null;
        }

        String uri;
        // trim strings as end users might have added spaces between separators
        if (recipient instanceof String) {
            uri = ((String) recipient).trim();
        } else if (recipient instanceof Endpoint) {
            uri = ((Endpoint) recipient).getEndpointKey();
        } else {
            // convert to a string type we can work with
            uri = exchange.getContext().getTypeConverter().mandatoryConvertTo(String.class, exchange, recipient);
        }

        // in case path has property placeholders then try to let property component resolve those
        try {
            uri = exchange.getContext().resolvePropertyPlaceholders(uri);
        } catch (Exception e) {
            throw new ResolveEndpointFailedException(uri, e);
        }

        return uri;
    }

    protected static String resolveScheme(Exchange exchange, String uri) {
        return ExchangeHelper.resolveScheme(uri);
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

    protected Exchange configureExchange(Exchange exchange, ExchangePattern pattern, Endpoint endpoint) {
        if (pattern != null) {
            exchange.setPattern(pattern);
        }
        // set property which endpoint we send to
        exchange.setProperty(Exchange.TO_ENDPOINT, endpoint.getEndpointUri());
        return exchange;
    }

    protected void doStart() throws Exception {
        if (producerCache == null) {
            producerCache = new ProducerCache(this, camelContext, cacheSize);
            log.debug("DynamicSendTo {} using ProducerCache with cacheSize={}", this, producerCache.getCapacity());
        }

        if (isAllowOptimisedComponents() && uri != null) {
            try {
                // in case path has property placeholders then try to let property component resolve those
                String u = camelContext.resolvePropertyPlaceholders(uri);
                // find out which component it is
                String scheme = ExchangeHelper.resolveScheme(u);
                if (scheme != null) {
                    // find out if the component can be optimised for send-dynamic
                    SendDynamicAwareResolver resolver = new SendDynamicAwareResolver();
                    dynamicAware = resolver.resolve(camelContext, scheme);
                    if (dynamicAware != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("Detected SendDynamicAware component: {} optimising toD: {}", scheme, URISupport.sanitizeUri(uri));
                        }
                    }
                }
            } catch (Throwable e) {
                // ignore
                if (log.isDebugEnabled()) {
                    log.debug("Error creating optimised SendDynamicAwareResolver for uri: " + URISupport.sanitizeUri(uri)
                        + " due to " + e.getMessage() + ". This exception is ignored", e);
                }
            }
        }

        ServiceHelper.startService(producerCache);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);
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

    public SendDynamicAware getDynamicAware() {
        return dynamicAware;
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

    public boolean isAllowOptimisedComponents() {
        return allowOptimisedComponents;
    }

    public void setAllowOptimisedComponents(boolean allowOptimisedComponents) {
        this.allowOptimisedComponents = allowOptimisedComponents;
    }
}

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

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.engine.DefaultProducerCache;
import org.apache.camel.impl.engine.EmptyProducerCache;
import org.apache.camel.spi.EndpointUtilizationStatistics;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor for forwarding exchanges to a dynamic endpoint destination.
 *
 * @see org.apache.camel.processor.SendProcessor
 */
public class SendDynamicProcessor extends AsyncProcessorSupport implements IdAware, RouteIdAware, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(SendDynamicProcessor.class);

    protected SendDynamicAware dynamicAware;
    protected CamelContext camelContext;
    protected final String uri;
    protected final Expression expression;
    protected ExchangePattern pattern;
    protected ProducerCache producerCache;
    protected String id;
    protected String routeId;
    protected boolean ignoreInvalidEndpoint;
    protected int cacheSize;
    protected boolean allowOptimisedComponents = true;

    public SendDynamicProcessor(String uri, Expression expression) {
        this.uri = uri;
        this.expression = expression;
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
    protected void doInit() throws Exception {
        expression.init(camelContext);
    }

    @Override
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
        boolean prototype = cacheSize < 0;
        try {
            recipient = expression.evaluate(exchange, Object.class);
            if (dynamicAware != null) {
                // if its the same scheme as the pre-resolved dynamic aware then we can optimise to use it
                String originalUri = uri;
                String uri = resolveUri(exchange, recipient);
                String scheme = resolveScheme(exchange, uri);
                if (dynamicAware.getScheme().equals(scheme)) {
                    SendDynamicAware.DynamicAwareEntry entry = dynamicAware.prepare(exchange, uri, originalUri);
                    if (entry != null) {
                        staticUri = dynamicAware.resolveStaticUri(exchange, entry);
                        preAwareProcessor = dynamicAware.createPreProcessor(exchange, entry);
                        postAwareProcessor = dynamicAware.createPostProcessor(exchange, entry);
                        if (staticUri != null) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Optimising toD via SendDynamicAware component: {} to use static uri: {}", scheme, URISupport.sanitizeUri(staticUri));
                            }
                        }
                    }
                }
            }
            Object targetRecipient = staticUri != null ? staticUri : recipient;
            targetRecipient = prepareRecipient(exchange, targetRecipient);
            if (targetRecipient == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Send dynamic evaluated as null so cannot send to any endpoint");
                }
                // no endpoint to send to, so ignore
                callback.done(true);
                return true;
            }
            Endpoint existing = getExistingEndpoint(exchange, targetRecipient);
            if (existing == null) {
                endpoint = resolveEndpoint(exchange, targetRecipient, prototype);
            } else {
                endpoint = existing;
                // we have an existing endpoint then its not a prototype scope
                prototype = false;
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
        final Processor preProcessor = preAwareProcessor;
        final Processor postProcessor = postAwareProcessor;
        // destination exchange pattern overrides pattern
        final ExchangePattern pattern = destinationExchangePattern != null ? destinationExchangePattern : this.pattern;
        final boolean stopEndpoint = prototype;
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

            LOG.debug(">>>> {} {}", endpoint, e);
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
                    // stop endpoint if prototype as it was only used once
                    if (stopEndpoint) {
                        ServiceHelper.stopAndShutdownService(endpoint);
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

    protected static Endpoint resolveEndpoint(Exchange exchange, Object recipient, boolean prototype) {
        return prototype ? ExchangeHelper.resolvePrototypeEndpoint(exchange, recipient) : ExchangeHelper.resolveEndpoint(exchange, recipient);
    }

    protected Exchange configureExchange(Exchange exchange, ExchangePattern pattern, Endpoint endpoint) {
        if (pattern != null) {
            exchange.setPattern(pattern);
        }
        // set property which endpoint we send to
        exchange.setProperty(Exchange.TO_ENDPOINT, endpoint.getEndpointUri());
        return exchange;
    }

    @Override
    protected void doStart() throws Exception {
        if (producerCache == null) {
            if (cacheSize < 0) {
                producerCache = new EmptyProducerCache(this, camelContext);
                LOG.debug("DynamicSendTo {} is not using ProducerCache", this);
            } else {
                producerCache = new DefaultProducerCache(this, camelContext, cacheSize);
                LOG.debug("DynamicSendTo {} using ProducerCache with cacheSize={}", this, cacheSize);
            }
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
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Detected SendDynamicAware component: {} optimising toD: {}", scheme, URISupport.sanitizeUri(uri));
                        }
                    }
                }
            } catch (Throwable e) {
                // ignore
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Error creating optimised SendDynamicAwareResolver for uri: " + URISupport.sanitizeUri(uri)
                        + " due to " + e.getMessage() + ". This exception is ignored", e);
                }
            }
        }

        ServiceHelper.startService(producerCache);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);
    }

    public EndpointUtilizationStatistics getEndpointUtilizationStatistics() {
        return producerCache.getEndpointUtilizationStatistics();
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
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

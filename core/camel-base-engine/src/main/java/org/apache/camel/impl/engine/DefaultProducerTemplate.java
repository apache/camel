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
package org.apache.camel.impl.engine;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spi.ProducerCache;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.cache.DefaultProducerCache;
import org.apache.camel.support.processor.ConvertBodyProcessor;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.concurrent.SynchronousExecutorService;

/**
 * Template (named like Spring's TransactionTemplate & JmsTemplate et al) for working with Camel and sending
 * {@link Message} instances in an {@link Exchange} to an {@link Endpoint}.
 */
public class DefaultProducerTemplate extends ServiceSupport implements ProducerTemplate {
    private final CamelContext camelContext;
    private volatile ProducerCache producerCache;
    private volatile ExecutorService executor;
    private Endpoint defaultEndpoint;
    private int maximumCacheSize;
    private boolean eventNotifierEnabled = true;
    private volatile boolean threadedAsyncMode = true;

    public DefaultProducerTemplate(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public DefaultProducerTemplate(CamelContext camelContext, ExecutorService executor) {
        this.camelContext = camelContext;
        this.executor = executor;
    }

    public DefaultProducerTemplate(CamelContext camelContext, Endpoint defaultEndpoint) {
        this(camelContext);
        this.defaultEndpoint = defaultEndpoint;
    }

    public static DefaultProducerTemplate newInstance(CamelContext camelContext, String defaultEndpointUri) {
        Endpoint endpoint = CamelContextHelper.getMandatoryEndpoint(camelContext, defaultEndpointUri);
        return new DefaultProducerTemplate(camelContext, endpoint);
    }

    @Override
    public int getMaximumCacheSize() {
        return maximumCacheSize;
    }

    @Override
    public void setMaximumCacheSize(int maximumCacheSize) {
        this.maximumCacheSize = maximumCacheSize;
    }

    @Override
    public boolean isThreadedAsyncMode() {
        return threadedAsyncMode;
    }

    @Override
    public void setThreadedAsyncMode(boolean useExecutor) {
        this.threadedAsyncMode = useExecutor;
    }

    @Override
    public int getCurrentCacheSize() {
        if (producerCache == null) {
            return 0;
        }
        return producerCache.size();
    }

    @Override
    public boolean isEventNotifierEnabled() {
        return eventNotifierEnabled;
    }

    @Override
    public void cleanUp() {
        if (producerCache != null) {
            producerCache.cleanUp();
        }
    }

    @Override
    public void setEventNotifierEnabled(boolean eventNotifierEnabled) {
        this.eventNotifierEnabled = eventNotifierEnabled;
        // if we already created the cache then adjust its setting as well
        if (producerCache != null) {
            producerCache.setEventNotifierEnabled(eventNotifierEnabled);
        }
    }

    @Override
    public Exchange send(String endpointUri, Exchange exchange) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint, exchange);
    }

    @Override
    public Exchange send(String endpointUri, Processor processor) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint, null, processor, null);
    }

    @Override
    public Exchange send(String endpointUri, ExchangePattern pattern, Processor processor) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint, pattern, processor, null);
    }

    @Override
    public Exchange send(Endpoint endpoint, Exchange exchange) {
        return send(endpoint, exchange, null);
    }

    @Override
    public Exchange send(Endpoint endpoint, Processor processor) {
        return send(endpoint, null, processor, null);
    }

    @Override
    public Exchange send(Endpoint endpoint, ExchangePattern pattern, Processor processor) {
        return send(endpoint, pattern, processor, null);
    }

    @Override
    public Exchange send(Endpoint endpoint, ExchangePattern pattern, Processor processor, Processor resultProcessor) {
        Exchange exchange = pattern != null ? endpoint.createExchange(pattern) : endpoint.createExchange();
        if (processor != null) {
            try {
                processor.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
                return exchange;
            }
        }
        return send(endpoint, exchange, resultProcessor);
    }

    public Exchange send(Endpoint endpoint, Exchange exchange, Processor resultProcessor) {
        return getProducerCache().send(endpoint, exchange, resultProcessor);
    }

    @Override
    public Object sendBody(Endpoint endpoint, ExchangePattern pattern, Object body) {
        Exchange result = send(endpoint, pattern, createSetBodyProcessor(body));
        return extractResultBody(result, pattern);
    }

    @Override
    public void sendBody(Endpoint endpoint, Object body) throws CamelExecutionException {
        Exchange result = send(endpoint, createSetBodyProcessor(body));
        // must invoke extract result body in case of exception to be rethrown
        extractResultBody(result);
    }

    @Override
    public void sendBody(String endpointUri, Object body) throws CamelExecutionException {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        sendBody(endpoint, body);
    }

    @Override
    public Object sendBody(String endpointUri, ExchangePattern pattern, Object body) throws CamelExecutionException {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        Object result = sendBody(endpoint, pattern, body);
        if (pattern == ExchangePattern.InOnly) {
            // return null if not OUT capable
            return null;
        } else {
            return result;
        }
    }

    @Override
    public void sendBodyAndHeader(String endpointUri, final Object body, final String header, final Object headerValue)
            throws CamelExecutionException {
        sendBodyAndHeader(resolveMandatoryEndpoint(endpointUri), body, header, headerValue);
    }

    @Override
    public void sendBodyAndHeader(Endpoint endpoint, final Object body, final String header, final Object headerValue)
            throws CamelExecutionException {
        Exchange result = send(endpoint, createBodyAndHeaderProcessor(body, header, headerValue));
        // must invoke extract result body in case of exception to be rethrown
        extractResultBody(result);
    }

    @Override
    public Object sendBodyAndHeader(
            Endpoint endpoint, ExchangePattern pattern, final Object body,
            final String header, final Object headerValue)
            throws CamelExecutionException {
        Exchange exchange = send(endpoint, pattern, createBodyAndHeaderProcessor(body, header, headerValue));
        Object result = extractResultBody(exchange, pattern);
        if (pattern == ExchangePattern.InOnly) {
            // return null if not OUT capable
            return null;
        } else {
            return result;
        }
    }

    @Override
    public Object sendBodyAndHeader(
            String endpoint, ExchangePattern pattern, final Object body,
            final String header, final Object headerValue)
            throws CamelExecutionException {
        Exchange exchange = send(endpoint, pattern, createBodyAndHeaderProcessor(body, header, headerValue));
        Object result = extractResultBody(exchange, pattern);
        if (pattern == ExchangePattern.InOnly) {
            // return null if not OUT capable
            return null;
        } else {
            return result;
        }
    }

    @Override
    public void sendBodyAndProperty(
            String endpointUri, final Object body,
            final String property, final Object propertyValue)
            throws CamelExecutionException {
        sendBodyAndProperty(resolveMandatoryEndpoint(endpointUri), body, property, propertyValue);
    }

    @Override
    public void sendBodyAndProperty(
            Endpoint endpoint, final Object body,
            final String property, final Object propertyValue)
            throws CamelExecutionException {
        Exchange result = send(endpoint, createBodyAndPropertyProcessor(body, property, propertyValue));
        // must invoke extract result body in case of exception to be rethrown
        extractResultBody(result);
    }

    @Override
    public Object sendBodyAndProperty(
            Endpoint endpoint, ExchangePattern pattern, final Object body,
            final String property, final Object propertyValue)
            throws CamelExecutionException {
        Exchange exchange = send(endpoint, pattern, createBodyAndPropertyProcessor(body, property, propertyValue));
        Object result = extractResultBody(exchange, pattern);
        if (pattern == ExchangePattern.InOnly) {
            // return null if not OUT capable
            return null;
        } else {
            return result;
        }
    }

    @Override
    public Object sendBodyAndProperty(
            String endpoint, ExchangePattern pattern, final Object body,
            final String property, final Object propertyValue)
            throws CamelExecutionException {
        Exchange exchange = send(endpoint, pattern, createBodyAndPropertyProcessor(body, property, propertyValue));
        Object result = extractResultBody(exchange, pattern);
        if (pattern == ExchangePattern.InOnly) {
            // return null if not OUT capable
            return null;
        } else {
            return result;
        }
    }

    @Override
    public void sendBodyAndHeaders(String endpointUri, final Object body, final Map<String, Object> headers)
            throws CamelExecutionException {
        sendBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), body, headers);
    }

    @Override
    public void sendBodyAndHeaders(Endpoint endpoint, final Object body, final Map<String, Object> headers)
            throws CamelExecutionException {
        Exchange result = send(endpoint, createBodyAndHeaders(body, headers));
        // must invoke extract result body in case of exception to be rethrown
        extractResultBody(result);
    }

    @Override
    public Object sendBodyAndHeaders(String endpointUri, ExchangePattern pattern, Object body, Map<String, Object> headers)
            throws CamelExecutionException {
        return sendBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), pattern, body, headers);
    }

    @Override
    public Object sendBodyAndHeaders(
            Endpoint endpoint, ExchangePattern pattern, final Object body, final Map<String, Object> headers)
            throws CamelExecutionException {
        Exchange exchange = send(endpoint, pattern, createBodyAndHeaders(body, headers));
        Object result = extractResultBody(exchange, pattern);
        if (pattern == ExchangePattern.InOnly) {
            // return null if not OUT capable
            return null;
        } else {
            return result;
        }
    }

    // Methods using an InOut ExchangePattern
    // -----------------------------------------------------------------------

    @Override
    public Exchange request(Endpoint endpoint, Processor processor) {
        return send(endpoint, ExchangePattern.InOut, processor);
    }

    @Override
    public Object requestBody(Object body) throws CamelExecutionException {
        return sendBody(getMandatoryDefaultEndpoint(), ExchangePattern.InOut, body);
    }

    @Override
    public Object requestBody(Endpoint endpoint, Object body) throws CamelExecutionException {
        return sendBody(endpoint, ExchangePattern.InOut, body);
    }

    @Override
    public Object requestBodyAndHeader(Object body, String header, Object headerValue) throws CamelExecutionException {
        return sendBodyAndHeader(getMandatoryDefaultEndpoint(), ExchangePattern.InOut, body, header, headerValue);
    }

    @Override
    public Object requestBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue)
            throws CamelExecutionException {
        return sendBodyAndHeader(endpoint, ExchangePattern.InOut, body, header, headerValue);
    }

    @Override
    public Exchange request(String endpointUri, Processor processor) throws CamelExecutionException {
        return send(resolveMandatoryEndpoint(endpointUri), ExchangePattern.InOut, processor, null);
    }

    @Override
    public Object requestBody(String endpointUri, Object body) throws CamelExecutionException {
        return sendBody(resolveMandatoryEndpoint(endpointUri), ExchangePattern.InOut, body);
    }

    @Override
    public Object requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue)
            throws CamelExecutionException {
        return sendBodyAndHeader(resolveMandatoryEndpoint(endpointUri), ExchangePattern.InOut, body, header, headerValue);
    }

    @Override
    public Object requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) {
        return sendBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), ExchangePattern.InOut, body, headers);
    }

    @Override
    public Object requestBodyAndHeaders(Endpoint endpoint, final Object body, final Map<String, Object> headers) {
        return sendBodyAndHeaders(endpoint, ExchangePattern.InOut, body, headers);
    }

    @Override
    public Object requestBodyAndHeaders(final Object body, final Map<String, Object> headers) {
        return sendBodyAndHeaders(getMandatoryDefaultEndpoint(), ExchangePattern.InOut, body, headers);
    }

    @Override
    public <T> T requestBody(Object body, Class<T> type) {
        return requestBody(getMandatoryDefaultEndpoint(), body, type);
    }

    @Override
    public <T> T requestBody(Endpoint endpoint, Object body, Class<T> type) {
        Exchange exchange
                = send(endpoint, ExchangePattern.InOut, createSetBodyProcessor(body), new ConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    @Override
    public <T> T requestBody(String endpointUri, Object body, Class<T> type) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        Exchange exchange
                = send(endpoint, ExchangePattern.InOut, createSetBodyProcessor(body), new ConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    @Override
    public <T> T requestBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue, Class<T> type) {
        Exchange exchange = send(endpoint, ExchangePattern.InOut, createBodyAndHeaderProcessor(body, header, headerValue),
                new ConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    @Override
    public <T> T requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue, Class<T> type) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        Exchange exchange = send(endpoint, ExchangePattern.InOut, createBodyAndHeaderProcessor(body, header, headerValue),
                new ConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    @Override
    public <T> T requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers, Class<T> type) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        Exchange exchange
                = send(endpoint, ExchangePattern.InOut, createBodyAndHeaders(body, headers), new ConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    @Override
    public <T> T requestBodyAndHeaders(Endpoint endpoint, Object body, Map<String, Object> headers, Class<T> type) {
        Exchange exchange
                = send(endpoint, ExchangePattern.InOut, createBodyAndHeaders(body, headers), new ConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    // Methods using the default endpoint
    // -----------------------------------------------------------------------

    @Override
    public void sendBody(Object body) {
        sendBody(getMandatoryDefaultEndpoint(), body);
    }

    @Override
    public Exchange send(Exchange exchange) {
        return send(getMandatoryDefaultEndpoint(), exchange);
    }

    @Override
    public Exchange send(Processor processor) {
        return send(getMandatoryDefaultEndpoint(), processor);
    }

    @Override
    public void sendBodyAndHeader(Object body, String header, Object headerValue) {
        sendBodyAndHeader(getMandatoryDefaultEndpoint(), body, header, headerValue);
    }

    @Override
    public void sendBodyAndProperty(Object body, String property, Object propertyValue) {
        sendBodyAndProperty(getMandatoryDefaultEndpoint(), body, property, propertyValue);
    }

    @Override
    public void sendBodyAndHeaders(Object body, Map<String, Object> headers) {
        sendBodyAndHeaders(getMandatoryDefaultEndpoint(), body, headers);
    }

    // Properties
    // -----------------------------------------------------------------------

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public Endpoint getDefaultEndpoint() {
        return defaultEndpoint;
    }

    @Override
    public void setDefaultEndpoint(Endpoint defaultEndpoint) {
        this.defaultEndpoint = defaultEndpoint;
    }

    /**
     * Sets the default endpoint to use if none is specified
     */
    @Override
    public void setDefaultEndpointUri(String endpointUri) {
        setDefaultEndpoint(getCamelContext().getEndpoint(endpointUri));
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    protected Processor createBodyAndHeaderProcessor(final Object body, final String header, final Object headerValue) {
        return new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setHeader(header, headerValue);
                in.setBody(body);
            }
        };
    }

    protected Processor createBodyAndHeaders(final Object body, final Map<String, Object> headers) {
        return new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                if (headers != null) {
                    for (Map.Entry<String, Object> header : headers.entrySet()) {
                        in.setHeader(header.getKey(), header.getValue());
                    }
                }
                in.setBody(body);
            }
        };
    }

    protected Processor createBodyAndPropertyProcessor(final Object body, final String property, final Object propertyValue) {
        return new Processor() {
            public void process(Exchange exchange) {
                exchange.setProperty(property, propertyValue);
                Message in = exchange.getIn();
                in.setBody(body);
            }
        };
    }

    protected Processor createSetBodyProcessor(final Object body) {
        return new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                in.setBody(body);
            }
        };
    }

    protected Function<Exchange, Exchange> createCompletionFunction(Synchronization onCompletion) {
        return answer -> {
            // invoke callback before returning answer
            // as it allows callback to be used without unit of work invoking it
            // and thus it works directly from a producer template as well, as opposed
            // to the unit of work that is injected in routes
            if (answer.isFailed()) {
                onCompletion.onFailure(answer);
            } else {
                onCompletion.onComplete(answer);
            }
            return answer;
        };
    }

    protected Endpoint resolveMandatoryEndpoint(String endpointUri) {
        Endpoint endpoint = camelContext.getEndpoint(endpointUri);
        if (endpoint == null) {
            throw new NoSuchEndpointException(endpointUri);
        }
        return endpoint;
    }

    protected Endpoint getMandatoryDefaultEndpoint() {
        Endpoint answer = getDefaultEndpoint();
        ObjectHelper.notNull(answer, "defaultEndpoint");
        return answer;
    }

    protected Object extractResultBody(Exchange result) {
        return extractResultBody(result, null);
    }

    protected Object extractResultBody(Exchange result, ExchangePattern pattern) {
        return ExchangeHelper.extractResultBody(result, pattern);
    }

    @Override
    public void setExecutorService(ExecutorService executorService) {
        this.executor = executorService;
    }

    @Override
    public CompletableFuture<Exchange> asyncSend(final String uri, final Exchange exchange) {
        return asyncSend(resolveMandatoryEndpoint(uri), exchange);
    }

    @Override
    public CompletableFuture<Exchange> asyncSend(final String uri, final Processor processor) {
        return asyncSend(resolveMandatoryEndpoint(uri), processor);
    }

    @Override
    public CompletableFuture<Object> asyncSendBody(final String uri, final Object body) {
        return asyncSendBody(resolveMandatoryEndpoint(uri), body);
    }

    @Override
    public CompletableFuture<Object> asyncRequestBody(final String uri, final Object body) {
        return asyncRequestBody(resolveMandatoryEndpoint(uri), body);
    }

    @Override
    public <T> CompletableFuture<T> asyncRequestBody(final String uri, final Object body, final Class<T> type) {
        return asyncRequestBody(resolveMandatoryEndpoint(uri), createSetBodyProcessor(body), type);
    }

    @Override
    public CompletableFuture<Object> asyncRequestBodyAndHeader(
            final String endpointUri, final Object body, final String header, final Object headerValue) {
        return asyncRequestBodyAndHeader(resolveMandatoryEndpoint(endpointUri), body, header, headerValue);
    }

    @Override
    public <T> CompletableFuture<T> asyncRequestBodyAndHeader(
            final String endpointUri, final Object body, final String header, final Object headerValue, final Class<T> type) {
        return asyncRequestBodyAndHeader(resolveMandatoryEndpoint(endpointUri), body, header, headerValue, type);
    }

    @Override
    public CompletableFuture<Object> asyncRequestBodyAndHeaders(
            final String endpointUri, final Object body, final Map<String, Object> headers) {
        return asyncRequestBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), body, headers);
    }

    @Override
    public <T> CompletableFuture<T> asyncRequestBodyAndHeaders(
            final String endpointUri, final Object body, final Map<String, Object> headers, final Class<T> type) {
        return asyncRequestBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), body, headers, type);
    }

    @Override
    public <T> T extractFutureBody(Future<?> future, Class<T> type) {
        return ExchangeHelper.extractFutureBody(camelContext, future, type);
    }

    @Override
    public <T> T extractFutureBody(Future<?> future, long timeout, TimeUnit unit, Class<T> type) throws TimeoutException {
        return ExchangeHelper.extractFutureBody(camelContext, future, timeout, unit, type);
    }

    @Override
    public CompletableFuture<Object> asyncRequestBody(final Endpoint endpoint, final Object body) {
        return asyncRequestBody(endpoint, createSetBodyProcessor(body));
    }

    @Override
    public <T> CompletableFuture<T> asyncRequestBody(Endpoint endpoint, Object body, Class<T> type) {
        return asyncRequestBody(endpoint, createSetBodyProcessor(body), type);
    }

    @Override
    public CompletableFuture<Object> asyncRequestBodyAndHeader(
            final Endpoint endpoint, final Object body, final String header,
            final Object headerValue) {
        return asyncRequestBody(endpoint, createBodyAndHeaderProcessor(body, header, headerValue));
    }

    protected <T> CompletableFuture<T> asyncRequestBody(final Endpoint endpoint, Processor processor, final Class<T> type) {
        return asyncRequestBody(endpoint, processor, new ConvertBodyProcessor(type))
                .thenApply(answer -> camelContext.getTypeConverter().convertTo(type, answer));
    }

    @Override
    public <T> CompletableFuture<T> asyncRequestBodyAndHeader(
            final Endpoint endpoint, final Object body, final String header,
            final Object headerValue, final Class<T> type) {
        return asyncRequestBody(endpoint, createBodyAndHeaderProcessor(body, header, headerValue), type);
    }

    @Override
    public CompletableFuture<Object> asyncRequestBodyAndHeaders(
            final Endpoint endpoint, final Object body,
            final Map<String, Object> headers) {
        return asyncRequestBody(endpoint, createBodyAndHeaders(body, headers));
    }

    @Override
    public <T> CompletableFuture<T> asyncRequestBodyAndHeaders(
            final Endpoint endpoint, final Object body,
            final Map<String, Object> headers, final Class<T> type) {
        return asyncRequestBody(endpoint, createBodyAndHeaders(body, headers), type);
    }

    @Override
    public CompletableFuture<Exchange> asyncSend(final Endpoint endpoint, final Exchange exchange) {
        return asyncSendExchange(endpoint, null, null, null, exchange);
    }

    @Override
    public CompletableFuture<Exchange> asyncSend(final Endpoint endpoint, final Processor processor) {
        return asyncSend(endpoint, null, processor, null);
    }

    @Override
    public CompletableFuture<Object> asyncSendBody(final Endpoint endpoint, final Object body) {
        return asyncSend(endpoint, createSetBodyProcessor(body))
                .thenApply(this::extractResultBody);
    }

    protected CompletableFuture<Object> asyncRequestBody(final Endpoint endpoint, Processor processor) {
        return asyncRequestBody(endpoint, processor, (Processor) null);
    }

    protected CompletableFuture<Object> asyncRequestBody(
            final Endpoint endpoint, Processor processor, Processor resultProcessor) {
        return asyncRequest(endpoint, processor, resultProcessor)
                .thenApply(e -> extractResultBody(e, ExchangePattern.InOut));
    }

    protected CompletableFuture<Exchange> asyncRequest(
            Endpoint endpoint, Processor processor,
            Processor resultProcessor) {
        return asyncSend(endpoint, ExchangePattern.InOut, processor, resultProcessor);
    }

    protected CompletableFuture<Exchange> asyncSend(
            Endpoint endpoint, ExchangePattern pattern, Processor processor, Processor resultProcessor) {
        return asyncSendExchange(endpoint, pattern, processor, resultProcessor, null);
    }

    protected CompletableFuture<Exchange> asyncSendExchange(
            Endpoint endpoint, ExchangePattern pattern, Processor processor, Processor resultProcessor,
            Exchange inExchange) {
        CompletableFuture<Exchange> exchangeFuture = new CompletableFuture<>();
        getExecutorService().submit(() -> getProducerCache().asyncSendExchange(endpoint, pattern, processor,
                resultProcessor, inExchange, exchangeFuture));
        return exchangeFuture;
    }

    private org.apache.camel.spi.ProducerCache getProducerCache() {
        if (!isStarted()) {
            throw new IllegalStateException("ProducerTemplate has not been started");
        }
        return producerCache;
    }

    private ExecutorService getExecutorService() {
        if (!isStarted()) {
            throw new IllegalStateException("ProducerTemplate has not been started");
        }
        if (executor == null) {
            // create a default executor which must be synchronized
            synchronized (lock) {
                if (executor == null) {
                    if (threadedAsyncMode) {
                        executor = camelContext.getExecutorServiceManager().newDefaultThreadPool(this, "ProducerTemplate");
                    } else {
                        executor = new SynchronousExecutorService();
                    }
                }
            }
        }
        return executor;
    }

    @Override
    protected void doBuild() throws Exception {
        producerCache = new DefaultProducerCache(this, camelContext, maximumCacheSize);
        producerCache.setEventNotifierEnabled(isEventNotifierEnabled());
        ServiceHelper.buildService(producerCache);
    }

    @Override
    protected void doInit() throws Exception {
        // need to lookup default endpoint as it may have been intercepted
        if (defaultEndpoint != null) {
            defaultEndpoint = camelContext.getEndpoint(defaultEndpoint.getEndpointUri());
        }
        ServiceHelper.initService(producerCache);
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(producerCache);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);
        if (executor != null) {
            camelContext.getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(producerCache);
        producerCache = null;
    }
}

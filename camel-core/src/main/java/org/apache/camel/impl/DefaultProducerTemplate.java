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
import org.apache.camel.processor.ConvertBodyProcessor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.CamelContextHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.concurrent.SynchronousExecutorService;

/**
 * Template (named like Spring's TransactionTemplate & JmsTemplate
 * et al) for working with Camel and sending {@link Message} instances in an
 * {@link Exchange} to an {@link Endpoint}.
 *
 * @version 
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

    public int getMaximumCacheSize() {
        return maximumCacheSize;
    }

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

    public int getCurrentCacheSize() {
        if (producerCache == null) {
            return 0;
        }
        return producerCache.size();
    }

    public boolean isEventNotifierEnabled() {
        return eventNotifierEnabled;
    }

    public void cleanUp() {
        if (producerCache != null) {
            producerCache.cleanUp();
        }
    }

    public void setEventNotifierEnabled(boolean eventNotifierEnabled) {
        this.eventNotifierEnabled = eventNotifierEnabled;
        // if we already created the cache then adjust its setting as well
        if (producerCache != null) {
            producerCache.setEventNotifierEnabled(eventNotifierEnabled);
        }
    }

    public Exchange send(String endpointUri, Exchange exchange) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint, exchange);
    }

    public Exchange send(String endpointUri, Processor processor) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint, processor);
    }

    public Exchange send(String endpointUri, ExchangePattern pattern, Processor processor) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint, pattern, processor);
    }

    public Exchange send(Endpoint endpoint, Exchange exchange) {
        getProducerCache().send(endpoint, exchange);
        return exchange;
    }

    public Exchange send(Endpoint endpoint, Processor processor) {
        return getProducerCache().send(endpoint, processor);
    }

    public Exchange send(Endpoint endpoint, ExchangePattern pattern, Processor processor) {
        return getProducerCache().send(endpoint, pattern, processor);
    }

    public Exchange send(Endpoint endpoint, ExchangePattern pattern, Processor processor, Processor resultProcessor) {
        return getProducerCache().send(endpoint, pattern, processor, resultProcessor);
    }

    public Object sendBody(Endpoint endpoint, ExchangePattern pattern, Object body) {
        Exchange result = send(endpoint, pattern, createSetBodyProcessor(body));
        return extractResultBody(result, pattern);
    }

    public void sendBody(Endpoint endpoint, Object body) throws CamelExecutionException {
        Exchange result = send(endpoint, createSetBodyProcessor(body));
        // must invoke extract result body in case of exception to be rethrown
        extractResultBody(result);
    }

    public void sendBody(String endpointUri, Object body) throws CamelExecutionException {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        sendBody(endpoint, body);
    }

    public Object sendBody(String endpointUri, ExchangePattern pattern, Object body) throws CamelExecutionException {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        Object result = sendBody(endpoint, pattern, body);
        if (pattern.isOutCapable()) {
            return result;
        } else {
            // return null if not OUT capable
            return null;
        }
    }

    public void sendBodyAndHeader(String endpointUri, final Object body, final String header, final Object headerValue) throws CamelExecutionException {
        sendBodyAndHeader(resolveMandatoryEndpoint(endpointUri), body, header, headerValue);
    }

    public void sendBodyAndHeader(Endpoint endpoint, final Object body, final String header, final Object headerValue) throws CamelExecutionException {
        Exchange result = send(endpoint, createBodyAndHeaderProcessor(body, header, headerValue));
        // must invoke extract result body in case of exception to be rethrown
        extractResultBody(result);
    }

    public Object sendBodyAndHeader(Endpoint endpoint, ExchangePattern pattern, final Object body,
                                    final String header, final Object headerValue) throws CamelExecutionException {
        Exchange exchange = send(endpoint, pattern, createBodyAndHeaderProcessor(body, header, headerValue));
        Object result = extractResultBody(exchange, pattern);
        if (pattern.isOutCapable()) {
            return result;
        } else {
            // return null if not OUT capable
            return null;
        }
    }

    public Object sendBodyAndHeader(String endpoint, ExchangePattern pattern, final Object body,
                                    final String header, final Object headerValue) throws CamelExecutionException {
        Exchange exchange = send(endpoint, pattern, createBodyAndHeaderProcessor(body, header, headerValue));
        Object result = extractResultBody(exchange, pattern);
        if (pattern.isOutCapable()) {
            return result;
        } else {
            // return null if not OUT capable
            return null;
        }
    }

    public void sendBodyAndProperty(String endpointUri, final Object body,
                                    final String property, final Object propertyValue) throws CamelExecutionException {
        sendBodyAndProperty(resolveMandatoryEndpoint(endpointUri), body, property, propertyValue);
    }

    public void sendBodyAndProperty(Endpoint endpoint, final Object body,
                                    final String property, final Object propertyValue) throws CamelExecutionException {
        Exchange result = send(endpoint, createBodyAndPropertyProcessor(body, property, propertyValue));
        // must invoke extract result body in case of exception to be rethrown
        extractResultBody(result);
    }

    public Object sendBodyAndProperty(Endpoint endpoint, ExchangePattern pattern, final Object body,
                                      final String property, final Object propertyValue) throws CamelExecutionException {
        Exchange exchange = send(endpoint, pattern, createBodyAndPropertyProcessor(body, property, propertyValue));
        Object result = extractResultBody(exchange, pattern);
        if (pattern.isOutCapable()) {
            return result;
        } else {
            // return null if not OUT capable
            return null;
        }
    }

    public Object sendBodyAndProperty(String endpoint, ExchangePattern pattern, final Object body,
                                      final String property, final Object propertyValue) throws CamelExecutionException {
        Exchange exchange = send(endpoint, pattern, createBodyAndPropertyProcessor(body, property, propertyValue));
        Object result = extractResultBody(exchange, pattern);
        if (pattern.isOutCapable()) {
            return result;
        } else {
            // return null if not OUT capable
            return null;
        }
    }

    public void sendBodyAndHeaders(String endpointUri, final Object body, final Map<String, Object> headers) throws CamelExecutionException {
        sendBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), body, headers);
    }

    public void sendBodyAndHeaders(Endpoint endpoint, final Object body, final Map<String, Object> headers) throws CamelExecutionException {
        Exchange result = send(endpoint, createBodyAndHeaders(body, headers));
        // must invoke extract result body in case of exception to be rethrown
        extractResultBody(result);
    }

    public Object sendBodyAndHeaders(String endpointUri, ExchangePattern pattern, Object body, Map<String, Object> headers) throws CamelExecutionException {
        return sendBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), pattern, body, headers);
    }

    public Object sendBodyAndHeaders(Endpoint endpoint, ExchangePattern pattern, final Object body, final Map<String, Object> headers) throws CamelExecutionException {
        Exchange exchange = send(endpoint, pattern, createBodyAndHeaders(body, headers));
        Object result = extractResultBody(exchange, pattern);
        if (pattern.isOutCapable()) {
            return result;
        } else {
            // return null if not OUT capable
            return null;
        }
    }

    // Methods using an InOut ExchangePattern
    // -----------------------------------------------------------------------

    public Exchange request(Endpoint endpoint, Processor processor) {
        return send(endpoint, ExchangePattern.InOut, processor);
    }

    public Object requestBody(Object body) throws CamelExecutionException {
        return sendBody(getMandatoryDefaultEndpoint(), ExchangePattern.InOut, body);
    }

    public Object requestBody(Endpoint endpoint, Object body) throws CamelExecutionException {
        return sendBody(endpoint, ExchangePattern.InOut, body);
    }

    public Object requestBodyAndHeader(Object body, String header, Object headerValue) throws CamelExecutionException {
        return sendBodyAndHeader(getMandatoryDefaultEndpoint(), ExchangePattern.InOut, body, header, headerValue);
    }

    public Object requestBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue) throws CamelExecutionException {
        return sendBodyAndHeader(endpoint, ExchangePattern.InOut, body, header, headerValue);
    }

    public Exchange request(String endpoint, Processor processor) throws CamelExecutionException {
        return send(endpoint, ExchangePattern.InOut, processor);
    }

    public Object requestBody(String endpoint, Object body) throws CamelExecutionException {
        return sendBody(endpoint, ExchangePattern.InOut, body);
    }

    public Object requestBodyAndHeader(String endpoint, Object body, String header, Object headerValue) throws CamelExecutionException {
        return sendBodyAndHeader(endpoint, ExchangePattern.InOut, body, header, headerValue);
    }

    public Object requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers) {
        return requestBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), body, headers);
    }

    public Object requestBodyAndHeaders(Endpoint endpoint, final Object body, final Map<String, Object> headers) {
        return sendBodyAndHeaders(endpoint, ExchangePattern.InOut, body, headers);
    }

    public Object requestBodyAndHeaders(final Object body, final Map<String, Object> headers) {
        return sendBodyAndHeaders(getDefaultEndpoint(), ExchangePattern.InOut, body, headers);
    }

    public <T> T requestBody(Object body, Class<T> type) {
        Exchange exchange = producerCache.send(getMandatoryDefaultEndpoint(), ExchangePattern.InOut, createSetBodyProcessor(body), createConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    public <T> T requestBody(Endpoint endpoint, Object body, Class<T> type) {
        Exchange exchange = producerCache.send(endpoint, ExchangePattern.InOut, createSetBodyProcessor(body), createConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    public <T> T requestBody(String endpointUri, Object body, Class<T> type) {
        Exchange exchange = producerCache.send(resolveMandatoryEndpoint(endpointUri), ExchangePattern.InOut, createSetBodyProcessor(body), createConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    public <T> T requestBodyAndHeader(Endpoint endpoint, Object body, String header, Object headerValue, Class<T> type) {
        Exchange exchange = producerCache.send(endpoint, ExchangePattern.InOut, createBodyAndHeaderProcessor(body, header, headerValue), createConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    public <T> T requestBodyAndHeader(String endpointUri, Object body, String header, Object headerValue, Class<T> type) {
        Exchange exchange = producerCache.send(resolveMandatoryEndpoint(endpointUri), ExchangePattern.InOut, createBodyAndHeaderProcessor(body, header, headerValue), createConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    public <T> T requestBodyAndHeaders(String endpointUri, Object body, Map<String, Object> headers, Class<T> type) {
        Exchange exchange = producerCache.send(resolveMandatoryEndpoint(endpointUri), ExchangePattern.InOut, createBodyAndHeaders(body, headers), createConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    public <T> T requestBodyAndHeaders(Endpoint endpoint, Object body, Map<String, Object> headers, Class<T> type) {
        Exchange exchange = producerCache.send(endpoint, ExchangePattern.InOut, createBodyAndHeaders(body, headers), createConvertBodyProcessor(type));
        Object answer = extractResultBody(exchange);
        return camelContext.getTypeConverter().convertTo(type, answer);
    }

    // Methods using the default endpoint
    // -----------------------------------------------------------------------

    public void sendBody(Object body) {
        sendBody(getMandatoryDefaultEndpoint(), body);
    }

    public Exchange send(Exchange exchange) {
        return send(getMandatoryDefaultEndpoint(), exchange);
    }

    public Exchange send(Processor processor) {
        return send(getMandatoryDefaultEndpoint(), processor);
    }

    public void sendBodyAndHeader(Object body, String header, Object headerValue) {
        sendBodyAndHeader(getMandatoryDefaultEndpoint(), body, header, headerValue);
    }

    public void sendBodyAndProperty(Object body, String property, Object propertyValue) {
        sendBodyAndProperty(getMandatoryDefaultEndpoint(), body, property, propertyValue);
    }

    public void sendBodyAndHeaders(Object body, Map<String, Object> headers) {
        sendBodyAndHeaders(getMandatoryDefaultEndpoint(), body, headers);
    }

    // Properties
    // -----------------------------------------------------------------------

    /**
     * @deprecated use {@link #getCamelContext()}
     */
    @Deprecated
    public CamelContext getContext() {
        return getCamelContext();
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public Endpoint getDefaultEndpoint() {
        return defaultEndpoint;
    }

    public void setDefaultEndpoint(Endpoint defaultEndpoint) {
        this.defaultEndpoint = defaultEndpoint;
    }

    /**
     * Sets the default endpoint to use if none is specified
     */
    public void setDefaultEndpointUri(String endpointUri) {
        setDefaultEndpoint(getCamelContext().getEndpoint(endpointUri));
    }

    /**
     * @deprecated use {@link CamelContext#getEndpoint(String, Class)}
     */
    @Deprecated
    public <T extends Endpoint> T getResolvedEndpoint(String endpointUri, Class<T> expectedClass) {
        return camelContext.getEndpoint(endpointUri, expectedClass);
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

    protected Processor createConvertBodyProcessor(final Class<?> type) {
        return new ConvertBodyProcessor(type);
    }

    protected Function<Exchange, Exchange> createCompletionFunction(Synchronization onCompletion) {
        return (answer) -> {
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

    public void setExecutorService(ExecutorService executorService) {
        this.executor = executorService;
    }

    public CompletableFuture<Exchange> asyncSend(final String uri, final Exchange exchange) {
        return asyncSend(resolveMandatoryEndpoint(uri), exchange);
    }

    public CompletableFuture<Exchange> asyncSend(final String uri, final Processor processor) {
        return asyncSend(resolveMandatoryEndpoint(uri), processor);
    }

    public CompletableFuture<Object> asyncSendBody(final String uri, final Object body) {
        return asyncSendBody(resolveMandatoryEndpoint(uri), body);
    }

    public CompletableFuture<Object> asyncRequestBody(final String uri, final Object body) {
        return asyncRequestBody(resolveMandatoryEndpoint(uri), body);
    }

    public <T> CompletableFuture<T> asyncRequestBody(final String uri, final Object body, final Class<T> type) {
        return asyncRequestBody(resolveMandatoryEndpoint(uri), createSetBodyProcessor(body), type);
    }

    public CompletableFuture<Object> asyncRequestBodyAndHeader(final String endpointUri, final Object body, final String header, final Object headerValue) {
        return asyncRequestBodyAndHeader(resolveMandatoryEndpoint(endpointUri), body, header, headerValue);
    }

    public <T> CompletableFuture<T> asyncRequestBodyAndHeader(final String endpointUri, final Object body, final String header, final Object headerValue, final Class<T> type) {
        return asyncRequestBodyAndHeader(resolveMandatoryEndpoint(endpointUri), body, header, headerValue, type);
    }

    public CompletableFuture<Object> asyncRequestBodyAndHeaders(final String endpointUri, final Object body, final Map<String, Object> headers) {
        return asyncRequestBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), body, headers);
    }

    public <T> CompletableFuture<T> asyncRequestBodyAndHeaders(final String endpointUri, final Object body, final Map<String, Object> headers, final Class<T> type) {
        return asyncRequestBodyAndHeaders(resolveMandatoryEndpoint(endpointUri), body, headers, type);
    }

    public <T> T extractFutureBody(Future<?> future, Class<T> type) {
        return ExchangeHelper.extractFutureBody(camelContext, future, type);
    }

    public <T> T extractFutureBody(Future<?> future, long timeout, TimeUnit unit, Class<T> type) throws TimeoutException {
        return ExchangeHelper.extractFutureBody(camelContext, future, timeout, unit, type);
    }

    public CompletableFuture<Object> asyncCallbackSendBody(String uri, Object body, Synchronization onCompletion) {
        return asyncCallbackSendBody(resolveMandatoryEndpoint(uri), body, onCompletion);
    }

    public CompletableFuture<Object> asyncCallbackSendBody(Endpoint endpoint, Object body, Synchronization onCompletion) {
        return asyncCallback(endpoint, ExchangePattern.InOnly, body, onCompletion);
    }

    public CompletableFuture<Object> asyncCallbackRequestBody(String uri, Object body, Synchronization onCompletion) {
        return asyncCallbackRequestBody(resolveMandatoryEndpoint(uri), body, onCompletion);
    }

    public CompletableFuture<Object> asyncCallbackRequestBody(Endpoint endpoint, Object body, Synchronization onCompletion) {
        return asyncCallback(endpoint, ExchangePattern.InOut, body, onCompletion);
    }

    public CompletableFuture<Exchange> asyncCallback(String uri, Exchange exchange, Synchronization onCompletion) {
        return asyncCallback(resolveMandatoryEndpoint(uri), exchange, onCompletion);
    }

    public CompletableFuture<Exchange> asyncCallback(String uri, Processor processor, Synchronization onCompletion) {
        return asyncCallback(resolveMandatoryEndpoint(uri), processor, onCompletion);
    }

    public CompletableFuture<Object> asyncRequestBody(final Endpoint endpoint, final Object body) {
        return asyncRequestBody(endpoint, createSetBodyProcessor(body));
    }

    public <T> CompletableFuture<T> asyncRequestBody(Endpoint endpoint, Object body, Class<T> type) {
        return asyncRequestBody(endpoint, createSetBodyProcessor(body), type);
    }

    public CompletableFuture<Object> asyncRequestBodyAndHeader(final Endpoint endpoint, final Object body, final String header,
                                                    final Object headerValue) {
        return asyncRequestBody(endpoint, createBodyAndHeaderProcessor(body, header, headerValue));
    }

    protected  <T> CompletableFuture<T> asyncRequestBody(final Endpoint endpoint, Processor processor, final Class<T> type) {
        return asyncRequestBody(endpoint, processor, createConvertBodyProcessor(type))
                .thenApply(answer -> camelContext.getTypeConverter().convertTo(type, answer));
    }

    public <T> CompletableFuture<T> asyncRequestBodyAndHeader(final Endpoint endpoint, final Object body, final String header,
                                                   final Object headerValue, final Class<T> type) {
        return asyncRequestBody(endpoint, createBodyAndHeaderProcessor(body, header, headerValue), type);
    }

    public CompletableFuture<Object> asyncRequestBodyAndHeaders(final Endpoint endpoint, final Object body,
                                                     final Map<String, Object> headers) {
        return asyncRequestBody(endpoint, createBodyAndHeaders(body, headers));
    }

    public <T> CompletableFuture<T> asyncRequestBodyAndHeaders(final Endpoint endpoint, final Object body,
                                                    final Map<String, Object> headers, final Class<T> type) {
        return asyncRequestBody(endpoint, createBodyAndHeaders(body, headers), type);
    }

    public CompletableFuture<Exchange> asyncSend(final Endpoint endpoint, final Exchange exchange) {
        return asyncSendExchange(endpoint, null, null, null, exchange);
    }

    public CompletableFuture<Exchange> asyncSend(final Endpoint endpoint, final Processor processor) {
        return asyncSend(endpoint, null, processor, null);
    }

    public CompletableFuture<Object> asyncSendBody(final Endpoint endpoint, final Object body) {
        return asyncSend(endpoint, createSetBodyProcessor(body))
                .thenApply(this::extractResultBody);
    }

    public CompletableFuture<Exchange> asyncCallback(final Endpoint endpoint, final Exchange exchange, final Synchronization onCompletion) {
        return asyncSend(endpoint, exchange).thenApply(createCompletionFunction(onCompletion));
    }

    public CompletableFuture<Exchange> asyncCallback(final Endpoint endpoint, final Processor processor, final Synchronization onCompletion) {
        return asyncSend(endpoint, processor).thenApply(createCompletionFunction(onCompletion));
    }

    protected CompletableFuture<Object> asyncRequestBody(final Endpoint endpoint, Processor processor) {
        return asyncRequestBody(endpoint, processor, (Processor) null);
    }

    protected CompletableFuture<Object> asyncRequestBody(final Endpoint endpoint, Processor processor, Processor resultProcessor) {
        return asyncRequest(endpoint, processor, resultProcessor)
                .thenApply(e -> extractResultBody(e, ExchangePattern.InOut));
    }

    protected CompletableFuture<Exchange> asyncRequest(Endpoint endpoint, Processor processor,
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

    protected CompletableFuture<Object> asyncCallback(final Endpoint endpoint, final ExchangePattern pattern,
            final Object body, final Synchronization onCompletion) {
        return asyncSend(endpoint, pattern, createSetBodyProcessor(body), null)
                .thenApply(createCompletionFunction(onCompletion))
                .thenApply(answer -> {
                    Object result = extractResultBody(answer, pattern);
                    if (pattern.isOutCapable()) {
                        return result;
                    } else {
                        // return null if not OUT capable
                        return null;
                    }
                });
    }

    private ProducerCache getProducerCache() {
        if (!isStarted()) {
            throw new IllegalStateException("ProducerTemplate has not been started");
        }
        return producerCache;
    }

    private ExecutorService getExecutorService() {
        if (!isStarted()) {
            throw new IllegalStateException("ProducerTemplate has not been started");
        }

        if (executor != null) {
            return executor;
        }

        // create a default executor which must be synchronized
        synchronized (this) {
            if (executor != null) {
                return executor;
            }
            if (!threadedAsyncMode) {
                executor = new SynchronousExecutorService();
            } else {
                executor = camelContext.getExecutorServiceManager().newDefaultThreadPool(this, "ProducerTemplate");
            }
        }

        ObjectHelper.notNull(executor, "ExecutorService");
        return executor;
    }

    protected void doStart() throws Exception {
        if (producerCache == null) {
            if (maximumCacheSize > 0) {
                producerCache = new ProducerCache(this, camelContext, maximumCacheSize);
            } else {
                producerCache = new ProducerCache(this, camelContext);
            }
            producerCache.setEventNotifierEnabled(isEventNotifierEnabled());
        }

        // need to lookup default endpoint as it may have been intercepted
        if (defaultEndpoint != null) {
            defaultEndpoint = camelContext.getEndpoint(defaultEndpoint.getEndpointUri());
        }

        ServiceHelper.startService(producerCache);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopService(producerCache);
        producerCache = null;

        if (executor != null) {
            camelContext.getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
    }

}

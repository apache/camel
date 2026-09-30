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
package org.apache.camel.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ShutdownableService;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.camel.support.service.ServiceHelper;

/**
 * This is an endpoint when sending to it, is intercepted and is routed in a detour (before and optionally after).
 * <p/>
 * The endpoint has one interceptor set by {@link #setBefore(Processor)}, {@link #setAfter(Processor)},
 * {@link #setSkip(boolean)} and {@link #setOnWhen(Predicate)} (such as when mocking endpoints), and it can have
 * interceptors that routes register and unregister with {@link #addInterceptor(Interceptor)} and
 * {@link #removeInterceptor(Interceptor)} (the interceptSendToEndpoint EIP).
 */
public class DefaultInterceptSendToEndpoint implements InterceptSendToEndpoint, ShutdownableService {

    /**
     * An interceptor that a route has registered on the endpoint.
     *
     * @param routeId the id of the route the interceptor belongs to
     * @param before  the processor to route to before sending to the endpoint, which takes care of the onWhen predicate
     *                (and sets the {@link org.apache.camel.ExchangePropertyKey#INTERCEPT_SEND_TO_ENDPOINT_WHEN_MATCHED}
     *                property when it has one)
     * @param after   the optional processor to route to after sending to the endpoint
     * @param skip    whether to skip sending to the endpoint (when the onWhen predicate matched, if any)
     */
    public record Interceptor(String routeId, Processor before, Processor after, boolean skip) {
    }

    private final CopyOnWriteArrayList<Interceptor> interceptors = new CopyOnWriteArrayList<>();

    private final CamelContext camelContext;
    private final Endpoint delegate;
    private Predicate onWhen;
    private Processor before;
    private Processor after;
    private boolean skip;

    /**
     * Intercepts sending to the given endpoint
     *
     * @param destination the original endpoint
     * @param skip        <tt>true</tt> to skip sending after the detour to the original endpoint
     */
    public DefaultInterceptSendToEndpoint(final Endpoint destination, boolean skip) {
        this.camelContext = destination.getCamelContext();
        this.delegate = destination;
        this.skip = skip;
    }

    public Predicate getOnWhen() {
        return onWhen;
    }

    public void setOnWhen(Predicate onWhen) {
        this.onWhen = onWhen;
    }

    public void setBefore(Processor before) {
        this.before = before;
    }

    public void setAfter(Processor after) {
        this.after = after;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    /**
     * Adds an interceptor that a route registers (if not already added)
     */
    public void addInterceptor(Interceptor interceptor) {
        interceptors.addIfAbsent(interceptor);
    }

    /**
     * Removes an interceptor that a route has registered
     */
    public void removeInterceptor(Interceptor interceptor) {
        interceptors.remove(interceptor);
    }

    /**
     * The interceptors that routes have registered, in the order they were added
     */
    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    @Override
    public Processor getBefore() {
        return before;
    }

    @Override
    public Processor getAfter() {
        return after;
    }

    @Override
    public Endpoint getOriginalEndpoint() {
        return delegate;
    }

    @Override
    public boolean isSkip() {
        return skip;
    }

    @Override
    public String getEndpointUri() {
        return delegate.getEndpointUri();
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return delegate.getExchangePattern();
    }

    @Override
    public String getEndpointBaseUri() {
        return delegate.getEndpointBaseUri();
    }

    @Override
    public String getEndpointKey() {
        return delegate.getEndpointKey();
    }

    @Override
    public Exchange createExchange() {
        return delegate.createExchange();
    }

    @Override
    public Exchange createExchange(ExchangePattern pattern) {
        return delegate.createExchange(pattern);
    }

    @Override
    public void configureExchange(Exchange exchange) {
        delegate.configureExchange(exchange);
    }

    @Override
    public CamelContext getCamelContext() {
        return delegate.getCamelContext();
    }

    @Override
    public void setComponent(Component component) {
        delegate.setComponent(component);
    }

    @Override
    public Component getComponent() {
        return delegate.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return createAsyncProducer();
    }

    @Override
    public AsyncProducer createAsyncProducer() throws Exception {
        AsyncProducer producer = delegate.createAsyncProducer();
        return PluginHelper.getInternalProcessorFactory(camelContext)
                .createInterceptSendToEndpointProcessor(this, delegate, producer, skip, onWhen);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return delegate.createConsumer(processor);
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        return delegate.createPollingConsumer();
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        delegate.configureProperties(options);
    }

    @Override
    public void setCamelContext(CamelContext context) {
        delegate.setCamelContext(context);
    }

    @Override
    public boolean isLenientProperties() {
        return delegate.isLenientProperties();
    }

    @Override
    public boolean isSingleton() {
        return delegate.isSingleton();
    }

    @Override
    public void start() {
        ServiceHelper.startService(before, delegate);
    }

    @Override
    public void stop() {
        ServiceHelper.stopService(delegate, before);
    }

    @Override
    public void shutdown() {
        ServiceHelper.stopAndShutdownServices(delegate, before);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}

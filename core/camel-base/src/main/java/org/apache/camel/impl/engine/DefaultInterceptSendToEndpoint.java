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

import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ShutdownableService;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.apache.camel.support.service.ServiceHelper;

/**
 * This is an endpoint when sending to it, is intercepted and is routed in a detour (before and optionally after).
 */
public class DefaultInterceptSendToEndpoint implements InterceptSendToEndpoint, ShutdownableService {

    private final Endpoint delegate;
    private Processor before;
    private Processor after;
    private boolean skip;

    /**
     * Intercepts sending to the given endpoint
     *
     * @param destination  the original endpoint
     * @param skip <tt>true</tt> to skip sending after the detour to the original endpoint
     */
    public DefaultInterceptSendToEndpoint(final Endpoint destination, boolean skip) {
        this.delegate = destination;
        this.skip = skip;
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
    public CamelContext getCamelContext() {
        return delegate.getCamelContext();
    }

    @Override
    public Producer createProducer() throws Exception {
        return createAsyncProducer();
    }

    @Override
    public AsyncProducer createAsyncProducer() throws Exception {
        AsyncProducer producer = delegate.createAsyncProducer();
        return new InterceptSendToEndpointProcessor(this, delegate, producer, skip);
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

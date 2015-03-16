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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.util.ServiceHelper;

/**
 * A {@link Producer} that defers being started, until {@link org.apache.camel.CamelContext} has been started, this
 * ensures that the producer is able to adapt to changes that may otherwise occur during starting
 * CamelContext. If we do not defer starting the producer it may not adapt to those changes, and
 * send messages to wrong endpoints.
 */
public class DeferProducer extends org.apache.camel.support.ServiceSupport implements Producer, AsyncProcessor {

    private Producer delegate;
    private final Endpoint endpoint;

    public DeferProducer(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public Exchange createExchange() {
        if (delegate == null) {
            throw new IllegalStateException("Not started");
        }
        return delegate.createExchange();
    }

    @Override
    public Exchange createExchange(ExchangePattern pattern) {
        if (delegate == null) {
            throw new IllegalStateException("Not started");
        }
        return delegate.createExchange(pattern);
    }

    @Override
    @Deprecated
    public Exchange createExchange(Exchange exchange) {
        if (delegate == null) {
            throw new IllegalStateException("Not started");
        }
        return delegate.createExchange(exchange);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (delegate == null) {
            throw new IllegalStateException("Not started");
        }
        delegate.process(exchange);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (delegate == null) {
            exchange.setException(new IllegalStateException("Not started"));
            callback.done(true);
            return true;
        }

        if (delegate instanceof AsyncProcessor) {
            return ((AsyncProcessor) delegate).process(exchange, callback);
        }

        // fallback to sync mode
        try {
            process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }

        callback.done(true);
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        // need to lookup endpoint again as it may be intercepted
        Endpoint lookup = endpoint.getCamelContext().getEndpoint(endpoint.getEndpointUri());

        delegate = lookup.createProducer();
        ServiceHelper.startService(delegate);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(delegate);
    }

    @Override
    public boolean isSingleton() {
        if (delegate != null) {
            return delegate.isSingleton();
        } else {
            // assume singleton by default
            return true;
        }
    }

    @Override
    public Endpoint getEndpoint() {
        if (delegate != null) {
            return delegate.getEndpoint();
        } else {
            return endpoint;
        }
    }

    @Override
    public String toString() {
        if (delegate != null) {
            return delegate.toString();
        } else {
            return "DelegateProducer[" + endpoint + "]";
        }
    }

}

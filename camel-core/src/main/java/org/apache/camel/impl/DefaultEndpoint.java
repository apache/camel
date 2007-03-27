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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Consumer;
import org.apache.camel.Service;
import org.apache.camel.util.ObjectHelper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;

/**
 * A default endpoint useful for implementation inheritence
 * 
 * @version $Revision$
 */
public abstract class DefaultEndpoint<E extends Exchange> implements Endpoint<E> {
    private String endpointUri;
    private CamelContext context;
    private Processor<E> inboundProcessor;
    protected AtomicBoolean activated = new AtomicBoolean(false);
    protected AtomicBoolean deactivated = new AtomicBoolean(false);

    protected DefaultEndpoint(String endpointUri, CamelContext container) {
        this.endpointUri = endpointUri;
        this.context = container;
    }

    public int hashCode() {
        return endpointUri.hashCode() * 37 + 1;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof DefaultEndpoint) {
            DefaultEndpoint that = (DefaultEndpoint) object;
            return ObjectHelper.equals(this.endpointUri, that.endpointUri);
        }
        return false;
    }

    @Override
    public String toString() {
        return "Endpoint[" + endpointUri + "]";
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    public CamelContext getContext() {
        return context;
    }

    /**
     * Converts the given exchange to the specified exchange type
     */
    public E convertTo(Class<E> type, Exchange exchange) {
        // TODO we could infer type parameter
        if (type.isInstance(exchange)) {
            return type.cast(exchange);
        }
        return getContext().getExchangeConverter().convertTo(type, exchange);
    }

    public void activate(Processor<E> inboundProcessor) throws Exception {
        if (activated.compareAndSet(false, true)) {
            deactivated.set(false);
            this.inboundProcessor = inboundProcessor;
            doActivate();
        }
        else {
            throw new IllegalStateException("Endpoint is already active: " + getEndpointUri());
        }
    }

    public void deactivate() {
        if (deactivated.compareAndSet(false, true)) {
            activated.set(false);
            doDeactivate();
        }
    }


    public E createExchange(E exchange) {
        E answer = createExchange();
        answer.copyFrom(exchange);
        return answer;
    }

    /**
     * The processor used to process inbound message exchanges
     */
    public Processor<E> getInboundProcessor() {
        return inboundProcessor;
    }

    public void setInboundProcessor(Processor<E> inboundProcessor) {
        this.inboundProcessor = inboundProcessor;
    }

    /**
     * Called at most once by the container to activate the endpoint
     */
    protected void doActivate() throws Exception {
    }

    /**
     * Called at most once by the container to deactivate the endpoint
     */
    protected void doDeactivate() {
    }

    protected <T extends Service> T startService(T service) throws Exception {
        service.start();
        return service;
    }
}

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
package org.apache.camel.component.routebox.direct;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.Suspendable;
import org.apache.camel.component.routebox.RouteboxConsumer;
import org.apache.camel.component.routebox.RouteboxServiceSupport;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.util.AsyncProcessorConverterHelper;

public class RouteboxDirectConsumer extends RouteboxServiceSupport implements RouteboxConsumer, ShutdownAware, Suspendable {
    protected ProducerTemplate producer;
    private final Processor processor;
    private volatile AsyncProcessor asyncProcessor;

    public RouteboxDirectConsumer(RouteboxDirectEndpoint endpoint, Processor processor) {
        super(endpoint);
        this.processor = processor;
        producer = endpoint.getConfig().getInnerProducerTemplate();
    }
    
    protected void doStart() throws Exception {
        // add consumer to endpoint
        boolean existing = this == getEndpoint().getConsumer();
        if (!existing && getEndpoint().hasConsumer(this)) {
            throw new IllegalArgumentException("Cannot add a 2nd consumer to the same endpoint. Endpoint " + getEndpoint() + " only allows one consumer.");
        }
        if (!existing) {
            getEndpoint().addConsumer(this);
        }
        
        // now start the inner context
        if (!isStartedInnerContext()) {
            doStartInnerContext(); 
        }
    }

    protected void doStop() throws Exception {
        getEndpoint().removeConsumer(this);
        
        // now stop the inner context
        if (isStartedInnerContext()) {
            doStopInnerContext();
        }
    }

    protected void doSuspend() throws Exception {
        getEndpoint().removeConsumer(this);
    }

    protected void doResume() throws Exception {
        // resume by using the start logic
        doStart();
    }
    
    /**
     * Provides an {@link org.apache.camel.AsyncProcessor} interface to the configured
     * processor on the consumer. If the processor does not implement the interface,
     * it will be adapted so that it does.
     */
    public synchronized AsyncProcessor getAsyncProcessor() {
        if (asyncProcessor == null) {            
            asyncProcessor = AsyncProcessorConverterHelper.convert(processor);
        }
        return asyncProcessor;
    }

    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // deny stopping on shutdown as we want direct consumers to run in case some other queues
        // depend on this consumer to run, so it can complete its exchanges
        return true;
    }

    public int getPendingExchangesSize() {
        // return 0 as we do not have an internal memory queue with a variable size
        // of inflight messages. 
        return 0;
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // noop
    }
    
    public RouteboxDirectEndpoint getEndpoint() {
        return (RouteboxDirectEndpoint) getRouteboxEndpoint();
    }

    public Processor getProcessor() {
        return processor;
    }

}

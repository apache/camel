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

import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.SuspendableService;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.apache.camel.spi.RouteContext;

/**
 * A {@link DefaultRoute} which starts with an
 * <a href="http://camel.apache.org/event-driven-consumer.html">Event Driven Consumer</a>
 * <p/>
 * Use the API from {@link org.apache.camel.CamelContext} to control the lifecycle of a route,
 * such as starting and stopping using the {@link org.apache.camel.CamelContext#startRoute(String)}
 * and {@link org.apache.camel.CamelContext#stopRoute(String)} methods.
 *
 * @version 
 */
public class EventDrivenConsumerRoute extends DefaultRoute {
    private final Processor processor;
    private Consumer consumer;

    public EventDrivenConsumerRoute(RouteContext routeContext, Endpoint endpoint, Processor processor) {
        super(routeContext, endpoint);
        this.processor = processor;
    }

    @Override
    public String toString() {
        return "EventDrivenConsumerRoute[" + getEndpoint() + " -> " + processor + "]";
    }

    public Processor getProcessor() {
        return processor;
    }

    /**
     * Factory method to lazily create the complete list of services required for this route
     * such as adding the processor or consumer
     */
    @Override
    protected void addServices(List<Service> services) throws Exception {
        Endpoint endpoint = getEndpoint();
        consumer = endpoint.createConsumer(processor);
        if (consumer != null) {
            services.add(consumer);
        }
        Processor processor = getProcessor();
        if (processor instanceof Service) {
            services.add((Service)processor);
        }
    }

    @SuppressWarnings("unchecked")
    public Navigate<Processor> navigate() {
        Processor answer = getProcessor();
        // skip the instrumentation processor if this route was wrapped by one
        if (answer instanceof DelegateAsyncProcessor) {
            answer = ((DelegateAsyncProcessor) answer).getProcessor();
        }

        if (answer instanceof Navigate) {
            return (Navigate) answer;
        }
        return null;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public boolean supportsSuspension() {
        return consumer instanceof SuspendableService;
    }
}

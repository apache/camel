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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.RouteAware;
import org.apache.camel.Service;
import org.apache.camel.Suspendable;
import org.apache.camel.SuspendableService;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.EndpointHelper;

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
            if (consumer instanceof RouteAware) {
                ((RouteAware) consumer).setRoute(this);
            }
        }
        Processor processor = getProcessor();
        if (processor instanceof Service) {
            services.add((Service)processor);
        }
    }

    @SuppressWarnings("unchecked")
    public Navigate<Processor> navigate() {
        Processor answer = getProcessor();

        // we want navigating routes to be easy, so skip the initial channel
        // and navigate to its output where it all starts from end user point of view
        if (answer instanceof Navigate) {
            Navigate<Processor> nav = (Navigate<Processor>) answer;
            if (nav.next().size() == 1) {
                Object first = nav.next().get(0);
                if (first instanceof Navigate) {
                    return (Navigate<Processor>) first;
                }
            }
            return (Navigate<Processor>) answer;
        }
        return null;
    }

    public List<Processor> filter(String pattern) {
        List<Processor> match = new ArrayList<Processor>();
        doFilter(pattern, navigate(), match);
        return match;
    }

    @SuppressWarnings("unchecked")
    private void doFilter(String pattern, Navigate<Processor> nav, List<Processor> match) {
        List<Processor> list = nav.next();
        if (list != null) {
            for (Processor proc : list) {
                String id = null;
                if (proc instanceof IdAware) {
                    id = ((IdAware) proc).getId();
                }
                if (EndpointHelper.matchPattern(id, pattern)) {
                    match.add(proc);
                }
                if (proc instanceof Navigate) {
                    Navigate<Processor> child = (Navigate<Processor>) proc;
                    doFilter(pattern, child, match);
                }
            }
        }
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public boolean supportsSuspension() {
        return consumer instanceof Suspendable && consumer instanceof SuspendableService;
    }
}

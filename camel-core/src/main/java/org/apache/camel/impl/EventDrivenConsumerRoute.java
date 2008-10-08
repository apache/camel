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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.Service;

/**
 * A {@link Route} which starts with an
 * <a href="http://activemq.apache.org/camel/event-driven-consumer.html">Event Driven Consumer</a>
 *
 * @version $Revision$
 */
public class EventDrivenConsumerRoute<E extends Exchange> extends Route<E> {
    private Processor processor;

    public EventDrivenConsumerRoute(Endpoint endpoint, Processor processor) {
        super(endpoint);
        this.processor = processor;
    }

    @Override
    public String toString() {
        return "EventDrivenConsumerRoute[" + getEndpoint() + " -> " + processor + "]";
    }

    public Processor getProcessor() {
        return processor;
    }

    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    /**
     * Factory method to lazily create the complete list of services required for this route
     * such as adding the processor or consumer
     */
    @Override
    protected void addServices(List<Service> services) throws Exception {
        Endpoint<E> endpoint = getEndpoint();
        Consumer<E> consumer = endpoint.createConsumer(processor);
        if (consumer != null) {
            services.add(consumer);
        }
        Processor processor = getProcessor();
        if (processor instanceof Service) {
            services.add((Service)processor);
        }
    }
}

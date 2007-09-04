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
package org.apache.camel.component.event;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.TopicLoadBalancer;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

/**
 * An <a href="http://activemq.apache.org/camel/event.html">Event Endpoint</a>
 * for working with Spring ApplicationEvents
 * 
 * @version $Revision: 1.1 $
 */
public class EventEndpoint extends DefaultEndpoint<Exchange> {
    private final EventComponent component;
    private LoadBalancer loadBalancer;

    public EventEndpoint(String endpointUri, EventComponent component) {
        super(endpointUri, component);
        this.component = component;
    }

    @Override
    public EventComponent getComponent() {
        return component;
    }

    public ApplicationContext getApplicationContext() {
        return getComponent().getApplicationContext();
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer<Exchange> createProducer() throws Exception {
        return new DefaultProducer<Exchange>(this) {
            public void process(Exchange exchange) throws Exception {
                ApplicationEvent event = toApplicationEvent(exchange);
                getApplicationContext().publishEvent(event);
            }
        };
    }

    public EventConsumer createConsumer(Processor processor) throws Exception {
        return new EventConsumer(this, processor);
    }

    public void onApplicationEvent(ApplicationEvent event) {
        Exchange exchange = createExchange();
        exchange.getIn().setBody(event);
        try {
            getLoadBalancer().process(exchange);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    public LoadBalancer getLoadBalancer() {
        if (loadBalancer == null) {
            loadBalancer = createLoadBalancer();
        }
        return loadBalancer;
    }

    public void setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    public synchronized void consumerStarted(EventConsumer consumer) {
        getLoadBalancer().addProcessor(consumer.getProcessor());
    }

    public synchronized void consumerStopped(EventConsumer consumer) {
        getLoadBalancer().removeProcessor(consumer.getProcessor());
    }

    protected LoadBalancer createLoadBalancer() {
        return new TopicLoadBalancer();
    }

    protected ApplicationEvent toApplicationEvent(Exchange exchange) {
        ApplicationEvent event = exchange.getIn().getBody(ApplicationEvent.class);
        if (event == null) {
            event = new CamelEvent(this, exchange);
        }
        return event;
    }
}

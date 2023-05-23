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
package org.apache.camel.component.event;

import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.TopicLoadBalancer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;

import static org.apache.camel.RuntimeCamelException.wrapRuntimeCamelException;

/**
 * Listen for Spring Application Events.
 */
@UriEndpoint(firstVersion = "1.4.0", scheme = "spring-event", title = "Spring Event", syntax = "spring-event:name",
             category = { Category.MESSAGING })
public class EventEndpoint extends DefaultEndpoint implements ApplicationContextAware {
    private LoadBalancer loadBalancer;
    private ApplicationContext applicationContext;

    @UriPath(description = "Name of endpoint")
    private String name;

    public EventEndpoint(String endpointUri, EventComponent component, String name) {
        super(endpointUri, component);
        this.applicationContext = component.getApplicationContext();
        this.name = name;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Producer createProducer() throws Exception {
        ObjectHelper.notNull(applicationContext, "applicationContext");
        return new DefaultProducer(this) {
            public void process(Exchange exchange) throws Exception {
                ApplicationEvent event = toApplicationEvent(exchange);
                applicationContext.publishEvent(event);
            }
        };
    }

    @Override
    public EventConsumer createConsumer(Processor processor) throws Exception {
        ObjectHelper.notNull(applicationContext, "applicationContext");
        EventConsumer answer = new EventConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public void onApplicationEvent(ApplicationEvent event) {
        Exchange exchange = createExchange();
        exchange.getIn().setBody(event);
        try {
            getLoadBalancer().process(exchange);
        } catch (Exception e) {
            throw wrapRuntimeCamelException(e);
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

    @Override
    public EventComponent getComponent() {
        return (EventComponent) super.getComponent();
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    public synchronized void consumerStarted(EventConsumer consumer) {
        getComponent().consumerStarted(this);
        getLoadBalancer().addProcessor(consumer.getAsyncProcessor());
    }

    public synchronized void consumerStopped(EventConsumer consumer) {
        getComponent().consumerStopped(this);
        getLoadBalancer().removeProcessor(consumer.getAsyncProcessor());
    }

    protected LoadBalancer createLoadBalancer() {
        return new TopicLoadBalancer();
    }

    protected ApplicationEvent toApplicationEvent(Exchange exchange) {
        ApplicationEvent event = exchange.getIn().getBody(ApplicationEvent.class);
        if (event != null) {
            return event;
        }
        return new CamelEvent(this, exchange);
    }
}

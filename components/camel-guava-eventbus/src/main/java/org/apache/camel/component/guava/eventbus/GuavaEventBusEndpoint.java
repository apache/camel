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
package org.apache.camel.component.guava.eventbus;

import com.google.common.eventbus.EventBus;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The guava-eventbus component provides integration bridge between Camel and Google Guava EventBus.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "guava-eventbus", title = "Guava EventBus", syntax = "guava-eventbus:eventBusRef", label = "eventbus")
public class GuavaEventBusEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {

    private EventBus eventBus;

    @UriPath
    private String eventBusRef;
    @UriParam
    private Class<?> eventClass;
    @UriParam
    private Class<?> listenerInterface;

    public GuavaEventBusEndpoint(String endpointUri, Component component, EventBus eventBus, Class<?> listenerInterface) {
        super(endpointUri, component);
        this.eventBus = eventBus;
        this.listenerInterface = listenerInterface;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new GuavaEventBusProducer(this, eventBus);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        GuavaEventBusConsumer answer = new GuavaEventBusConsumer(this, processor, eventBus, eventClass, listenerInterface);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    public Exchange createExchange(Object event) {
        Exchange exchange = createExchange();
        exchange.getIn().setBody(event);
        return exchange;
    }

    public String getEventBusRef() {
        return eventBusRef;
    }

    /**
     * To lookup the Guava EventBus from the registry with the given name
     */
    public void setEventBusRef(String eventBusRef) {
        this.eventBusRef = eventBusRef;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * To use the given Guava EventBus instance
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public Class<?> getEventClass() {
        return eventClass;
    }

    /**
     * If used on the consumer side of the route, will filter events received from the EventBus to the instances of
     * the class and superclasses of eventClass. Null value of this option is equal to setting it to the java.lang.Object
     * i.e. the consumer will capture all messages incoming to the event bus. This option cannot be used together
     * with listenerInterface option.
     */
    public void setEventClass(Class<?> eventClass) {
        this.eventClass = eventClass;
    }

    public Class<?> getListenerInterface() {
        return listenerInterface;
    }

    /**
     * The interface with method(s) marked with the @Subscribe annotation.
     * Dynamic proxy will be created over the interface so it could be registered as the EventBus listener.
     * Particularly useful when creating multi-event listeners and for handling DeadEvent properly. This option cannot be used together with eventClass option.
     */
    public void setListenerInterface(Class<?> listenerInterface) {
        this.listenerInterface = listenerInterface;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (eventBusRef != null && eventBus == null) {
            eventBus = CamelContextHelper.mandatoryLookup(getCamelContext(), eventBusRef, EventBus.class);
        }
    }
}

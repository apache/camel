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
package org.apache.camel.component.guava.eventbus;

import java.util.Map;

import com.google.common.eventbus.EventBus;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * Camel component for Guava EventBus
 * (http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/eventbus/EventBus.html). Supports both
 * producer and consumer endpoints.
 */
public class GuavaEventBusComponent extends UriEndpointComponent {

    private EventBus eventBus;
    private Class<?> listenerInterface;

    public GuavaEventBusComponent() {
        super(GuavaEventBusEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        GuavaEventBusEndpoint answer = new GuavaEventBusEndpoint(uri, this, eventBus, listenerInterface);
        answer.setEventBusRef(remaining);
        setProperties(answer, parameters);
        return answer;
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

}
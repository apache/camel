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
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.CamelContextHelper;

/**
 * Camel component for Guava EventBus
 * (http://docs.guava-libraries.googlecode.com/git/javadoc/com/google/common/eventbus/EventBus.html). Supports both
 * producer and consumer endpoints.
 */
public class GuavaEventBusComponent extends DefaultComponent {

    private EventBus eventBus;
    private Class<?> listenerInterface;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        EventBus resolvedEventBus = eventBus;
        if (resolvedEventBus == null) {
            resolvedEventBus = CamelContextHelper.mandatoryLookup(getCamelContext(), remaining, EventBus.class);
        }

        return new GuavaEventBusEndpoint(uri, this, resolvedEventBus, listenerInterface);
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public Class<?> getListenerInterface() {
        return listenerInterface;
    }

    public void setListenerInterface(Class<?> listenerInterface) {
        this.listenerInterface = listenerInterface;
    }

}
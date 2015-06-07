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

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.impl.UriEndpointComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The <a href="http://camel.apache.org/event.html">Event Component</a> is for working with Spring ApplicationEvents.
 * 
 * @version 
 */
public class EventComponent extends UriEndpointComponent implements ApplicationContextAware {
    private static final Logger LOG = LoggerFactory.getLogger(EventComponent.class);
    private ApplicationContext applicationContext;
    private final Set<EventEndpoint> endpoints = new LinkedHashSet<EventEndpoint>();

    public EventComponent() {
        super(EventEndpoint.class);
    }

    public EventComponent(ApplicationContext applicationContext) {
        super(EventEndpoint.class);
        setApplicationContext(applicationContext);
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * The Spring ApplicationContext
     */
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ConfigurableApplicationContext getConfigurableApplicationContext() {
        ApplicationContext applicationContext = getApplicationContext();
        if (applicationContext instanceof ConfigurableApplicationContext) {
            return (ConfigurableApplicationContext)applicationContext;
        } else {
            throw new IllegalArgumentException("Class: " + applicationContext.getClass().getName() + " is not an instanceof ConfigurableApplicationContext.");
        }
    }

    protected EventEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        EventEndpoint answer = new EventEndpoint(uri, this, remaining);
        setProperties(answer, parameters);
        return answer;
    }

    protected void consumerStarted(EventEndpoint endpoint) {
        endpoints.add(endpoint);
    }

    protected void consumerStopped(EventEndpoint endpoint) {
        endpoints.remove(endpoint);
    }

    public void onApplicationEvent(ApplicationEvent event) {
        // broadcast to the endpoints in use
        for (EventEndpoint endpoint : endpoints) {
            try {
                endpoint.onApplicationEvent(event);
            } catch (Exception e) {
                LOG.warn("Error on application event " + event + ". This exception will be ignored.", e);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        endpoints.clear();
        super.doStop();
    }
}

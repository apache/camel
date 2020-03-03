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
package org.apache.camel.component.directvm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * The <a href="http://camel.apache.org/direct-vm.html">Direct VM Component</a> manages {@link DirectVmEndpoint} and holds the list of named direct-vm endpoints.
 */
@Component("direct-vm")
public class DirectVmComponent extends DefaultComponent {

    private static final AtomicInteger START_COUNTER = new AtomicInteger();

    // must keep a map of consumers on the component to ensure endpoints can lookup old consumers
    // later in case the DirectVmEndpoint was re-created due the old was evicted from the endpoints LRUCache
    // on DefaultCamelContext
    private static final ConcurrentMap<String, DirectVmConsumer> CONSUMERS = new ConcurrentHashMap<>();
    @Metadata(label = "producer", defaultValue = "true")
    private boolean block = true;
    @Metadata(label = "producer", defaultValue = "30000")
    private long timeout = 30000L;
    @Metadata(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy;
    @Metadata(label = "advanced", defaultValue = "true")
    private boolean propagateProperties = true;

    public DirectVmComponent() {
    }

    /**
     * Gets all the consumer endpoints.
     *
     * @return consumer endpoints
     */
    public static Collection<Endpoint> getConsumerEndpoints() {
        Collection<Endpoint> endpoints = new ArrayList<>(CONSUMERS.size());
        for (DirectVmConsumer consumer : CONSUMERS.values()) {
            endpoints.add(consumer.getEndpoint());
        }
        return endpoints;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DirectVmEndpoint answer = new DirectVmEndpoint(uri, this);
        answer.setBlock(block);
        answer.setTimeout(timeout);
        answer.setPropagateProperties(propagateProperties);
        setProperties(answer, parameters);
        return answer;
    }

    public DirectVmConsumer getConsumer(DirectVmEndpoint endpoint) {
        String key = getConsumerKey(endpoint.getEndpointUri());
        return CONSUMERS.get(key);
    }

    public void addConsumer(DirectVmEndpoint endpoint, DirectVmConsumer consumer) {
        String key = getConsumerKey(endpoint.getEndpointUri());
        DirectVmConsumer existing = CONSUMERS.putIfAbsent(key, consumer);
        if (existing != null) {
            String contextId = existing.getEndpoint().getCamelContext().getName();
            throw new IllegalStateException("A consumer " + existing + " already exists from CamelContext: " + contextId + ". Multiple consumers not supported");
        }
    }

    public void removeConsumer(DirectVmEndpoint endpoint, DirectVmConsumer consumer) {
        String key = getConsumerKey(endpoint.getEndpointUri());
        CONSUMERS.remove(key);
    }

    private static String getConsumerKey(String uri) {
        if (uri.contains("?")) {
            // strip parameters
            uri = uri.substring(0, uri.indexOf('?'));
        }
        return uri;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        START_COUNTER.incrementAndGet();
    }

    @Override
    protected void doStop() throws Exception {
        if (START_COUNTER.decrementAndGet() <= 0) {
            // clear queues when no more direct-vm components in use
            CONSUMERS.clear();
        }
        super.doStop();
    }

    public boolean isBlock() {
        return block;
    }

    /**
     * If sending a message to a direct endpoint which has no active consumer,
     * then we can tell the producer to block and wait for the consumer to become active.
     */
    public void setBlock(boolean block) {
        this.block = block;
    }

    public long getTimeout() {
        return timeout;
    }

    /**
     * The timeout value to use if block is enabled.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * Sets a {@link HeaderFilterStrategy} that will only be applied on producer endpoints (on both directions: request and response).
     * <p>Default value: none.</p>
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public boolean isPropagateProperties() {
        return propagateProperties;
    }

    /**
     * Whether to propagate or not properties from the producer side to the consumer side, and vice versa.
     * <p>Default value: true.</p>
     */
    public void setPropagateProperties(boolean propagateProperties) {
        this.propagateProperties = propagateProperties;
    }

}

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
package org.apache.camel.component.direct;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StopWatch;

/**
 * The <a href="http://camel.apache.org/direct.html">Direct Component</a> manages {@link DirectEndpoint} and holds the
 * list of named direct endpoints.
 */
@Component("direct")
public class DirectComponent extends DefaultComponent {

    // active consumers
    private final Map<String, DirectConsumer> consumers = new HashMap<>();
    // counter that is used for producers to keep track if any consumer was added/removed since they last checked
    // this is used for optimization to avoid each producer to get consumer for each message processed
    // (locking via synchronized, and then lookup in the map as the cost)
    // consumers and producers are only added/removed during startup/shutdown or if routes is manually controlled
    private volatile int stateCounter;

    @Metadata(label = "producer", defaultValue = "true")
    private boolean block = true;
    @Metadata(label = "producer", defaultValue = "30000")
    private long timeout = 30000L;

    public DirectComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DirectEndpoint endpoint = new DirectEndpoint(uri, this);
        endpoint.setBlock(block);
        endpoint.setTimeout(timeout);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(consumers);
        consumers.clear();
        super.doShutdown();
    }

    public boolean isBlock() {
        return block;
    }

    /**
     * If sending a message to a direct endpoint which has no active consumer, then we can tell the producer to block
     * and wait for the consumer to become active.
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

    int getStateCounter() {
        return stateCounter;
    }

    public void addConsumer(String key, DirectConsumer consumer) {
        synchronized (consumers) {
            if (consumers.putIfAbsent(key, consumer) != null) {
                throw new IllegalArgumentException(
                        "Cannot add a 2nd consumer to the same endpoint: " + key
                                                   + ". DirectEndpoint only allows one consumer.");
            }
            // state changed so inc counter
            stateCounter++;
            consumers.notifyAll();
        }
    }

    public void removeConsumer(String key, DirectConsumer consumer) {
        synchronized (consumers) {
            consumers.remove(key, consumer);
            // state changed so inc counter
            stateCounter++;
            consumers.notifyAll();
        }
    }

    protected DirectConsumer getConsumer(String key, boolean block, long timeout) throws InterruptedException {
        synchronized (consumers) {
            DirectConsumer answer = consumers.get(key);
            if (answer == null && block) {
                StopWatch watch = new StopWatch();
                for (;;) {
                    answer = consumers.get(key);
                    if (answer != null) {
                        break;
                    }
                    long rem = timeout - watch.taken();
                    if (rem <= 0) {
                        break;
                    }
                    consumers.wait(rem);
                }
            }
            return answer;
        }
    }

}

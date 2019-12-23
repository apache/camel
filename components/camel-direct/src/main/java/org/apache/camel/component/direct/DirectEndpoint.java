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

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;

/**
 * The direct component provides direct, synchronous call to another endpoint from the same CamelContext.
 *
 * This endpoint can be used to connect existing routes in the same CamelContext.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = "direct", title = "Direct", syntax = "direct:name", label = "core,endpoint")
public class DirectEndpoint extends DefaultEndpoint {

    private final Map<String, DirectConsumer> consumers;

    @UriPath(description = "Name of direct endpoint") @Metadata(required = true)
    private String name;

    @UriParam(label = "producer", defaultValue = "true")
    private boolean block = true;
    @UriParam(label = "producer", defaultValue = "30000")
    private long timeout = 30000L;
    @UriParam(label = "producer")
    private boolean failIfNoConsumers = true;

    public DirectEndpoint() {
        this.consumers = new HashMap<>();
    }

    public DirectEndpoint(String endpointUri, Component component) {
        this(endpointUri, component, new HashMap<>());
    }

    public DirectEndpoint(String uri, Component component, Map<String, DirectConsumer> consumers) {
        super(uri, component);
        this.consumers = consumers;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DirectProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new DirectConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public void addConsumer(DirectConsumer consumer) {
        String key = getKey();
        synchronized (consumers) {
            if (consumers.putIfAbsent(key, consumer) != null) {
                throw new IllegalArgumentException("Cannot add a 2nd consumer to the same endpoint. Endpoint " + this + " only allows one consumer.");
            }
            consumers.notifyAll();
        }
    }

    public void removeConsumer(DirectConsumer consumer) {
        String key = getKey();
        synchronized (consumers) {
            consumers.remove(key, consumer);
            consumers.notifyAll();
        }
    }

    protected DirectConsumer getConsumer() throws InterruptedException {
        String key = getKey();
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
//            if (answer != null && answer.getEndpoint() != this) {
//                throw new IllegalStateException();
//            }
            return answer;
        }
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
     *
     * @param timeout the timeout value
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isFailIfNoConsumers() {
        return failIfNoConsumers;
    }

    /**
     * Whether the producer should fail by throwing an exception, when sending to a DIRECT endpoint with no active consumers.
     */
    public void setFailIfNoConsumers(boolean failIfNoConsumers) {
        this.failIfNoConsumers = failIfNoConsumers;
    }

    protected String getKey() {
        String uri = getEndpointUri();
        if (uri.indexOf('?') != -1) {
            return StringHelper.before(uri, "?");
        } else {
            return uri;
        }
    }
}

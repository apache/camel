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
package org.apache.camel.component.direct;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents a direct endpoint that synchronously invokes the consumer of the
 * endpoint when a producer sends a message to it.
 *
 * @version 
 */
@UriEndpoint(scheme = "direct", consumerClass = DirectConsumer.class, label = "core,endpoint")
public class DirectEndpoint extends DefaultEndpoint {

    @UriPath(description = "Name of direct endpoint")
    private String name;
    private volatile Map<String, DirectConsumer> consumers;
    @UriParam(defaultValue = "false")
    private boolean block;
    @UriParam(defaultValue = "30000")
    private long timeout = 30000L;

    public DirectEndpoint() {
        this.consumers = new HashMap<String, DirectConsumer>();
    }

    public DirectEndpoint(String endpointUri, Component component) {
        this(endpointUri, component, new HashMap<String, DirectConsumer>());
    }

    public DirectEndpoint(String uri, Component component, Map<String, DirectConsumer> consumers) {
        super(uri, component);
        this.consumers = consumers;
    }

    public Producer createProducer() throws Exception {
        if (block) {
            return new DirectBlockingProducer(this);
        } else {
            return new DirectProducer(this);
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new DirectConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public boolean isSingleton() {
        return true;
    }

    public void addConsumer(DirectConsumer consumer) {
        String key = consumer.getEndpoint().getKey();
        consumers.put(key, consumer);
    }

    public void removeConsumer(DirectConsumer consumer) {
        String key = consumer.getEndpoint().getKey();
        consumers.remove(key);
    }

    public boolean hasConsumer(DirectConsumer consumer) {
        String key = consumer.getEndpoint().getKey();
        return consumers.containsKey(key);
    }

    public DirectConsumer getConsumer() {
        String key = getKey();
        return consumers.get(key);
    }

    /**
     * If sending a message to a direct endpoint which has no active consumer,
     * then we can tell the producer to block and wait for the consumer to become active.
     * <p/>
     * Is by default <tt>false</tt>.
     */
    public boolean isBlock() {
        return block;
    }

    /**
     * If sending a message to a direct endpoint which has no active consumer,
     * then we can tell the producer to block and wait for the consumer to become active.
     * <p/>
     * Is by default <tt>false</tt>.
     *
     * @param block whether to block
     */
    public void setBlock(boolean block) {
        this.block = block;
    }

    /**
     * The timeout value to use if block is enabled.
     * <p/>
     * Is by default <tt>30000</tt>.
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * The timeout value to use if block is enabled.
     * <p/>
     * Is by default <tt>30000</tt>.
     *
     * @param timeout the timeout value
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    protected String getKey() {
        String uri = getEndpointUri();
        if (uri.indexOf('?') != -1) {
            return ObjectHelper.before(uri, "?");
        } else {
            return uri;
        }
    }
}

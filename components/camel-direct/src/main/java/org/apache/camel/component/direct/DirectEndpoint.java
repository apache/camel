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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.StringHelper;

/**
 * Call another endpoint from the same Camel Context synchronously.
 *
 * This endpoint can be used to connect existing routes in the same CamelContext.
 */
@UriEndpoint(firstVersion = "1.0.0", scheme = "direct", title = "Direct", syntax = "direct:name",
             category = { Category.CORE, Category.MESSAGING })
public class DirectEndpoint extends DefaultEndpoint {

    private final DirectComponent component;
    private final String key;

    @UriPath(description = "Name of direct endpoint")
    @Metadata(required = true)
    private String name;
    @UriParam(label = "advanced")
    private boolean synchronous;

    @UriParam(label = "producer", defaultValue = "true")
    private boolean block = true;
    @UriParam(label = "producer", defaultValue = "30000")
    private long timeout = 30000L;
    @UriParam(label = "producer", defaultValue = "true")
    private boolean failIfNoConsumers = true;

    public DirectEndpoint(String uri, DirectComponent component) {
        super(uri, component);
        this.component = component;
        if (uri.indexOf('?') != -1) {
            this.key = StringHelper.before(uri, "?");
        } else {
            this.key = uri;
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DirectProducer(this, key);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new DirectConsumer(this, processor, key);
        configureConsumer(answer);
        return answer;
    }

    @Deprecated
    public DirectConsumer getConsumer() throws InterruptedException {
        return component.getConsumer(key, block, timeout);
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Whether synchronous processing is forced.
     *
     * If enabled then the producer thread, will be forced to wait until the message has been completed before the same
     * thread will continue processing.
     *
     * If disabled (default) then the producer thread may be freed and can do other work while the message is continued
     * processed by other threads (reactive).
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
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

    public boolean isFailIfNoConsumers() {
        return failIfNoConsumers;
    }

    /**
     * Whether the producer should fail by throwing an exception, when sending to a DIRECT endpoint with no active
     * consumers.
     */
    public void setFailIfNoConsumers(boolean failIfNoConsumers) {
        this.failIfNoConsumers = failIfNoConsumers;
    }

}

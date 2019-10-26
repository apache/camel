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

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The direct-vm component provides direct, synchronous call to another endpoint from any CamelContext in the same JVM.
 *
 * This endpoint can be used to connect existing routes in the same JVM between different CamelContexts.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = "direct-vm", title = "Direct VM", syntax = "direct-vm:name", label = "core,endpoint")
public class DirectVmEndpoint extends DefaultEndpoint implements AsyncEndpoint {

    @UriPath(description = "Name of direct-vm endpoint") @Metadata(required = true)
    private String name;

    @UriParam(label = "producer", defaultValue = "true")
    private boolean block = true;
    @UriParam(label = "producer", defaultValue = "30000")
    private long timeout = 30000L;
    @UriParam(label = "producer")
    private boolean failIfNoConsumers = true;
    @UriParam(label = "producer,advanced")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam(label = "advanced", defaultValue = "true")
    private boolean propagateProperties = true;

    public DirectVmEndpoint(String endpointUri, DirectVmComponent component) {
        super(endpointUri, component);
    }

    @Override
    public DirectVmComponent getComponent() {
        return (DirectVmComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        if (block) {
            return new DirectVmBlockingProducer(this);
        } else {
            return new DirectVmProducer(this);
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        Consumer answer = new DirectVmConsumer(this, new DirectVmProcessor(processor, this));
        configureConsumer(answer);
        return answer;
    }

    public DirectVmConsumer getConsumer() {
        return getComponent().getConsumer(this);
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

    public boolean isFailIfNoConsumers() {
        return failIfNoConsumers;
    }

    /**
     * Whether the producer should fail by throwing an exception, when sending to a Direct-VM endpoint with no active consumers.
     */
    public void setFailIfNoConsumers(boolean failIfNoConsumers) {
        this.failIfNoConsumers = failIfNoConsumers;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy == null ? getComponent().getHeaderFilterStrategy() : headerFilterStrategy;
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

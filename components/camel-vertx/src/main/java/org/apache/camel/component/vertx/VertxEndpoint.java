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
package org.apache.camel.component.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * The vertx component is used for sending and receive messages from a vertx event bus.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "vertx", title = "Vert.x", syntax = "vertx:address", label = "eventbus,reactive")
public class VertxEndpoint extends DefaultEndpoint implements AsyncEndpoint, MultipleConsumersSupport {

    @UriPath @Metadata(required = true)
    private String address;
    @UriParam
    private Boolean pubSub;

    public VertxEndpoint(String uri, VertxComponent component, String address) {
        super(uri, component);
        this.address = address;
    }

    @Override
    public VertxComponent getComponent() {
        return (VertxComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new VertxProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        VertxConsumer consumer = new VertxConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    public EventBus getEventBus() {
        if (getVertx() != null) {
            return getVertx().eventBus();
        } else {
            return null;
        }
    }

    public Vertx getVertx() {
        return getComponent().getVertx();
    }

    public String getAddress() {
        return address;
    }

    /**
     * Sets the event bus address used to communicate
     */
    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isPubSub() {
        return pubSub != null && pubSub;
    }

    public Boolean getPubSub() {
        return pubSub;
    }

    /**
     * Whether to use publish/subscribe instead of point to point when sending to a vertx endpoint.
     */
    public void setPubSub(Boolean pubSub) {
        this.pubSub = pubSub;
    }

}

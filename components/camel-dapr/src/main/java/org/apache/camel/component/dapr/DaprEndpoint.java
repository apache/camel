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
package org.apache.camel.component.dapr;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import io.dapr.client.DaprPreviewClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.dapr.consumer.DaprConfigurationConsumer;
import org.apache.camel.component.dapr.consumer.DaprPubSubConsumer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Dapr component which interfaces with Dapr Building Blocks.
 */
@UriEndpoint(firstVersion = "4.12.0", scheme = "dapr", title = "Dapr", syntax = "dapr:operation", category = {
        Category.CLOUD, Category.SAAS }, headersClass = DaprConstants.class)
public class DaprEndpoint extends DefaultEndpoint {

    @UriParam
    private DaprConfiguration configuration;
    private DaprClient client;
    private DaprPreviewClient previewClient;

    public DaprEndpoint(String uri, DaprComponent component, final DaprConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DaprProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        DaprOperation operation = configuration.getOperation();

        switch (operation) {
            case pubSub:
                return new DaprPubSubConsumer(this, processor);
            case configuration:
                return new DaprConfigurationConsumer(this, processor);
            default:
                throw new IllegalArgumentException("Cannot create Dapr consumer with type " + operation);
        }

    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        client = configuration.getClient() != null ? configuration.getClient() : new DaprClientBuilder().build();
        previewClient = configuration.getPreviewClient() != null
                ? configuration.getPreviewClient() : new DaprClientBuilder().buildPreviewClient();
    }

    /**
     * The endpoint configurations
     */
    public DaprConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(DaprConfiguration config) {
        this.configuration = config;
    }

    /**
     * The DaprClient
     */
    public DaprClient getClient() {
        return client;
    }

    /**
     * The DaprPreviewClient
     */
    public DaprPreviewClient getPreviewClient() {
        return previewClient;
    }
}

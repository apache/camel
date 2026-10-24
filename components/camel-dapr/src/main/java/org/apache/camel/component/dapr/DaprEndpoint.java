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
import io.dapr.workflows.client.DaprWorkflowClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.dapr.consumer.DaprConfigurationConsumer;
import org.apache.camel.component.dapr.consumer.DaprPubSubConsumer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dapr component which interfaces with Dapr Building Blocks.
 */
@UriEndpoint(firstVersion = "4.12.0", scheme = "dapr", title = "Dapr", syntax = "dapr:operation", category = {
        Category.CLOUD, Category.SAAS }, headersClass = DaprConstants.class)
@BodyTypes(consumerOut = @BodyType(value = { Carrier.BYTES, Carrier.MAP },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "pubSub", carriers = Carrier.BYTES,
                                                          javaType = "byte[]"),
                                           @BodyType.When(option = "operation", value = "configuration",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<java.lang.String, java.lang.String>") },
                                   description = "For pubSub the data of the cloud event as bytes, with its id, type, source and"
                                                 + " content type in headers. For configuration a Map of the changed keys and"
                                                 + " their values, with the ConfigurationItems in the CamelDaprRawConfigResponse"
                                                 + " header"),
           producerIn = @BodyType(value = {
                   Carrier.TEXT, Carrier.BYTES, Carrier.VALUE, Carrier.MAP, Carrier.LIST, Carrier.POJO, Carrier.NULL },
                                  description = "The data of invokeService, pubSub, invokeBinding, state save and the workflow"
                                                + " input, event and output, given to the Dapr client as is: bytes are sent as"
                                                + " they are, other bodies are serialized as JSON (text as a JSON string), and a"
                                                + " stream fails: convert it to bytes first. State save needs a body; the other"
                                                + " operations do not read it"),
           producerOut = @BodyType(value = {
                   Carrier.BYTES, Carrier.POJO, Carrier.LIST, Carrier.MAP, Carrier.VALUE, Carrier.UNCHANGED, Carrier.NULL },
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "invokeService",
                                                          carriers = { Carrier.BYTES, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "invokeBinding",
                                                          carriers = { Carrier.BYTES, Carrier.NULL }),
                                           @BodyType.When(option = "operation", value = "pubSub", carriers = Carrier.POJO,
                                                          javaType = "io.dapr.client.domain.PublishEventRequest"),
                                           @BodyType.When(option = "operation", value = "state",
                                                          carriers = { Carrier.POJO, Carrier.LIST }),
                                           @BodyType.When(option = "operation", value = "secret", carriers = Carrier.MAP),
                                           @BodyType.When(option = "operation", value = "configuration",
                                                          carriers = Carrier.MAP,
                                                          javaType = "java.util.Map<java.lang.String, java.lang.String>"),
                                           @BodyType.When(option = "operation", value = "lock", carriers = Carrier.VALUE),
                                           @BodyType.When(option = "operation", value = "workflow",
                                                          carriers = { Carrier.POJO, Carrier.UNCHANGED, Carrier.NULL }) },
                                   description = "The response as bytes for invokeService and invokeBinding (null when there"
                                                 + " is none), the PublishEventRequest for pubSub, the State for state get and"
                                                 + " save, a List of State for getBulk and the request for the other state"
                                                 + " operations, a Map for secret and configuration, true or false for tryLock"
                                                 + " and the UnlockResponseStatus for unlock. For workflow the"
                                                 + " NewWorkflowOptions for scheduleNew, the WorkflowState for state and the"
                                                 + " waits, unchanged for terminate and raiseEvent, and null otherwise"))
public class DaprEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(DaprEndpoint.class);

    @UriParam
    private DaprConfiguration configuration;
    @UriParam(label = "advanced",
              description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;
    private DaprClient client;
    private DaprPreviewClient previewClient;
    private DaprWorkflowClient workflowClient;

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
        workflowClient
                = configuration.getWorkflowClient() != null ? configuration.getWorkflowClient() : new DaprWorkflowClient();
    }

    @Override
    public void doStop() throws Exception {
        // close only the clients that this endpoint created: a configured (or autowired) client can be shared with
        // other endpoints and is closed by its owner. The clients are created again when the endpoint is started again
        if (client != configuration.getClient()) {
            close(client);
        }
        if (previewClient != configuration.getPreviewClient()) {
            close(previewClient);
        }
        if (workflowClient != configuration.getWorkflowClient()) {
            close(workflowClient);
        }
        client = null;
        previewClient = null;
        workflowClient = null;
        super.doStop();
    }

    private static void close(AutoCloseable daprClient) {
        if (daprClient == null) {
            return;
        }
        try {
            daprClient.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.debug("Failed to close {} due to: {}", daprClient, e.getMessage(), e);
        }
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

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new DaprHeaderFilterStrategy();
        }
        return headerFilterStrategy;
    }

    /**
     * To use a custom {@link org.apache.camel.spi.HeaderFilterStrategy} to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
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

    /**
     * The DaprWorkflowClient
     */
    public DaprWorkflowClient getWorkflowClient() {
        return workflowClient;
    }
}

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
package org.apache.camel.component.azure.eventhubs.integration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.models.EventPosition;
import com.azure.messaging.eventhubs.models.SendOptions;
import com.azure.storage.blob.BlobContainerAsyncClient;
import org.apache.camel.component.azure.eventhubs.EventHubsConfiguration;
import org.apache.camel.component.azure.eventhubs.TestUtils;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "connectionString", matches = ".*",
                         disabledReason = "Make sure to supply azure eventHubs connectionString, e.g:  mvn verify -DconnectionString=string -DblobAccountName=blob -DblobAccessKey=key")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EventProcessorIT {

    private EventHubsConfiguration configuration;
    private BlobContainerAsyncClient containerAsyncClient;

    @BeforeAll
    public void prepare() throws Exception {
        final Properties properties = TestUtils.loadAzureAccessFromJvmEnv();
        final String containerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        configuration = new EventHubsConfiguration();
        configuration.setConnectionString(properties.getProperty("connectionString"));
        configuration.setConsumerGroupName(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME);
        configuration.setBlobAccessKey(properties.getProperty("blobAccessKey"));
        configuration.setBlobAccountName(properties.getProperty("blobAccountName"));

        Map<String, EventPosition> positionMap = new HashMap<>();
        positionMap.put("0", EventPosition.earliest());

        configuration.setEventPosition(positionMap);
        configuration.setBlobContainerName(containerName);

        containerAsyncClient = EventHubsClientFactory.createBlobContainerClient(configuration);

        // create test container
        containerAsyncClient.create().block();
    }

    @Test
    public void testEventProcessingWithBlobCheckpointStore() {
        final AtomicBoolean doneAsync = new AtomicBoolean();
        final EventHubProducerAsyncClient producerAsyncClient
                = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);
        final Consumer<EventContext> onEvent = eventContext -> {
            final String body = eventContext.getEventData().getBodyAsString();
            if (eventContext.getPartitionContext().getPartitionId().equals("0")
                    && body.contains("Testing Event Consumer With BlobStore")) {
                assertTrue(true);
                doneAsync.set(true);
            }
        };
        final Consumer<ErrorContext> onError = errorContext -> {
        };
        final EventProcessorClient processorClient
                = EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError);

        processorClient.start();

        producerAsyncClient.send(Collections.singletonList(new EventData("Testing Event Consumer With BlobStore")),
                new SendOptions().setPartitionId("0")).block();

        Awaitility.await()
                .timeout(30, TimeUnit.SECONDS)
                .untilTrue(doneAsync);

        processorClient.stop();
        producerAsyncClient.close();
    }

    @AfterAll
    public void tearDown() {
        containerAsyncClient.delete().block();
    }
}

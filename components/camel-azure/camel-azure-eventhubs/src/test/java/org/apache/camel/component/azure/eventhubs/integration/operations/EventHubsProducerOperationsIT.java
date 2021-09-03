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
package org.apache.camel.component.azure.eventhubs.integration.operations;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerAsyncClient;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.models.EventPosition;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.eventhubs.EventHubsConfiguration;
import org.apache.camel.component.azure.eventhubs.EventHubsConstants;
import org.apache.camel.component.azure.eventhubs.TestUtils;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.component.azure.eventhubs.operations.EventHubsProducerOperations;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "connectionString", matches = ".*",
                         disabledReason = "Make sure to supply azure eventHubs connectionString, e.g:  mvn verify -DconnectionString=string -DblobAccountName=blob -DblobAccessKey=key")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventHubsProducerOperationsIT extends CamelTestSupport {

    private EventHubsConfiguration configuration;
    private EventHubProducerAsyncClient producerAsyncClient;
    private EventHubConsumerAsyncClient consumerAsyncClient;

    @BeforeAll
    public void prepare() throws Exception {
        final Properties properties = TestUtils.loadAzureAccessFromJvmEnv();

        configuration = new EventHubsConfiguration();
        configuration.setConnectionString(properties.getProperty("connectionString"));
        configuration.setConsumerGroupName(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME);

        producerAsyncClient = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);
        consumerAsyncClient = EventHubsClientFactory.createEventHubConsumerAsyncClient(configuration);
    }

    @Test
    public void testSendEventWithSpecificPartition() {
        final EventHubsProducerOperations operations = new EventHubsProducerOperations(producerAsyncClient, configuration);
        final String firstPartition = producerAsyncClient.getPartitionIds().blockLast();
        final Exchange exchange = new DefaultExchange(context);

        exchange.getIn().setHeader(EventHubsConstants.PARTITION_ID, firstPartition);
        exchange.getIn().setBody("test should be in firstPartition");

        operations.sendEvents(exchange, doneSync -> {
        });

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollDelay(Duration.ofSeconds(2))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> {
                    final Boolean eventExists = consumerAsyncClient
                            .receiveFromPartition(firstPartition, EventPosition.earliest())
                            .any(partitionEvent -> partitionEvent.getPartitionContext().getPartitionId().equals(firstPartition)
                                    && partitionEvent.getData().getBodyAsString()
                                            .contains("test should be in firstPartition"))
                            .block();

                    if (eventExists == null) {
                        return false;
                    }

                    return eventExists;
                });
    }

    @Test
    public void testIterableExchangesSendEventsWithSpecificPartition() {
        final EventHubsProducerOperations operations = new EventHubsProducerOperations(producerAsyncClient, configuration);
        final String firstPartition = producerAsyncClient.getPartitionIds().blockLast();

        final Exchange exchange1 = new DefaultExchange(context);
        final Exchange exchange2 = new DefaultExchange(context);

        exchange1.getIn().setBody("Exchange Message 1");
        exchange2.getIn().setBody("Exchange Message 2");

        final List<Exchange> exchanges = new LinkedList<>();
        exchanges.add(exchange1);
        exchanges.add(exchange2);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(exchanges);

        operations.sendEvents(exchange, doneSync -> {
        });

        Awaitility.await()
                .atMost(40, TimeUnit.SECONDS)
                .pollDelay(Duration.ofSeconds(2))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> {
                    final Boolean event1Exists = consumerAsyncClient
                            .receiveFromPartition(firstPartition, EventPosition.earliest())
                            .any(partitionEvent -> partitionEvent.getPartitionContext().getPartitionId().equals(firstPartition)
                                    && partitionEvent.getData().getBodyAsString()
                                            .contains("Exchange Message 1"))
                            .block();

                    final Boolean event2Exists = consumerAsyncClient
                            .receiveFromPartition(firstPartition, EventPosition.earliest())
                            .any(partitionEvent -> partitionEvent.getPartitionContext().getPartitionId().equals(firstPartition)
                                    && partitionEvent.getData().getBodyAsString()
                                            .contains("Exchange Message 2"))
                            .block();

                    if (event1Exists == null || event2Exists == null) {
                        return false;
                    }

                    return event1Exists && event2Exists;
                });
    }

    @Test
    public void testIterableStringSendEventsWithSpecificPartition() {
        final EventHubsProducerOperations operations = new EventHubsProducerOperations(producerAsyncClient, configuration);
        final String firstPartition = producerAsyncClient.getPartitionIds().blockLast();

        final List<String> messages = new LinkedList<>();
        messages.add("Test String Message 1");
        messages.add("Test String Message 2");

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(messages);

        operations.sendEvents(exchange, doneSync -> {
        });

        Awaitility.await()
                .atMost(40, TimeUnit.SECONDS)
                .pollDelay(Duration.ofSeconds(2))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> {
                    final Boolean event1Exists = consumerAsyncClient
                            .receiveFromPartition(firstPartition, EventPosition.earliest())
                            .any(partitionEvent -> partitionEvent.getPartitionContext().getPartitionId().equals(firstPartition)
                                    && partitionEvent.getData().getBodyAsString()
                                            .contains("Test String Message 1"))
                            .block();

                    final Boolean event2Exists = consumerAsyncClient
                            .receiveFromPartition(firstPartition, EventPosition.earliest())
                            .any(partitionEvent -> partitionEvent.getPartitionContext().getPartitionId().equals(firstPartition)
                                    && partitionEvent.getData().getBodyAsString()
                                            .contains("Test String Message 2"))
                            .block();

                    if (event1Exists == null || event2Exists == null) {
                        return false;
                    }

                    return event1Exists && event2Exists;
                });
    }

    @AfterAll
    public void tearDown() {
        producerAsyncClient.close();
        consumerAsyncClient.close();
    }
}

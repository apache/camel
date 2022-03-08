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

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerAsyncClient;
import com.azure.messaging.eventhubs.models.EventPosition;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.eventhubs.EventHubsConfiguration;
import org.apache.camel.component.azure.eventhubs.EventHubsConstants;
import org.apache.camel.component.azure.eventhubs.TestUtils;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "connectionString", matches = ".*",
                         disabledReason = "Make sure to supply azure eventHubs connectionString, e.g:  mvn verify -DconnectionString=string -DblobAccountName=blob -DblobAccessKey=key")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventHubsProducerIT extends CamelTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    private EventHubConsumerAsyncClient consumerAsyncClient;

    @BeforeAll
    public void prepare() throws Exception {
        final Properties properties = TestUtils.loadAzureAccessFromJvmEnv();

        final EventHubsConfiguration configuration = new EventHubsConfiguration();
        configuration.setConnectionString(properties.getProperty(TestUtils.CONNECTION_STRING));
        configuration.setConsumerGroupName(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME);

        consumerAsyncClient = EventHubsClientFactory.createEventHubConsumerAsyncClient(configuration);
    }

    @Test
    public void testSendEventWithSpecificPartition() throws InterruptedException {

        final String messageBody = RandomStringUtils.randomAlphabetic(30);
        final String firstPartition = "0";

        final AtomicBoolean eventExists = new AtomicBoolean();

        final CompletableFuture<Exchange> resultAsync = template.asyncSend("direct:sendAsync", exchange -> {
            exchange.getIn().setHeader(EventHubsConstants.PARTITION_ID, firstPartition);
            exchange.getIn().setBody(messageBody);
        });

        resultAsync.whenComplete((exchange, throwable) -> {
            // we sent our exchange, let's check it out
            final Boolean eventFlag = consumerAsyncClient.receiveFromPartition(firstPartition, EventPosition.earliest())
                    .any(partitionEvent -> partitionEvent.getPartitionContext().getPartitionId().equals(firstPartition)
                            && partitionEvent.getData().getBodyAsString()
                                    .contains(messageBody))
                    .block();

            if (eventFlag == null) {
                eventExists.set(false);
            }

            eventExists.set(eventFlag);
        });

        result.expectedMinimumMessageCount(1);
        result.setAssertPeriod(20000);
        result.assertIsSatisfied();

        assertTrue(eventExists.get());
    }

    @Test
    public void testSendingNonValidData() throws InterruptedException {

        final String messageBody = RandomStringUtils.randomAlphabetic(30);
        final String firstPartition = "0";

        final AtomicReference<Exchange> resultExchange = new AtomicReference<>();

        final CompletableFuture<Exchange> resultAsync = template.asyncSend("direct:sendAsync", exchange -> {
            exchange.getIn().setHeader(EventHubsConstants.PARTITION_ID, firstPartition);
            exchange.getIn().setHeader(EventHubsConstants.PARTITION_KEY, "testKey");
            exchange.getIn().setBody(messageBody);
        });

        resultAsync.whenComplete((exchange, throwable) -> resultExchange.set(exchange));

        result.setAssertPeriod(100);
        result.assertIsSatisfied();

        assertNotNull(resultExchange.get());
        assertNotNull(resultExchange.get().getException());
    }

    @AfterAll
    public void tearDown() {
        consumerAsyncClient.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sendAsync")
                        .to("azure-eventhubs:?connectionString=RAW({{connectionString}})")
                        .to(result);
            }
        };
    }
}

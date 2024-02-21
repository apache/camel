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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.models.EventPosition;
import com.azure.messaging.eventhubs.models.SendOptions;
import com.azure.storage.blob.BlobContainerAsyncClient;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfSystemProperty(named = "connectionString", matches = ".*",
                         disabledReason = "Make sure to supply azure eventHubs connectionString, e.g:  mvn verify -DconnectionString=string -DblobAccountName=blob -DblobAccessKey=key")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventHubsConsumerIT extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint result;

    private String containerName;
    private BlobContainerAsyncClient containerAsyncClient;
    private EventHubsConfiguration configuration;

    @BeforeAll
    public void prepare() throws Exception {
        containerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        final Properties properties = TestUtils.loadAzureAccessFromJvmEnv();

        configuration = new EventHubsConfiguration();
        configuration.setBlobAccessKey(properties.getProperty(TestUtils.BLOB_ACCESS_KEY));
        configuration.setBlobAccountName(properties.getProperty(TestUtils.BLOB_ACCOUNT_NAME));
        configuration.setBlobContainerName(containerName);
        configuration.setConnectionString(properties.getProperty(TestUtils.CONNECTION_STRING));

        containerAsyncClient = EventHubsClientFactory.createBlobContainerClient(configuration);

        // create test container
        containerAsyncClient.create().block();
    }

    @Test
    public void testConsumerEvents() throws InterruptedException {
        // send test data
        final EventHubProducerAsyncClient producerAsyncClient
                = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);

        final String messageBody = RandomStringUtils.randomAlphabetic(30);
        final String messageKey = RandomStringUtils.randomAlphabetic(5);

        producerAsyncClient
                .send(Collections.singletonList(new EventData(messageBody)), new SendOptions().setPartitionKey(messageKey))
                .block();

        result.expectedMinimumMessageCount(1);
        result.setAssertPeriod(20000);

        final List<Exchange> exchanges = result.getExchanges();
        result.assertIsSatisfied();

        // now we check our messages
        final Exchange returnedMessage = exchanges.stream()
                .filter(Objects::nonNull)
                .filter(exchange -> exchange.getMessage().getHeader(EventHubsConstants.PARTITION_KEY)
                                    != null
                        && exchange.getMessage().getHeader(EventHubsConstants.PARTITION_KEY).equals(messageKey))
                .findFirst()
                .orElse(null);

        assertNotNull(returnedMessage);

        assertEquals(messageKey, returnedMessage.getMessage().getHeader(EventHubsConstants.PARTITION_KEY));

        assertNotNull(returnedMessage.getMessage().getBody());
        assertNotNull(returnedMessage.getMessage().getHeader(EventHubsConstants.PARTITION_ID));
        assertNotNull(returnedMessage.getMessage().getHeader(EventHubsConstants.SEQUENCE_NUMBER));
        assertNotNull(returnedMessage.getMessage().getHeader(EventHubsConstants.OFFSET));
        assertNotNull(returnedMessage.getMessage().getHeader(EventHubsConstants.ENQUEUED_TIME));
    }

    @AfterAll
    public void tearDown() {
        // delete testing container
        containerAsyncClient.delete().block();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-eventhubs:?"
                     + "connectionString=RAW({{connectionString}})"
                     + "&blobContainerName=" + containerName + "&eventPosition=#eventPosition"
                     + "&blobAccountName={{blobAccountName}}&blobAccessKey=RAW({{blobAccessKey}})")
                        .to(result);

            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final Map<String, EventPosition> positionMap = new HashMap<>();
        positionMap.put("0", EventPosition.earliest());
        positionMap.put("1", EventPosition.earliest());

        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("eventPosition", positionMap);

        return context;
    }
}

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
package org.apache.camel.component.azure.servicebus.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "connectionString", matches = ".*",
                         disabledReason = "Make sure to supply azure eventHubs connectionString, e.g:  mvn verify -DconnectionString=string -DblobAccountName=blob -DblobAccessKey=key")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceBusConsumerIT extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint result;

    /*
    private String containerName;
    private BlobContainerAsyncClient containerAsyncClient;
    private org.apache.camel.component.azure.eventhubs.ServicebusConfiguration configuration;
    
    @BeforeAll
    public void prepare() throws Exception {
        containerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();
    
        final Properties properties = ServiceBusTestUtils.loadAzureAccessFromJvmEnv();
    
        configuration = new org.apache.camel.component.azure.eventhubs.ServicebusConfiguration();
        configuration.setBlobAccessKey(properties.getProperty(ServiceBusTestUtils.BLOB_ACCESS_KEY));
        configuration.setBlobAccountName(properties.getProperty(ServiceBusTestUtils.BLOB_ACCOUNT_NAME));
        configuration.setBlobContainerName(containerName);
        configuration.setConnectionString(properties.getProperty(ServiceBusTestUtils.CONNECTION_STRING));
    
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
                .filter(exchange -> exchange.getMessage().getHeader(org.apache.camel.component.azure.eventhubs.ServiceBusConstants.PARTITION_KEY)
                                    != null
                        && exchange.getMessage().getHeader(org.apache.camel.component.azure.eventhubs.ServiceBusConstants.PARTITION_KEY).equals(messageKey))
                .findFirst()
                .orElse(null);
    
        assertNotNull(returnedMessage);
    
        assertEquals(messageKey, returnedMessage.getMessage().getHeader(org.apache.camel.component.azure.eventhubs.ServiceBusConstants.PARTITION_KEY));
    
        assertNotNull(returnedMessage.getMessage().getBody());
        assertNotNull(returnedMessage.getMessage().getHeader(org.apache.camel.component.azure.eventhubs.ServiceBusConstants.PARTITION_ID));
        assertNotNull(returnedMessage.getMessage().getHeader(org.apache.camel.component.azure.eventhubs.ServiceBusConstants.SEQUENCE_NUMBER));
        assertNotNull(returnedMessage.getMessage().getHeader(org.apache.camel.component.azure.eventhubs.ServiceBusConstants.OFFSET));
        assertNotNull(returnedMessage.getMessage().getHeader(org.apache.camel.component.azure.eventhubs.ServiceBusConstants.ENQUEUED_TIME));
    }
    
    @AfterAll
    public void tearDown() {
        // delete testing container
        containerAsyncClient.delete().block();
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
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
    
     */
}

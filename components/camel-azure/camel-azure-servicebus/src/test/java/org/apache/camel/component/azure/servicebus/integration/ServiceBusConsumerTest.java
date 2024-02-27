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

import java.util.LinkedList;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.servicebus.ServiceBusConstants;
import org.apache.camel.component.azure.servicebus.client.ServiceBusSenderAsyncClientWrapper;
import org.apache.camel.component.azure.servicebus.operations.ServiceBusSenderOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIfSystemProperty(named = "connectionString", matches = ".*",
                         disabledReason = "Make sure to supply azure servicebus connectionString, e.g:  mvn verify -DconnectionString=string")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceBusConsumerTest extends BaseCamelServiceBusTestSupport {

    @EndpointInject("mock:receiveMessagesResult")
    private MockEndpoint receiveMessagesResult;

    @EndpointInject("mock:peekMessagesResult")
    private MockEndpoint peekMessagesResult;

    @Test
    void testReceiveMessages() throws InterruptedException {
        // send test data
        final List<Object> inputBatch = new LinkedList<>();
        inputBatch.add("test batch 1");
        inputBatch.add("test batch 2");
        inputBatch.add("test batch 3");

        new ServiceBusSenderOperations(new ServiceBusSenderAsyncClientWrapper(senderAsyncClient))
                .sendMessages(inputBatch, null, null, null)
                .block();

        // test the data now
        receiveMessagesResult.expectedMessageCount(3);
        receiveMessagesResult.expectedBodiesReceived("test batch 1", "test batch 2", "test batch 3");
        receiveMessagesResult.assertIsSatisfied();

        final List<Exchange> exchanges = receiveMessagesResult.getExchanges();

        assertNotNull(exchanges.get(0).getMessage().getHeaders());
        assertNotNull(exchanges.get(1).getMessage().getHeaders());
        assertNotNull(exchanges.get(2).getMessage().getHeaders());

        // we test headers
        assertNotNull(exchanges.get(0).getMessage().getHeader(ServiceBusConstants.MESSAGE_ID));
        assertNotNull(exchanges.get(1).getMessage().getHeader(ServiceBusConstants.MESSAGE_ID));
        assertNotNull(exchanges.get(2).getMessage().getHeader(ServiceBusConstants.MESSAGE_ID));
    }

    @Test
    @Disabled
    void testPeekMessages() throws InterruptedException {
        // send test data
        final List<Object> inputBatch = new LinkedList<>();
        inputBatch.add("peek test batch 1");
        inputBatch.add("peek test batch 2");
        inputBatch.add("peek test batch 3");

        new ServiceBusSenderOperations(new ServiceBusSenderAsyncClientWrapper(senderAsyncClient))
                .sendMessages(inputBatch, null, null, null)
                .block();

        // test the data now
        peekMessagesResult.expectedMessageCount(3);
        peekMessagesResult.expectedBodiesReceived("peek test batch 1", "peek test batch 2", "peek test batch 3");
        peekMessagesResult.assertIsSatisfied();

        final List<Exchange> exchanges = peekMessagesResult.getExchanges();

        assertNotNull(exchanges.get(0).getMessage().getHeaders());
        assertNotNull(exchanges.get(1).getMessage().getHeaders());
        assertNotNull(exchanges.get(2).getMessage().getHeaders());

        // we test headers
        assertNotNull(exchanges.get(0).getMessage().getHeader(ServiceBusConstants.MESSAGE_ID));
        assertNotNull(exchanges.get(1).getMessage().getHeader(ServiceBusConstants.MESSAGE_ID));
        assertNotNull(exchanges.get(2).getMessage().getHeader(ServiceBusConstants.MESSAGE_ID));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("azure-servicebus:test//?connectionString=test").to(receiveMessagesResult);
                from("azure-servicebus:test//?connectionString=test&consumerOperation=peekMessages&peekNumMaxMessages=3")
                        .to(peekMessagesResult);
            }
        };
    }
}

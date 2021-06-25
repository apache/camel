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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.servicebus.client.ServiceBusSenderAsyncClientWrapper;
import org.apache.camel.component.azure.servicebus.operations.ServiceBusSenderOperations;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "connectionString", matches = ".*",
                         disabledReason = "Make sure to supply azure eventHubs connectionString, e.g:  mvn verify -DconnectionString=string")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceBusProducerIT extends BaseCamelServiceBusTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void testReceiveMessages() throws InterruptedException {

        final List<String> inputBatch = new LinkedList<>();
        inputBatch.add("test batch 1");
        inputBatch.add("test batch 2");
        inputBatch.add("test batch 3");

        new ServiceBusSenderOperations(new ServiceBusSenderAsyncClientWrapper(senderAsyncClient)).sendMessages(inputBatch, null)
                .block();

        final AtomicBoolean eventExists = new AtomicBoolean();

        Thread.sleep(1000);

        result.expectedMessageCount(3);

        //template.asyncSend("direct:receiveMessages", exchange -> {
        //});

        template.send("direct:receiveMessages", exchange -> {
        });

        result.setAssertPeriod(5000);
        result.assertIsSatisfied();

        /*final CompletableFuture<Exchange> resultAsync = template.asyncSend("direct:receiveMessages", exchange -> {
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
        
        assertTrue(eventExists.get());*/
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:receiveMessages").to("azure-servicebus:test//?connectionString=test").to(result);
            }
        };
    }
}

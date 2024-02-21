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

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.stream.StreamSupport;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.servicebus.ServiceBusConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "connectionString", matches = ".*",
                         disabledReason = "Make sure to supply azure servicebus connectionString, e.g:  mvn verify -DconnectionString=string")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceBusProducerTest extends BaseCamelServiceBusTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @Test
    public void testSendMessage() throws InterruptedException {
        template.send("direct:sendMessage", exchange -> {
            exchange.getIn().setBody("123456789");
            exchange.getIn().setHeader(ServiceBusConstants.APPLICATION_PROPERTIES, Map.of("customKey", "customValue"));
        });

        Thread.sleep(1000);

        // let's check our data
        final List<ServiceBusReceivedMessage> receivedMessages
                = receiverAsyncClient.receiveMessages().toStream().toList();

        final boolean batch1Exists = receivedMessages.stream()
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("123456789"));

        final boolean applicationPropertiesPresent = receivedMessages.stream()
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getApplicationProperties()
                        .containsKey("customKey"));

        assertTrue(batch1Exists, "test message body");
        assertTrue(applicationPropertiesPresent, "test message application properties");

        //test byte body
        byte[] testByteBody = "test data".getBytes(StandardCharsets.UTF_8);
        template.send("direct:sendMessage", exchange -> {
            exchange.getIn().setBody(testByteBody);
            exchange.getIn().setHeader(ServiceBusConstants.APPLICATION_PROPERTIES, Map.of("customKey", "customValue"));
        });

        Thread.sleep(1000);

        final List<ServiceBusReceivedMessage> receivedMessages2
                = receiverAsyncClient.receiveMessages().toStream().toList();

        final boolean batch2Exists = receivedMessages2.stream()
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("123456789"));

        final boolean applicationPropertiesPresent2 = receivedMessages2.stream()
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getApplicationProperties()
                        .containsKey("customKey"));

        assertTrue(batch2Exists, "test byte body");
        assertTrue(applicationPropertiesPresent2, "test byte message application properties");
    }

    @Test
    public void testSendBatchMessages() throws InterruptedException {
        template.send("direct:sendBatchMessages", exchange -> {
            final List<String> inputBatch = new LinkedList<>();
            inputBatch.add("test batch 1");
            inputBatch.add("test batch 2");
            inputBatch.add("test batch 3");
            inputBatch.add("123456");

            exchange.getIn().setBody(inputBatch);
        });

        Thread.sleep(1000);

        // let's check our data
        final Spliterator<ServiceBusReceivedMessage> receivedMessages
                = receiverAsyncClient.receiveMessages().toIterable().spliterator();

        final boolean batch1Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 1"));

        final boolean batch2Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 2"));

        final boolean batch3Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 3"));

        final boolean batch4Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("123456"));

        assertTrue(batch1Exists, "test message body 1");
        assertTrue(batch2Exists, "test message body 2");
        assertTrue(batch3Exists, "test message body 3");
        assertTrue(batch4Exists, "test message body 4");

        //test bytes
        final List<byte[]> inputBatch2 = new LinkedList<>();
        byte[] byteBody1 = "test data".getBytes(StandardCharsets.UTF_8);
        byte[] byteBody2 = "test data2".getBytes(StandardCharsets.UTF_8);
        inputBatch2.add(byteBody1);
        inputBatch2.add(byteBody2);

        template.send("direct:sendBatchMessages", exchange -> {
            exchange.getIn().setBody(inputBatch2);
        });

        Thread.sleep(1000);

        // let's check our data
        final Spliterator<ServiceBusReceivedMessage> receivedMessages2
                = receiverAsyncClient.receiveMessages().toIterable().spliterator();
        final boolean byteBody1Exists = StreamSupport.stream(receivedMessages2, false)
                .anyMatch(serviceBusReceivedMessage -> Arrays.equals(serviceBusReceivedMessage.getBody().toBytes(), byteBody1));
        final boolean byteBody2Exists = StreamSupport.stream(receivedMessages2, false)
                .anyMatch(serviceBusReceivedMessage -> Arrays.equals(serviceBusReceivedMessage.getBody().toBytes(), byteBody2));

        assertTrue(byteBody1Exists, "test byte body 1");
        assertTrue(byteBody2Exists, "test byte body 2");
    }

    @Test
    void testScheduleMessage() throws InterruptedException {
        template.send("direct:scheduleMessage", exchange -> {
            exchange.getIn().setHeader(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME, OffsetDateTime.now());
            exchange.getIn().setBody("test message");
        });

        Thread.sleep(1000);

        // let's check our data
        final Spliterator<ServiceBusReceivedMessage> receivedMessages
                = receiverAsyncClient.receiveMessages().toIterable().spliterator();

        final boolean batch1Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test message"));

        assertTrue(batch1Exists);

        //test bytes
        byte[] testByteBody = "test data".getBytes(StandardCharsets.UTF_8);
        template.send("direct:scheduleMessage", exchange -> {
            exchange.getIn().setHeader(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME, OffsetDateTime.now());
            exchange.getIn().setBody(testByteBody);
        });

        Thread.sleep(1000);

        final Spliterator<ServiceBusReceivedMessage> receivedMessages2
                = receiverAsyncClient.receiveMessages().toIterable().spliterator();

        final boolean batch2Exists = StreamSupport.stream(receivedMessages2, false)
                .anyMatch(serviceBusReceivedMessage -> Arrays.equals(serviceBusReceivedMessage.getBody().toBytes(),
                        testByteBody));

        assertTrue(batch2Exists);
    }

    @Test
    public void testScheduleBatchMessages() throws InterruptedException {
        template.send("direct:scheduleBatchMessages", exchange -> {
            final List<Object> inputBatch = new LinkedList<>();
            inputBatch.add("test batch 1");
            inputBatch.add("test batch 2");
            inputBatch.add("test batch 3");
            inputBatch.add("123456");

            exchange.getIn().setHeader(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME, OffsetDateTime.now());
            exchange.getIn().setBody(inputBatch);
        });

        Thread.sleep(1000);

        // let's check our data
        final Spliterator<ServiceBusReceivedMessage> receivedMessages
                = receiverAsyncClient.receiveMessages().toIterable().spliterator();

        final boolean batch1Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 1"));

        final boolean batch2Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 2"));

        final boolean batch3Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("test batch 3"));

        final boolean batch4Exists = StreamSupport.stream(receivedMessages, false)
                .anyMatch(serviceBusReceivedMessage -> serviceBusReceivedMessage.getBody().toString().equals("123456"));

        assertTrue(batch1Exists, "test message body 1");
        assertTrue(batch2Exists, "test message body 2");
        assertTrue(batch3Exists, "test message body 3");
        assertTrue(batch4Exists, "test message body 4");

        //test bytes
        final List<byte[]> inputBatch2 = new LinkedList<>();
        byte[] byteBody1 = "test data".getBytes(StandardCharsets.UTF_8);
        byte[] byteBody2 = "test data2".getBytes(StandardCharsets.UTF_8);
        inputBatch2.add(byteBody1);
        inputBatch2.add(byteBody2);

        template.send("direct:scheduleBatchMessages", exchange -> {
            exchange.getIn().setHeader(ServiceBusConstants.SCHEDULED_ENQUEUE_TIME, OffsetDateTime.now());
            exchange.getIn().setBody(inputBatch2);
        });

        Thread.sleep(1000);

        // let's check our data
        final Spliterator<ServiceBusReceivedMessage> receivedMessages2
                = receiverAsyncClient.receiveMessages().toIterable().spliterator();

        final boolean byteBody1Exists = StreamSupport.stream(receivedMessages2, false)
                .anyMatch(serviceBusReceivedMessage -> Arrays.equals(serviceBusReceivedMessage.getBody().toBytes(), byteBody1));

        final boolean byteBody2Exists = StreamSupport.stream(receivedMessages2, false)
                .anyMatch(serviceBusReceivedMessage -> Arrays.equals(serviceBusReceivedMessage.getBody().toBytes(), byteBody2));

        assertTrue(byteBody1Exists, "test byte message body 1");
        assertTrue(byteBody2Exists, "test byte message body 2");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sendBatchMessages").to("azure-servicebus:test//?connectionString=test").to(result);
                from("direct:sendMessage").to("azure-servicebus:test//?connectionString=test").to(result);
                from("direct:scheduleMessage")
                        .to("azure-servicebus:test//?connectionString=test&producerOperation=scheduleMessages").to(result);
                from("direct:scheduleBatchMessages")
                        .to("azure-servicebus:test//?connectionString=test&producerOperation=scheduleMessages").to(result);
            }
        };
    }
}

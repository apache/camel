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
package org.apache.camel.component.azure.storage.queue.integration;

import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.storage.queue.QueueConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StorageQueueProducerIT extends StorageQueueBase {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;
    private String resultName = "mock:result";

    @Test
    public void testCreateDeleteQueue() throws InterruptedException {

        template.send("direct:createQueue", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
        });

        result.assertIsSatisfied();

        // delete queue
        template.send("direct:deleteQueue", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
        });

        // create from name from the route
        template.send("direct:createQueue", ExchangePattern.InOnly, exchange -> {
        });
        result.assertIsSatisfied();

        // check name
        assertEquals("testqueue", serviceClient.getQueueClient("testqueue").getQueueName());

    }

    @Test
    public void testSendAndDeleteMessage() throws InterruptedException {

        // first test if queue is not created
        template.send("direct:sendMessage", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
            exchange.getIn().setBody("test-message-1");
            exchange.getIn().setHeader(QueueConstants.CREATE_QUEUE, true);
        });

        result.assertIsSatisfied();

        // queue is created because of the flag
        assertFalse(result.getExchanges().isEmpty());

        result.reset();

        // test the rest
        template.send("direct:sendMessage", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
            exchange.getIn().setBody("test-message-1");
        });

        result.assertIsSatisfied();

        final Map<String, Object> sentMessageHeaders = result.getExchanges().get(0).getMessage().getHeaders();

        result.reset();

        template.send("direct:deleteMessage", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
            exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, sentMessageHeaders.get(QueueConstants.MESSAGE_ID));
            exchange.getIn().setHeader(QueueConstants.POP_RECEIPT, sentMessageHeaders.get(QueueConstants.POP_RECEIPT));
        });

        result.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createQueue")
                        .to(componentUri("createQueue", "testqueue"))
                        .to(resultName);

                from("direct:deleteQueue")
                        .to(componentUri("deleteQueue", "testqueue"))
                        .to(resultName);

                from("direct:sendMessage")
                        .to(componentUri("sendMessage", "test"))
                        .to(resultName);

                from("direct:deleteMessage")
                        .to(componentUri("deleteMessage", "test"))
                        .to(resultName);

                from("direct:receiveMessages")
                        .to(componentUri("receiveMessages", "test"))
                        .to(resultName);
            }
        };
    }

    @Test
    public void testHeaderPreservation() {

        // first test if queue is not created
        template.send("direct:sendMessage", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
            exchange.getIn().setBody("test-message-1");
            exchange.getIn().setHeader(QueueConstants.CREATE_QUEUE, true);
            exchange.getIn().setHeader("DoNotDelete", "keep me");
        });
        assertEquals("keep me", result.getExchanges().get(0).getMessage().getHeader("DoNotDelete"));
    }

    private String componentUri(final String operation, final String queueName) {
        return String.format("azure-storage-queue://cameldev/%s?operation=%s", queueName, operation);
    }
}

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
package org.apache.camel.component.azure.storage.queue;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueMessageItem;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.storage.queue.client.QueueClientFactory;
import org.apache.camel.component.azure.storage.queue.client.QueueServiceClientWrapper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueProducerIT extends CamelTestSupport {

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint result;
    private String resultName = "mock:result";

    private QueueConfiguration configuration;
    private QueueServiceClientWrapper serviceClient;

    @BeforeAll
    public void prepare() throws Exception {
        configuration = new QueueConfiguration();
        configuration.setCredentials(storageSharedKeyCredential());

        final QueueServiceClient client = QueueClientFactory.createQueueServiceClient(configuration);
        serviceClient = new QueueServiceClientWrapper(client);
    }

    @Test
    public void testCreateDeleteQueue() throws InterruptedException {
        final String queueName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

        template.send("direct:createQueue", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
        });

        result.assertIsSatisfied();

        // check name
        assertEquals(queueName, serviceClient.listQueues(null, null).get(0).getName());

        // delete queue
        template.send("direct:deleteQueue", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
        });

        // create from name from the route
        template.send("direct:createQueue", ExchangePattern.InOnly, exchange -> {
        });
        result.assertIsSatisfied();

        // check name
        assertEquals("testqueue", serviceClient.listQueues(null, null).get(0).getName());

        serviceClient.getQueueClientWrapper("testqueue").delete(null);
    }

    @Test
    public void testSendAndDeleteMessage() throws InterruptedException {
        final String queueName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

        // first test if queue is not created
        template.send("direct:sendMessage", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
            exchange.getIn().setBody("test-message-1");
            exchange.getIn().setHeader(QueueConstants.CREATE_QUEUE, false);
        });

        result.assertIsSatisfied();

        // queue not created because of the flag
        assertTrue(result.getExchanges().isEmpty());

        result.reset();

        // test the rest
        template.send("direct:sendMessage", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
            exchange.getIn().setBody("test-message-1");
        });

        result.assertIsSatisfied();

        final Map<String, Object> sentMessageHeaders = result.getExchanges().get(0).getMessage().getHeaders();

        // check message
        assertEquals("test-message-1",
                serviceClient.getQueueClientWrapper(queueName).peekMessages(1, null).get(0).getMessageText());

        result.reset();

        template.send("direct:deleteMessage", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
            exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, sentMessageHeaders.get(QueueConstants.MESSAGE_ID));
            exchange.getIn().setHeader(QueueConstants.POP_RECEIPT, sentMessageHeaders.get(QueueConstants.POP_RECEIPT));
        });

        // check
        assertTrue(serviceClient.getQueueClientWrapper(queueName).peekMessages(1, null).isEmpty());

        result.assertIsSatisfied();

        serviceClient.getQueueClientWrapper(queueName).delete(null);
    }

    @Test
    public void testReceiveMessages() throws InterruptedException {
        final String queueName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        serviceClient.getQueueClientWrapper(queueName).create(null, null);
        serviceClient.getQueueClientWrapper(queueName).sendMessage("test-message-1", null, null, null);
        serviceClient.getQueueClientWrapper(queueName).sendMessage("test-message-2", null, null, null);
        serviceClient.getQueueClientWrapper(queueName).sendMessage("test-message-3", null, null, null);

        result.expectedMessageCount(1);

        template.send("direct:receiveMessages", ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader(QueueConstants.QUEUE_NAME, queueName);
            exchange.getIn().setHeader(QueueConstants.MAX_MESSAGES, 3);
        });

        result.assertIsSatisfied();

        final List<QueueMessageItem> messages = result.getExchanges().get(0).getMessage().getBody(List.class);
        final List<String> messagesText = messages
                .stream()
                .map(QueueMessageItem::getMessageText)
                .collect(Collectors.toList());

        assertTrue(messagesText.contains("test-message-1"));
        assertTrue(messagesText.contains("test-message-2"));
        assertTrue(messagesText.contains("test-message-3"));

        serviceClient.getQueueClientWrapper(queueName).delete(null);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("creds", storageSharedKeyCredential());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
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

    private StorageSharedKeyCredential storageSharedKeyCredential() throws Exception {
        final Properties properties = QueueTestUtils.loadAzureAccessFromJvmEnv();
        return new StorageSharedKeyCredential(properties.getProperty("account_name"), properties.getProperty("access_key"));
    }

    private String componentUri(final String operation, final String queueName) {
        return String.format("azure-storage-queue://cameldev/%s?credentials=#creds&operation=%s", queueName, operation);
    }
}

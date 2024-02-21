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
package org.apache.camel.component.azure.storage.queue.operations.integration;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.PeekedMessageItem;
import com.azure.storage.queue.models.QueueItem;
import com.azure.storage.queue.models.QueueMessageItem;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.component.azure.storage.queue.QueueConstants;
import org.apache.camel.component.azure.storage.queue.QueueTestUtils;
import org.apache.camel.component.azure.storage.queue.client.QueueClientFactory;
import org.apache.camel.component.azure.storage.queue.client.QueueClientWrapper;
import org.apache.camel.component.azure.storage.queue.client.QueueServiceClientWrapper;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperationResponse;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperations;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "accountName", matches = ".*",
                         disabledReason = "Make sure to supply azure accessKey or accountName, e.g:  mvn verify -DaccountName=myacc -DaccessKey=mykey")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueOperationsIT extends CamelTestSupport {

    private QueueConfiguration configuration;
    private QueueServiceClientWrapper serviceClientWrapper;

    @BeforeAll
    public void setup() throws Exception {
        final Properties properties = QueueTestUtils.loadAzureAccessFromJvmEnv();

        configuration = new QueueConfiguration();
        configuration.setAccountName(properties.getProperty("account_name"));
        configuration.setAccessKey(properties.getProperty("access_key"));
        configuration.setMaxMessages(5);

        final QueueServiceClient client = QueueClientFactory.createQueueServiceClient(configuration);

        serviceClientWrapper = new QueueServiceClientWrapper(client);
    }

    @Test
    public void testCreateDeleteQueue() {
        final String queueName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final QueueClientWrapper clientWrapper = serviceClientWrapper.getQueueClientWrapper(queueName);
        final QueueOperations operations = new QueueOperations(configuration, clientWrapper);

        // test create queue
        final QueueOperationResponse response = operations.createQueue(null);

        assertNotNull(response);
        assertNotNull(response.getHeaders());
        assertTrue((boolean) response.getBody());
        assertTrue(serviceClientWrapper.listQueues(null, null)
                .stream()
                .map(QueueItem::getName)
                .toList()
                .contains(queueName));

        // delete and test
        final QueueOperationResponse response2 = operations.deleteQueue(null);
        assertNotNull(response2);
        assertNotNull(response2.getHeaders());
        assertTrue((boolean) response2.getBody());
        assertFalse(serviceClientWrapper.listQueues(null, null)
                .stream()
                .map(QueueItem::getName)
                .toList()
                .contains(queueName));
    }

    @Test
    public void testSendMessageAndClearQueue() {
        final String queueName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final QueueClientWrapper clientWrapper = serviceClientWrapper.getQueueClientWrapper(queueName);
        final QueueOperations operations = new QueueOperations(configuration, clientWrapper);

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("testing message");

        final QueueOperationResponse response = operations.sendMessage(exchange);

        assertNotNull(response);
        assertNotNull(response.getHeaders());
        assertTrue((boolean) response.getBody());
        assertNotNull(response.getHeaders().get(QueueConstants.MESSAGE_ID));
        assertNotNull(response.getHeaders().get(QueueConstants.EXPIRATION_TIME));
        assertNotNull(response.getHeaders().get(QueueConstants.POP_RECEIPT));

        final QueueMessageItem messageItem
                = clientWrapper.receiveMessages(1, Duration.ofSeconds(30), null).stream().findFirst().get();

        assertEquals("testing message", messageItem.getBody().toString());

        // test clear queue
        operations.clearQueue(exchange);

        assertTrue(clientWrapper.receiveMessages(1, null, null).isEmpty());

        // delete testing queue
        operations.deleteQueue(exchange);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testReceiveAndPeekMessages() {
        final QueueOperations operations = getQueueOperations();

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("testing message-1");
        operations.sendMessage(exchange);

        exchange.getIn().setBody("testing message-2");
        operations.sendMessage(exchange);

        exchange.getIn().setBody("testing message-3");
        operations.sendMessage(exchange);

        // test peek messages
        final QueueOperationResponse peekResponse = operations.peekMessages(exchange);
        final List<PeekedMessageItem> peekedMessageItems = (List<PeekedMessageItem>) peekResponse.getBody();

        assertEquals(3, peekedMessageItems.size());
        assertEquals("testing message-1", peekedMessageItems.get(0).getBody().toString());
        assertEquals("testing message-2", peekedMessageItems.get(1).getBody().toString());
        assertEquals("testing message-3", peekedMessageItems.get(2).getBody().toString());

        // test receive message
        exchange.getIn().setHeader(QueueConstants.MAX_MESSAGES, 1);
        final QueueOperationResponse receiveResponse = operations.receiveMessages(exchange);
        final List<QueueMessageItem> receivedMessageItems = (List<QueueMessageItem>) receiveResponse.getBody();

        assertEquals(1, receivedMessageItems.size());
        assertEquals("testing message-1", receivedMessageItems.get(0).getBody().toString());

        // make sure the message has been deQueued
        assertEquals(2, ((List<PeekedMessageItem>) operations.peekMessages(null).getBody()).size());

        // delete testing queue
        operations.deleteQueue(exchange);
    }

    @Test
    public void testDeleteMessages() {
        final QueueOperations operations = getQueueOperations();

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("testing message-1");
        final QueueOperationResponse sentMessage1 = operations.sendMessage(exchange);

        exchange.getIn().setBody("testing message-2");
        final QueueOperationResponse sentMessage2 = operations.sendMessage(exchange);

        // test delete message
        assertThrows(IllegalArgumentException.class, () -> operations.deleteMessage(exchange));
        exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, sentMessage1.getHeaders().get(QueueConstants.MESSAGE_ID));
        // we still need pop receipt
        assertThrows(IllegalArgumentException.class, () -> operations.deleteMessage(exchange));
        // delete message now
        exchange.getIn().setHeader(QueueConstants.POP_RECEIPT, sentMessage1.getHeaders().get(QueueConstants.POP_RECEIPT));
        operations.deleteMessage(exchange);

        // check the what we have in the queue
        final QueueOperationResponse peekResponse = operations.peekMessages(exchange);
        @SuppressWarnings("unchecked")
        final List<PeekedMessageItem> peekedMessageItems = (List<PeekedMessageItem>) peekResponse.getBody();

        assertEquals(1, peekedMessageItems.size());
        assertEquals(sentMessage2.getHeaders().get(QueueConstants.MESSAGE_ID), peekedMessageItems.get(0).getMessageId());

        // delete testing queue
        operations.deleteQueue(exchange);
    }

    @Test
    public void testUpdateMessage() {
        final QueueOperations operations = getQueueOperations();

        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("testing message-1");
        final QueueOperationResponse sentMessage = operations.sendMessage(exchange);

        // let's do our update
        exchange.getIn().setBody("updated message-1");
        exchange.getIn().setHeader(QueueConstants.POP_RECEIPT, sentMessage.getHeaders().get(QueueConstants.POP_RECEIPT));
        exchange.getIn().setHeader(QueueConstants.MESSAGE_ID, sentMessage.getHeaders().get(QueueConstants.MESSAGE_ID));
        exchange.getIn().setHeader(QueueConstants.VISIBILITY_TIMEOUT, Duration.ofMillis(10));

        final QueueOperationResponse updatedMessage = operations.updateMessage(exchange);

        assertNotNull(updatedMessage);
        assertNotNull(updatedMessage.getHeaders());
        assertTrue((boolean) updatedMessage.getBody());
        assertNotNull(updatedMessage.getHeaders().get(QueueConstants.POP_RECEIPT));
        assertNotNull(updatedMessage.getHeaders().get(QueueConstants.TIME_NEXT_VISIBLE));

        // check the what we have in the queue
        final QueueOperationResponse peekResponse = operations.peekMessages(exchange);
        @SuppressWarnings("unchecked")
        final List<PeekedMessageItem> peekedMessageItems = (List<PeekedMessageItem>) peekResponse.getBody();

        assertEquals(1, peekedMessageItems.size());
        assertEquals("updated message-1", peekedMessageItems.get(0).getBody().toString());

        // delete testing queue
        operations.deleteQueue(exchange);
    }

    @AfterAll
    public void tearDown() {
        // make sure to clean everything
        final List<QueueItem> queues = serviceClientWrapper.listQueues(null, null);

        if (queues.size() > 0) {
            queues.forEach(queueItem -> serviceClientWrapper.getQueueClientWrapper(queueItem.getName()).delete(null));
        }
    }

    private QueueOperations getQueueOperations() {
        final String queueName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final QueueClientWrapper clientWrapper = serviceClientWrapper.getQueueClientWrapper(queueName);
        return new QueueOperations(configuration, clientWrapper);
    }
}

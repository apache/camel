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
import java.util.Properties;

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.queue.QueueServiceClient;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.storage.queue.client.QueueClientFactory;
import org.apache.camel.component.azure.storage.queue.client.QueueClientWrapper;
import org.apache.camel.component.azure.storage.queue.client.QueueServiceClientWrapper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueConsumerIT extends CamelTestSupport {

    @EndpointInject("mock:result")
    private MockEndpoint result;
    private String resultName = "mock:result";

    private String queueName;
    private QueueClientWrapper queueClientWrapper;

    @BeforeAll
    public void prepare() throws Exception {
        queueName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

        final QueueConfiguration configuration = new QueueConfiguration();
        configuration.setCredentials(storageSharedKeyCredential());
        configuration.setQueueName(queueName);

        final QueueServiceClient serviceClient = QueueClientFactory.createQueueServiceClient(configuration);
        queueClientWrapper = new QueueServiceClientWrapper(serviceClient).getQueueClientWrapper(queueName);

        queueClientWrapper.create(null, null);
        queueClientWrapper.sendMessage("test-message-1", null, null, null);
        queueClientWrapper.sendMessage("test-message-2", null, null, null);
        queueClientWrapper.sendMessage("test-message-3", null, null, null);
    }

    @Test
    public void testPollingMessages() throws InterruptedException {
        result.expectedMessageCount(3);
        result.assertIsSatisfied();

        final List<Exchange> exchanges = result.getExchanges();

        result.message(0).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(0);
        result.message(1).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(1);
        result.message(2).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(2);
        result.expectedPropertyReceived(Exchange.BATCH_SIZE, 3);

        assertEquals("test-message-1", exchanges.get(0).getMessage().getBody());
        assertEquals("test-message-2", exchanges.get(1).getMessage().getBody());
        assertEquals("test-message-3", exchanges.get(2).getMessage().getBody());
    }

    @AfterAll
    public void tearDown() {
        queueClientWrapper.delete(null);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("azure-storage-queue://cameldev/" + queueName + "?credentials=#creds&maxMessages=5")
                        .to(resultName);

            }
        };
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("creds", storageSharedKeyCredential());
        return context;
    }

    private StorageSharedKeyCredential storageSharedKeyCredential() throws Exception {
        final Properties properties = QueueTestUtils.loadAzureAccessFromJvmEnv();
        return new StorageSharedKeyCredential(properties.getProperty("account_name"), properties.getProperty("access_key"));
    }
}

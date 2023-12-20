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

import java.util.List;
import java.util.Properties;

import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueItem;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.component.azure.storage.queue.QueueTestUtils;
import org.apache.camel.component.azure.storage.queue.client.QueueClientFactory;
import org.apache.camel.component.azure.storage.queue.client.QueueServiceClientWrapper;
import org.apache.camel.component.azure.storage.queue.operations.QueueOperationResponse;
import org.apache.camel.component.azure.storage.queue.operations.QueueServiceOperations;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "accountName", matches = ".*",
                         disabledReason = "Make sure to supply azure accessKey or accountName, e.g:  mvn verify -DaccountName=myacc -DaccessKey=mykey")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueServiceOperationsIT {

    private QueueConfiguration configuration;
    private QueueServiceClientWrapper clientWrapper;

    private String queueName1;
    private String queueName2;
    private String queueName3;

    @BeforeAll
    public void setup() throws Exception {
        final Properties properties = QueueTestUtils.loadAzureAccessFromJvmEnv();

        queueName1 = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        queueName2 = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        queueName3 = RandomStringUtils.randomAlphabetic(10).toLowerCase();

        configuration = new QueueConfiguration();
        configuration.setAccountName(properties.getProperty("account_name"));
        configuration.setAccessKey(properties.getProperty("access_key"));

        final QueueServiceClient client = QueueClientFactory.createQueueServiceClient(configuration);

        clientWrapper = new QueueServiceClientWrapper(client);

        // create test queues
        client.createQueue(queueName1);
        client.createQueue(queueName2);
        client.createQueue(queueName3);
    }

    @Test
    public void testListQueues() {
        final QueueServiceOperations operations = new QueueServiceOperations(configuration, clientWrapper);

        // test
        final QueueOperationResponse queuesResponse = operations.listQueues(null);

        assertNotNull(queuesResponse);
        assertNotNull(queuesResponse.getBody());

        @SuppressWarnings("unchecked")
        final List<String> queues = ((List<QueueItem>) queuesResponse.getBody())
                .stream()
                .map(QueueItem::getName)
                .toList();

        assertTrue(queues.contains(queueName1));
        assertTrue(queues.contains(queueName2));
        assertTrue(queues.contains(queueName3));
    }

    @AfterAll
    public void tearDown() {
        final QueueServiceClient client = QueueClientFactory.createQueueServiceClient(configuration);

        client.deleteQueue(queueName1);
        client.deleteQueue(queueName2);
        client.deleteQueue(queueName3);
    }
}

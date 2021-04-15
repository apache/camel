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

import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.queue.QueueServiceClient;
import org.apache.camel.CamelContext;
import org.apache.camel.component.azure.storage.queue.QueueConfiguration;
import org.apache.camel.test.infra.azure.common.AzureConfigs;
import org.apache.camel.test.infra.azure.common.services.AzureService;
import org.apache.camel.test.infra.azure.storage.queue.clients.AzureStorageClientUtils;
import org.apache.camel.test.infra.azure.storage.queue.services.AzureStorageQueueServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StorageQueueBase extends CamelTestSupport {
    @RegisterExtension
    public static AzureService service;

    protected QueueServiceClient serviceClient;
    protected String queueName;
    protected QueueConfiguration configuration;

    static {
        initCredentials();

        service = AzureStorageQueueServiceFactory.createService();
    }

    /*
     * The previous behavior of the test code was such that if accessKey or accountName properties were
     * set, the code would not start the azurite container and would execute against a remote environment.
     * To avoid breaking tests for environments relying on this behavior, copy the old properties into the
     *  new and set the test as remote.
     */
    private static void initCredentials() {
        String accountName = System.getProperty("accountName");
        String accessKey = System.getProperty("accessKey");

        if (StringUtils.isNotEmpty(accountName) && StringUtils.isNotEmpty(accessKey)) {
            System.setProperty(AzureConfigs.ACCOUNT_NAME, accountName);
            System.setProperty(AzureConfigs.ACCOUNT_KEY, accessKey);
            System.setProperty("azure.instance.type", "remote");
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("serviceClient", serviceClient);
        return context;
    }

    @BeforeAll
    public void initProperties() {
        queueName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        configuration = new QueueConfiguration();
        configuration.setCredentials(new StorageSharedKeyCredential(
                service.azureCredentials().accountName(), service.azureCredentials().accountKey()));
        configuration.setQueueName(queueName);

        serviceClient = AzureStorageClientUtils.getClient();
        serviceClient.getQueueClient(queueName).create();
    }

}

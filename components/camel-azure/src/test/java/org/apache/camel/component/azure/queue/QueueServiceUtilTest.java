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
package org.apache.camel.component.azure.queue;

import java.net.URI;

import com.microsoft.azure.storage.queue.CloudQueue;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.newAccountKeyCredentials;
import static org.apache.camel.component.azure.common.AzureServiceCommonTestUtil.registerCredentials;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class QueueServiceUtilTest extends CamelTestSupport {

    @Test
    public void testPrepareUri() throws Exception {
        registerCredentials(context);

        QueueServiceEndpoint endpoint
                = (QueueServiceEndpoint) context.getEndpoint("azure-queue://camelazure/testqueue?credentials=#creds");
        URI uri = QueueServiceUtil.prepareStorageQueueUri(endpoint.getConfiguration());
        assertEquals("https://camelazure.queue.core.windows.net/testqueue", uri.toString());
    }

    @Test
    public void testGetConfiguredClient() throws Exception {
        CloudQueue client
                = new CloudQueue(URI.create("https://camelazure.queue.core.windows.net/testqueue"), newAccountKeyCredentials());
        context.getRegistry().bind("azureQueueClient", client);

        QueueServiceEndpoint endpoint = (QueueServiceEndpoint) context
                .getEndpoint("azure-queue://camelazure/testqueue?azureQueueClient=#azureQueueClient");
        assertSame(client, QueueServiceUtil.getConfiguredClient(endpoint.getConfiguration()));
    }

    @Test
    public void testGetConfiguredClientUriMismatch() throws Exception {
        CloudQueue client
                = new CloudQueue(URI.create("https://camelazure.queue.core.windows.net/testqueue"), newAccountKeyCredentials());

        context.getRegistry().bind("azureQueueClient", client);

        QueueServiceEndpoint endpoint = (QueueServiceEndpoint) context
                .getEndpoint("azure-queue://camelazure/testqueue2?azureQueueClient=#azureQueueClient");

        QueueServiceConfiguration configuration = endpoint.getConfiguration();
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> QueueServiceUtil.getConfiguredClient(configuration));

        assertEquals("Invalid Client URI", ex.getMessage());
    }

    @Test
    public void testGetConfiguredClientWithClientUriValidationDisabled() throws Exception {
        URI uri = URI.create("https://custom/azure/service/url/container/testqueue");
        CloudQueue client = new CloudQueue(uri, newAccountKeyCredentials());

        context.getRegistry().bind("azureQueueClient", client);

        QueueServiceEndpoint endpoint = (QueueServiceEndpoint) context
                .getEndpoint("azure-queue://camelazure/testqueue?azureQueueClient=#azureQueueClient&validateClientURI=false");

        CloudQueue configuredClient = QueueServiceUtil.getConfiguredClient(endpoint.getConfiguration());
        assertEquals(uri, configuredClient.getUri());
    }

}

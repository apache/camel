/**
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

import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.core.Base64;
import com.microsoft.azure.storage.queue.CloudQueue;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.impl.PropertyPlaceholderDelegateRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class QueueServiceUtilTest extends CamelTestSupport {

    @Test
    public void testPrepareUri() throws Exception {
        registerCredentials();

        QueueServiceComponent component = new QueueServiceComponent(context);
        QueueServiceEndpoint endpoint = (QueueServiceEndpoint) component.createEndpoint("azure-queue://camelazure/testqueue?credentials=#creds");
        URI uri = QueueServiceUtil.prepareStorageQueueUri(endpoint.getConfiguration());
        assertEquals("https://camelazure.queue.core.windows.net/testqueue", uri.toString());
    }

    @Test
    public void testGetConfiguredClient() throws Exception {
        CloudQueue client = new CloudQueue(URI.create("https://camelazure.queue.core.windows.net/testqueue"), newAccountKeyCredentials());
        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("azureQueueClient", client);

        QueueServiceComponent component = new QueueServiceComponent(context);
        QueueServiceEndpoint endpoint = (QueueServiceEndpoint) component.createEndpoint("azure-queue://camelazure/testqueue?azureQueueClient=#azureQueueClient");
        assertSame(client, QueueServiceUtil.getConfiguredClient(endpoint.getConfiguration()));
    }

    @Test
    public void testGetConfiguredClientUriMismatch() throws Exception {
        CloudQueue client = new CloudQueue(URI.create("https://camelazure.queue.core.windows.net/testqueue"), newAccountKeyCredentials());

        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("azureQueueClient", client);

        QueueServiceComponent component = new QueueServiceComponent(context);
        QueueServiceEndpoint endpoint = (QueueServiceEndpoint) component.createEndpoint("azure-queue://camelazure/testqueue2?azureQueueClient=#azureQueueClient");

        try {
            QueueServiceUtil.getConfiguredClient(endpoint.getConfiguration());
            fail();
        } catch (IllegalArgumentException ex) {
            assertEquals("Invalid Client URI", ex.getMessage());
        }
    }

    private void registerCredentials() {
        JndiRegistry registry = (JndiRegistry) ((PropertyPlaceholderDelegateRegistry) context.getRegistry()).getRegistry();
        registry.bind("creds", newAccountKeyCredentials());
    }

    private StorageCredentials newAccountKeyCredentials() {
        return new StorageCredentialsAccountAndKey("camelazure", Base64.encode("key".getBytes()));
    }
}

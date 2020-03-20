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

import java.util.Map;
import java.util.Set;

import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.queue.CloudQueue;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("azure-queue")
public class QueueServiceComponent extends DefaultComponent {

    public static final String MISSING_QUEUE_CREDNTIALS_EXCEPTION_MESSAGE =
            "One of azureQueueClient, credentials or both credentialsAccountName and credentialsAccountKey must be specified";
    
    @Metadata(label = "advanced")
    private QueueServiceConfiguration configuration;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        final QueueServiceConfiguration configuration = this.configuration != null ? this.configuration.copy() : new QueueServiceConfiguration();

        String[] parts = null;
        if (remaining != null) {
            parts = remaining.split("/");
        }
        if (parts == null || parts.length < 1) {
            throw new IllegalArgumentException("The account name must be specified.");
        }
        configuration.setAccountName(parts[0]);
        if (parts.length > 1) {
            configuration.setQueueName(parts[1]);
        }
        if (parts.length > 2) {
            throw new IllegalArgumentException("Only the account and queue names must be specified.");
        }

        QueueServiceEndpoint endpoint = new QueueServiceEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        checkAndSetRegistryClient(configuration);
        checkCredentials(configuration);
        QueueServiceOperations operation = configuration.getOperation();
        if (operation != null && operation != QueueServiceOperations.listQueues && parts.length < 2) {
            throw new IllegalArgumentException("The queue name must be specified.");
        }
        return endpoint;
    }

    public QueueServiceConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The Queue Service configuration
     */
    public void setConfiguration(QueueServiceConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkCredentials(QueueServiceConfiguration cfg) {
        CloudQueue client = cfg.getAzureQueueClient();
        StorageCredentials creds = client == null ? cfg.getAccountCredentials() : client.getServiceClient().getCredentials();
        if (creds == null) {
            throw new IllegalArgumentException(MISSING_QUEUE_CREDNTIALS_EXCEPTION_MESSAGE);
        }
    }
    
    private void checkAndSetRegistryClient(QueueServiceConfiguration configuration) {
        Set<CloudQueue> clients = getCamelContext().getRegistry().findByType(CloudQueue.class);
        if (clients.size() == 1) {
            configuration.setAzureQueueClient(clients.stream().findFirst().get());
        }
    }
}

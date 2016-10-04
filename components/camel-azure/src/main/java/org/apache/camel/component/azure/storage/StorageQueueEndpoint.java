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
package org.apache.camel.component.azure.storage;

import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.queue.CloudQueueClient;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

/**
 * Represents an Azure Storage Queue endpoint.
 */
@UriEndpoint(scheme = "azure-storage-queue", title = "Azure", syntax = "azure-storage-queue:resource", consumerClass = StorageQueueConsumer.class, label = "azure,storage,queue")
public class StorageQueueEndpoint extends DefaultEndpoint {
    @UriParam
    private StorageConfiguration configuration;

    public StorageQueueEndpoint(String uri, StorageQueueComponent component, StorageConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() throws Exception {
        return new StorageQueueProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new StorageQueueConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }

    public StorageConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public void doStart() throws Exception {
        CloudStorageAccount account = configuration.getStorageAccount();
        CloudQueueClient client = configuration.getQueueClient();

        super.doStart();

        if (account == null) {
            account = CloudStorageAccount.parse(configuration.getConnectionString());
            configuration.setStorageAccount(account);
        }

        if (client == null) {
            client = account.createCloudQueueClient();
            configuration.setQueueClient(client);
        }
    }
}

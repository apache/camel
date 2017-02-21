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

import com.microsoft.azure.storage.queue.CloudQueue;
import org.apache.camel.component.azure.common.AbstractConfiguration;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class QueueServiceConfiguration extends AbstractConfiguration {

    private String queueName;
    @UriParam
    private CloudQueue azureQueueClient;
    
    @UriParam(label = "producer", defaultValue = "getMessage")
    private QueueServiceOperations operation = QueueServiceOperations.getMessage;
    
    public String getQueueName() {
        return queueName;
    }
    
    /**
     * The queue resource name 
     */
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public CloudQueue getAzureQueueClient() {
        return azureQueueClient;
    }

    /**
     * The queue service client 
     */
    public void setAzureQueueClient(CloudQueue azureQueueClient) {
        this.azureQueueClient = azureQueueClient;
    }

    public QueueServiceOperations getOperation() {
        return operation;
    }

    /**
     * Queue service operation hint to the producer 
     */
    public void setOperation(QueueServiceOperations operation) {
        this.operation = operation;
    }
}
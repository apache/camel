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
package org.apache.camel.component.azure.servicebus;

import java.util.List;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.models.CreateQueueResult;
import com.microsoft.windowsazure.services.servicebus.models.QueueInfo;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

public class SbQueueEndpoint extends AbstractSbEndpoint {
    public SbQueueEndpoint(String uri, SbComponent component, SbConfiguration configuration) {
        super(uri, component, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AbstractSbConsumer sbConsumer = new SbQueueConsumer(this, processor);
        configureConsumer(sbConsumer);
        return sbConsumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (!isQueueExisted()) {
            createQueue();
        }
    }
    @Override
    public Producer createProducer() throws Exception {
        return new SbQueueProducer(this);
    }
    private void createQueue() throws Exception {
        QueueInfo newInfo = new QueueInfo(configuration.getQueueName());
        if (configuration.getAutoDeleteOnIdle() != null) {
            newInfo.setAutoDeleteOnIdle(configuration.getAutoDeleteOnIdle());
        }

        if (configuration.getDeadLetteringOnMessageExpiration() != null) {
            newInfo.setDeadLetteringOnMessageExpiration(configuration.getDeadLetteringOnMessageExpiration());
        }

        if (configuration.getDefaultMessageTimeToLive() != null) {
            newInfo.setDefaultMessageTimeToLive(configuration.getDefaultMessageTimeToLive());
        }

        if (configuration.getDuplicateDetectionHistoryTimeWindow() != null) {
            newInfo.setDuplicateDetectionHistoryTimeWindow(configuration.getDuplicateDetectionHistoryTimeWindow());
        }

        if (configuration.getEnableBatchedOperations() != null) {
            newInfo.setEnableBatchedOperations(configuration.getEnableBatchedOperations());
        }

        if (configuration.getForwardTo() != null) {
            newInfo.setForwardTo(configuration.getForwardTo());
        }

        if (configuration.getIsAnonymousAccessible() != null) {
            newInfo.setIsAnonymousAccessible(configuration.getIsAnonymousAccessible());
        }

        if (configuration.getLockDuration() != null) {
            newInfo.setLockDuration(configuration.getLockDuration());
        }

        if (configuration.getMaxDeliveryCount() != null) {
            newInfo.setMaxDeliveryCount(configuration.getMaxDeliveryCount());
        }

        if (configuration.getMaxSizeInMegabytes() != null) {
            newInfo.setMaxSizeInMegabytes(configuration.getMaxSizeInMegabytes());
        }

        if (configuration.getPartitioningPolicy() != null) {
            newInfo.setPartitioningPolicy(configuration.getPartitioningPolicy());
        }

        if (configuration.getRequiresDuplicateDetection() != null) {
            newInfo.setRequiresDuplicateDetection(configuration.getRequiresDuplicateDetection());
        }

        if (configuration.getRequiresSession() != null) {
            newInfo.setRequiresSession(configuration.getRequiresSession());
        }

        if (configuration.getStatus() != null) {
            newInfo.setStatus(configuration.getStatus());
        }

        if (configuration.getSupportOrdering() != null) {
            newInfo.setSupportOrdering(configuration.getSupportOrdering());
        }

        if (configuration.getUserMetadata() != null) {
            newInfo.setUserMetadata(configuration.getUserMetadata());
        }

        CreateQueueResult createQueueResult = client.createQueue(newInfo);
        if (createQueueResult.getValue() == null) {
            throw new Exception("Failed to create a queue <" + configuration.getQueueName() + ">");
        }
    }
    private boolean isQueueExisted() throws ServiceException {
        List<QueueInfo> items = client.listQueues().getItems();
        for (QueueInfo item : items) {
            if (item.getPath().equalsIgnoreCase(configuration.getQueueName())) {
                return true;
            }
        }
        return false;
    }
}

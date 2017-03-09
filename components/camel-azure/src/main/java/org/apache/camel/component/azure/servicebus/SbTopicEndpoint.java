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
import com.microsoft.windowsazure.services.servicebus.models.CreateSubscriptionResult;
import com.microsoft.windowsazure.services.servicebus.models.CreateTopicResult;
import com.microsoft.windowsazure.services.servicebus.models.SubscriptionInfo;
import com.microsoft.windowsazure.services.servicebus.models.TopicInfo;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SbTopicEndpoint extends AbstractSbEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(SbComponent.class);

    public SbTopicEndpoint(String uri, SbComponent component, SbConfiguration configuration) {
        super(uri, component, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        LOG.debug("SbTopicEndpoint#createConsumer");
        AbstractSbConsumer sbConsumer = new SbTopicConsumer(this, processor);
        configureConsumer(sbConsumer);
        return sbConsumer;
    }
    @Override
    public Producer createProducer() throws Exception {
        LOG.debug("SbTopicEndpoint#createProducer");
        return new SbTopicProducer(this);
    }

    private void createSubscription() throws ServiceException {
        LOG.debug("createSubscription getSubscriptionName: " + configuration.getSubscriptionName());

        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(configuration.getSubscriptionName());

        if (configuration.getForwardTo() != null) {
            subscriptionInfo.setForwardTo(configuration.getForwardTo());
        }

        if (configuration.getMaxDeliveryCount() != null) {
            subscriptionInfo.setMaxDeliveryCount(configuration.getMaxDeliveryCount());
        }

        if (configuration.getAutoDeleteOnIdle() != null) {
            subscriptionInfo.setAutoDeleteOnIdle(configuration.getAutoDeleteOnIdle());
        }

        if (configuration.getDeadLetteringOnMessageExpiration() != null) {
            subscriptionInfo.setDeadLetteringOnMessageExpiration(configuration.getDeadLetteringOnMessageExpiration());
        }

        if (configuration.getDefaultMessageTimeToLive() != null) {
            subscriptionInfo.setDefaultMessageTimeToLive(configuration.getDefaultMessageTimeToLive());
        }

        if (configuration.getEnableBatchedOperations() != null) {
            subscriptionInfo.setEnableBatchedOperations(configuration.getEnableBatchedOperations());
        }

        if (configuration.getLockDuration() != null) {
            subscriptionInfo.setLockDuration(configuration.getLockDuration());
        }

        if (configuration.getRequiresSession() != null) {
            subscriptionInfo.setRequiresSession(configuration.getRequiresSession());
        }

        if (configuration.getUserMetadata() != null) {
            subscriptionInfo.setUserMetadata(configuration.getUserMetadata());
        }

        if (configuration.getDeadLetteringOnFilterEvaluationExceptions() != null) {
            subscriptionInfo.setDeadLetteringOnFilterEvaluationExceptions(configuration.getDeadLetteringOnFilterEvaluationExceptions());
        }


// TODO: support rule description
//        RuleDescription ruleDescription = new RuleDescription();
//        RuleAction ruleAction = new RuleAction();
//        Filter filter = new Filter();
//        ruleDescription.setAction(ruleAction);
//        ruleDescription.setFilter(filter);
//
//        if (configuration.getRuleName() != null)
//            ruleDescription.setName(configuration.getRuleName());
//        if (configuration.getRuleTag() != null)
//            ruleDescription.setTag(configuration.getRuleTag());
//        subscriptionInfo.setDefaultRuleDescription(ruleDescription);
//
        try {
            CreateSubscriptionResult createSubscriptionResult = client.createSubscription(configuration.getTopicPath(), subscriptionInfo);
            SubscriptionInfo createdSubscription = createSubscriptionResult.getValue();

            if (createdSubscription == null) {
                throw new ServiceException("Failed to create a subscription <" + configuration.getSubscriptionName() + ">");
            }

            LOG.debug("createSubscription success SubscriptionName:" + createdSubscription.getName());
        } catch (Exception ex) {
            LOG.debug(ex.getMessage());
            ex.printStackTrace();
        }
    }

    private boolean isSubscriptionExisted() throws ServiceException {
        List<SubscriptionInfo> items = client.listSubscriptions(configuration.getTopicPath()).getItems();
        for (SubscriptionInfo item : items) {
            if (item.getName().equalsIgnoreCase(configuration.getSubscriptionName())) {
                return true;
            }
        }
        return false;
    }

    private void createTopic() throws ServiceException {
        LOG.debug("createTopic");
        TopicInfo newInfo = new TopicInfo(configuration.getTopicPath());

        if (configuration.getAutoDeleteOnIdle() != null) {
            newInfo.setAutoDeleteOnIdle(configuration.getAutoDeleteOnIdle());
        }

        if (configuration.getFilteringMessageBeforePublishing() != null) {
            newInfo.setFilteringMessageBeforePublishing(configuration.getFilteringMessageBeforePublishing());
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

        if (configuration.getIsAnonymousAccessible() != null) {
            newInfo.setAnonymousAccessible(configuration.getIsAnonymousAccessible());
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

        if (configuration.getStatus() != null) {
            newInfo.setStatus(configuration.getStatus());
        }

        if (configuration.getSupportOrdering() != null) {
            newInfo.setSupportOrdering(configuration.getSupportOrdering());
        }

        if (configuration.getUserMetadata() != null) {
            newInfo.setUserMetadata(configuration.getUserMetadata());
        }

        try {
            CreateTopicResult createTopicResult = client.createTopic(newInfo);
            TopicInfo createdTopic = createTopicResult.getValue();
            if (createdTopic == null) {
                throw new ServiceException("Failed to create a topic <" + configuration.getTopicPath() + ">");
            }
            LOG.debug("createTopic success createdTopic.getPath: " + createdTopic.getPath());
        } catch (Exception ex) {
            LOG.debug(ex.getMessage());
            ex.printStackTrace();
        }

    }

    private boolean isTopicExisted() throws ServiceException {
        List<TopicInfo> items = client.listTopics().getItems();
        for (TopicInfo item : items) {
            if (item.getPath().equalsIgnoreCase(configuration.getTopicPath())) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!isTopicExisted()) {
            createTopic();
        }
        if (null != configuration.getSubscriptionName()) {
            if (!isSubscriptionExisted()) {
                createSubscription();
            }
        }
    }

}

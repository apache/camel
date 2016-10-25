package org.apache.camel.component.windowsazure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.implementation.Filter;
import com.microsoft.windowsazure.services.servicebus.implementation.RuleAction;
import com.microsoft.windowsazure.services.servicebus.implementation.RuleDescription;
import com.microsoft.windowsazure.services.servicebus.implementation.TopicDescription;
import com.microsoft.windowsazure.services.servicebus.models.*;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

import java.util.List;

/**
 * Created by alan on 17/10/16.
 */
public class SbTopicEndpoint extends AbstractSbEndpoint {
    public SbTopicEndpoint(String uri, SbComponent component, SbConfiguration configuration) {
        super(uri, component, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        System.out.println("$$$$$ SbTopicEndpoint#createConsumer $$$$$");

        AbstractSbConsumer sbConsumer = new SbTopicConsumer(this, processor);
        configureConsumer(sbConsumer);
        return sbConsumer;
    }
    @Override
    public Producer createProducer() throws Exception {
        System.out.println("$$$$$ SbTopicEndpoint#createProducer $$$$$");

        return new SbTopicProducer(this);
    }

    private void createSubscription() throws ServiceException {
        System.out.println("$$$$$ createSubscription $$$$$getSubscriptionName: " + configuration.getSubscriptionName());

        SubscriptionInfo subscriptionInfo = new SubscriptionInfo(configuration.getSubscriptionName());
        if (configuration.getForwardTo()!= null)
            subscriptionInfo.setForwardTo(configuration.getForwardTo());
        if (configuration.getMaxDeliveryCount() != null)
            subscriptionInfo.setMaxDeliveryCount(configuration.getMaxDeliveryCount());
        if (configuration.getAutoDeleteOnIdle() != null)
            subscriptionInfo.setAutoDeleteOnIdle(configuration.getAutoDeleteOnIdle());
        if (configuration.getDeadLetteringOnMessageExpiration() != null)
            subscriptionInfo.setDeadLetteringOnMessageExpiration(configuration.getDeadLetteringOnMessageExpiration());
        if (configuration.getDefaultMessageTimeToLive() != null)
            subscriptionInfo.setDefaultMessageTimeToLive(configuration.getDefaultMessageTimeToLive());
        if (configuration.getEnableBatchedOperations() != null)
            subscriptionInfo.setEnableBatchedOperations(configuration.getEnableBatchedOperations());
        if (configuration.getLockDuration() != null)
            subscriptionInfo.setLockDuration(configuration.getLockDuration());
        if (configuration.getRequiresSession() != null)
            subscriptionInfo.setRequiresSession(configuration.getRequiresSession());
        if (configuration.getUserMetadata() != null)
            subscriptionInfo.setUserMetadata(configuration.getUserMetadata());
        if (configuration.getDeadLetteringOnFilterEvaluationExceptions() != null)
            subscriptionInfo.setDeadLetteringOnFilterEvaluationExceptions(configuration.getDeadLetteringOnFilterEvaluationExceptions());
// todo: support rule description
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
        try{
            CreateSubscriptionResult createSubscriptionResult = client.createSubscription(configuration.getTopicPath(),subscriptionInfo);
            SubscriptionInfo createdSubscription = createSubscriptionResult.getValue();
            if (createdSubscription == null){
                throw new ServiceException("Failed to create a subscription <" + configuration.getSubscriptionName() + ">");
            }
            System.out.println("$$$$$ createSubscription success $$$$$SubscriptionName:" + createdSubscription.getName());
        }catch (Exception ex){
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }

    }

    private boolean isSubscriptionExisted() throws ServiceException {
        List<SubscriptionInfo> items = client.listSubscriptions(configuration.getTopicPath()).getItems();
        for (int i = 0; i <items.size() ; i++) {
            if (items.get(i).getName().equalsIgnoreCase(configuration.getSubscriptionName())){
                return true;
            }
        }
        return false;
    }

    private void createTopic() throws ServiceException {
        System.out.println("$$$$$ createTopic $$$$$");
        TopicInfo newInfo = new TopicInfo(configuration.getTopicPath());
//        TopicDescription topicDescription = new TopicDescription();
//        if (configuration.getUserMetadata()!=null)
//        topicDescription.setUserMetadata(configuration.getUserMetadata());
//        if (configuration.getEnableBatchedOperations()!=null)
//            topicDescription.setEnableBatchedOperations(configuration.getEnableBatchedOperations());
//        if (configuration.getAutoDeleteOnIdle()!=null)
//            topicDescription.setAutoDeleteOnIdle(configuration.getAutoDeleteOnIdle());
//        if (configuration.getDefaultMessageTimeToLive()!=null)
//            topicDescription.setDefaultMessageTimeToLive(configuration.getDefaultMessageTimeToLive());
//        if (configuration.getDuplicateDetectionHistoryTimeWindow()!=null)
//            topicDescription.setDuplicateDetectionHistoryTimeWindow(configuration.getDuplicateDetectionHistoryTimeWindow());
//        if (configuration.getFilteringMessageBeforePublishing()!=null)
//            topicDescription.setFilteringMessagesBeforePublishing(configuration.getFilteringMessageBeforePublishing());
//        if (configuration.getIsAnonymousAccessible()!=null)
//            topicDescription.setIsAnonymousAccessible(configuration.getIsAnonymousAccessible());
//        if (configuration.getMaxSizeInMegabytes()!=null)
//            topicDescription.setMaxSizeInMegabytes(configuration.getMaxSizeInMegabytes());
//        if (configuration.getPartitioningPolicy()!=null)
//            topicDescription.setPartitioningPolicy(configuration.getPartitioningPolicy());
//        if (configuration.getRequiresDuplicateDetection()!=null)
//            topicDescription.setRequiresDuplicateDetection(configuration.getRequiresDuplicateDetection());
//        if (configuration.getSupportOrdering()!=null)
//            topicDescription.setSupportOrdering(configuration.getSupportOrdering());
//
//        newInfo.setModel(topicDescription);

        if (configuration.getAutoDeleteOnIdle()!=null)
            newInfo.setAutoDeleteOnIdle(configuration.getAutoDeleteOnIdle());
        if (configuration.getFilteringMessageBeforePublishing()!=null)
            newInfo.setFilteringMessageBeforePublishing(configuration.getFilteringMessageBeforePublishing());
        if (configuration.getDefaultMessageTimeToLive()!=null)
            newInfo.setDefaultMessageTimeToLive(configuration.getDefaultMessageTimeToLive());
        if (configuration.getDuplicateDetectionHistoryTimeWindow()!=null)
            newInfo.setDuplicateDetectionHistoryTimeWindow(configuration.getDuplicateDetectionHistoryTimeWindow());
        if (configuration.getEnableBatchedOperations()!=null)
            newInfo.setEnableBatchedOperations(configuration.getEnableBatchedOperations());
        if (configuration.getIsAnonymousAccessible()!=null)
            newInfo.setAnonymousAccessible(configuration.getIsAnonymousAccessible());
        if (configuration.getMaxSizeInMegabytes()!=null)
            newInfo.setMaxSizeInMegabytes(configuration.getMaxSizeInMegabytes());
        if (configuration.getPartitioningPolicy()!=null)
            newInfo.setPartitioningPolicy(configuration.getPartitioningPolicy());
        if (configuration.getRequiresDuplicateDetection()!=null)
            newInfo.setRequiresDuplicateDetection(configuration.getRequiresDuplicateDetection());
        if (configuration.getStatus()!=null)
            newInfo.setStatus(configuration.getStatus());
        if (configuration.getSupportOrdering()!=null)
            newInfo.setSupportOrdering(configuration.getSupportOrdering());
        if (configuration.getUserMetadata()!=null)
            newInfo.setUserMetadata(configuration.getUserMetadata());
        try{
            CreateTopicResult createTopicResult = client.createTopic(newInfo);
            TopicInfo createdTopic = createTopicResult.getValue();
            if(createdTopic == null){
                throw new ServiceException("Failed to create a topic <" + configuration.getTopicPath() + ">");
            }
            System.out.println("$$$$$ createTopic success $$$$$ createdTopic.getPath: " + createdTopic.getPath());
        }catch (Exception ex){
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }

    }

    private boolean isTopicExisted() throws ServiceException {
        List<TopicInfo> items = client.listTopics().getItems();
        for (int i = 0; i <items.size() ; i++) {
            if (items.get(i).getPath().equalsIgnoreCase(configuration.getTopicPath())){
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

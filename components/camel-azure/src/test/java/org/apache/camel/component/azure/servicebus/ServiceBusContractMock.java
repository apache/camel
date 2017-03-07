package org.apache.camel.component.azure.servicebus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.windowsazure.core.pipeline.filter.ServiceRequestFilter;
import com.microsoft.windowsazure.core.pipeline.filter.ServiceResponseFilter;
import com.microsoft.windowsazure.core.pipeline.jersey.ServiceFilter;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.ServiceBusContract;
import com.microsoft.windowsazure.services.servicebus.models.*;

public class ServiceBusContractMock implements ServiceBusContract {
    private Map<String, TopicInfo> _dummyTopicInfos = new HashMap<>();
    private Map<String, SubscriptionInfo> _dummySubscriptionInfos = new HashMap<>();
    private Map<String, RuleInfo> _dummyRules = new HashMap<>();
    private Map<String, QueueInfo> _dummyQueueInfos = new HashMap<>();
    private Map<String, EventHubInfo> _dummyEventHubs = new HashMap<>();
    private Map<String, List<BrokeredMessage>> _dummyMq = new HashMap<>();
    private Map<String, List<String>> _dummyTopicSubscriptions = new HashMap<>();
    private Map<String, BrokeredMessage> _dummyLocks = new HashMap<>();

    private Logger LOG = LoggerFactory.getLogger(ServiceBusContractMock.class);

    protected BrokeredMessage getBrokeredMessage(String queuePath) {
        BrokeredMessage message = null;
        if(_dummyMq.get(queuePath) != null && _dummyMq.get(queuePath).size() >0) {
            message = _dummyMq.get(queuePath).get(0);
        }
        return message;
    }

    protected BrokeredMessage removeBrokeredMessage(String queuePath) {
        BrokeredMessage message = null;
        if(_dummyMq.get(queuePath) != null && _dummyMq.get(queuePath).size() >0) {
            message = _dummyMq.get(queuePath).get(0);
        }
        return message;
    }

    protected BrokeredMessage getQueueMessage(String queuePath, ReceiveMessageOptions options) {

        BrokeredMessage message = null;
        if (options.getTimeout() != null) {
            // do nothing
        }

        if (options.isReceiveAndDelete()) {
            message = removeBrokeredMessage(queuePath);
        } else if (options.isPeekLock()) {
            // Passing 0 as request content just to force jersey client to add Content-Length header.
            // ServiceBus service doesn't read http request body for message receive requests.
            message = getBrokeredMessage(queuePath);
            if (message != null){
                lockMessage(message);
            }
        } else {
            throw new RuntimeException("Unknown ReceiveMode");
        }
        return message;
    }

    protected void lockMessage(BrokeredMessage message) {
        if (message == null){
            LOG.debug("$$$ message == null $$$");

        }
        if (message.getBrokerProperties() == null){
            LOG.debug("$$$message.getBrokerProperties() == null$$$");
        }
        message.getBrokerProperties().setLockLocation("Lock://" + message.getLockToken());
        _dummyLocks.put(message.getLockLocation(), message);
    }

//    private BrokeredMessage getTopicMessage(String topicPath) {
//        return getBrokeredMessage(topicPath,_dummyTopics);
//    }

    protected void setBrokeredMessage(String name, BrokeredMessage message, Map<String, List<BrokeredMessage>> manager){
        List<BrokeredMessage> messagesList = manager.computeIfAbsent(name, k -> new ArrayList<>());
        messagesList.add(message);
    }

    protected synchronized void setQueueMessage(String queuePath, BrokeredMessage message){
        setBrokeredMessage(queuePath, message, _dummyMq);
    }

    protected synchronized void setTopicMessage(String topicPath, BrokeredMessage message){
        List<String> subscriptions = _dummyTopicSubscriptions.computeIfAbsent(topicPath, k -> new ArrayList<>());
        subscriptions.forEach(subscritption -> setBrokeredMessage(getSubscriptionPath(topicPath, subscritption), message, _dummyMq));
    }

    protected String getSubscriptionPath(String topicPath, String subscriptionName) {
        return getSubscriptionPathPrefix(topicPath) + subscriptionName;
    }

    protected String getRulePath(String topicPath, String subscriptionName, String ruleName){
        return getRulePathPrefix(topicPath, subscriptionName) + ruleName;
    }

    protected String getRulePathPrefix(String topicPath, String subscriptionName) {
        return getSubscriptionPath(topicPath, subscriptionName) + "/rules/";
    }

    protected String getSubscriptionPathPrefix(String topicPath) {
        return topicPath + "/subscriptions/";
    }

    protected void deleteMessageByLockLocation(List<BrokeredMessage> queue, String lockLocation) {
        for(int i=0; i<queue.size(); i++){
            if (queue.get(i).getLockLocation().equals(lockLocation)){
                queue.remove(i);
                return;
            }
        }
    }
    /**
     * Sends a queue message.
     *
     * @param queuePath A <code>String</code> object that represents the name of the
     *                  queue to which the message will be sent.
     * @param message   A <code>Message</code> object that represents the message to
     *                  send.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void sendQueueMessage(String queuePath, BrokeredMessage message) throws ServiceException {
        sendMessage(queuePath, message);
    }

    /**
     * Receives a queue message.
     *
     * @param queuePath A <code>String</code> object that represents the name of the
     *                  queue from which to receive the message.
     * @return A <code>ReceiveQueueMessageResult</code> object that represents
     * the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ReceiveQueueMessageResult receiveQueueMessage(String queuePath) throws ServiceException {
        return receiveQueueMessage(queuePath, ReceiveMessageOptions.DEFAULT);
    }

    /**
     * Receives a queue message using the specified receive message options.
     *
     * @param queuePath A <code>String</code> object that represents the name of the
     *                  queue from which to receive the message.
     * @param options   A <code>ReceiveMessageOptions</code> object that represents
     *                  the receive message options.
     * @return A <code>ReceiveQueueMessageResult</code> object that represents
     * the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ReceiveQueueMessageResult receiveQueueMessage(String queuePath, ReceiveMessageOptions options) throws ServiceException {
        return new ReceiveQueueMessageResult(getQueueMessage(queuePath, options));
    }



    /**
     * Sends a topic message.
     *
     * @param topicPath A <code>String</code> object that represents the name of the
     *                  topic to which the message will be sent.
     * @param message   A <code>Message</code> object that represents the message to
     *                  send.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void sendTopicMessage(String topicPath, BrokeredMessage message) throws ServiceException {
        sendMessage(topicPath, message);
    }

    /**
     * Receives a subscription message.
     *
     * @param topicPath        A <code>String</code> object that represents the name of the
     *                         topic to receive.
     * @param subscriptionName A <code>String</code> object that represents the name of the
     *                         subscription from the message will be received.
     * @return A <code>ReceiveSubscriptionMessageResult</code> object that
     * represents the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ReceiveSubscriptionMessageResult receiveSubscriptionMessage(String topicPath, String subscriptionName) throws ServiceException {
        return receiveSubscriptionMessage(topicPath, subscriptionName,
                ReceiveMessageOptions.DEFAULT);
    }

    /**
     * Receives a subscription message using the specified receive message
     * options.
     *
     * @param topicPath        A <code>String</code> object that represents the name of the
     *                         topic to receive.
     * @param subscriptionName A <code>String</code> object that represents the name of the
     *                         subscription from the message will be received.
     * @param options          A <code>ReceiveMessageOptions</code> object that represents
     *                         the receive message options.
     * @return A <code>ReceiveSubscriptionMessageResult</code> object that
     * represents the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ReceiveSubscriptionMessageResult receiveSubscriptionMessage(String topicPath, String subscriptionName, ReceiveMessageOptions options) throws ServiceException {
        BrokeredMessage message = getBrokeredMessage(getSubscriptionPath(topicPath, subscriptionName));
        return new ReceiveSubscriptionMessageResult(message);
    }


    /**
     * Unlocks a message.
     *
     * @param message A <code>Message</code> object that represents the message to
     *                unlock.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void unlockMessage(BrokeredMessage message) throws ServiceException {
        _dummyLocks.remove(message.getLockLocation());
        message.getBrokerProperties().setLockLocation("");
    }

    /**
     * Sends a message.
     *
     * @param path    A <code>String</code> object that represents the path to which
     *                the message will be sent. This may be the value of a queuePath
     *                or a topicPath.
     * @param message A <code>Message</code> object that represents the message to
     *                send.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void sendMessage(String path, BrokeredMessage message) throws ServiceException {
        setQueueMessage(path,message);
    }

    /**
     * Receives a message.
     *
     * @param path A <code>String</code> object that represents the path from
     *             which a message will be received. This may either be the value
     *             of queuePath or a combination of the topicPath +
     *             "/subscriptions/" + subscriptionName.
     * @return A <code>ReceiveSubscriptionMessageResult</code> object that
     * represents the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ReceiveMessageResult receiveMessage(String path) throws ServiceException {
        return receiveMessage(path, ReceiveMessageOptions.DEFAULT);
    }

    /**
     * Receives a message using the specified receive message options.
     *
     * @param path    A <code>String</code> object that represents the path from
     *                which a message will be received. This may either be the value
     *                of queuePath or a combination of the topicPath +
     *                "/subscriptions/" + subscriptionName.
     * @param options A <code>ReceiveMessageOptions</code> object that represents
     *                the receive message options.
     * @return A <code>ReceiveSubscriptionMessageResult</code> object that
     * represents the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ReceiveMessageResult receiveMessage(String path, ReceiveMessageOptions options)
            throws ServiceException {
        return new ReceiveMessageResult(getQueueMessage(path, options));
    }

    /**
     * Deletes a message.
     *
     * @param message A <code>Message</code> object that represents the message to
     *                delete.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void deleteMessage(BrokeredMessage message) throws ServiceException {
        _dummyMq.values().forEach(queue->deleteMessageByLockLocation(queue, message.getLockLocation()));
    }

    /**
     * Creates a queue.
     *
     * @param queueInfo A <code>QueueInfo</code> object that represents the queue to
     *                  create.
     * @return A <code>CreateQueueResult</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public CreateQueueResult createQueue(QueueInfo queueInfo) throws ServiceException {
        _dummyQueueInfos.put(queueInfo.getPath(),queueInfo);
        return new CreateQueueResult(queueInfo);
    }

    /**
     * Deletes a queue.
     *
     * @param queuePath A <code>String</code> object that represents the name of the
     *                  queue to delete.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void deleteQueue(String queuePath) throws ServiceException {
        _dummyQueueInfos.remove(queuePath);
        _dummyMq.remove(queuePath);
    }

    /**
     * Retrieves a queue.
     *
     * @param queuePath A <code>String</code> object that represents the name of the
     *                  queue to retrieve.
     * @return A <code>GetQueueResult</code> object that represents the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public GetQueueResult getQueue(String queuePath) throws ServiceException {
        return new GetQueueResult(_dummyQueueInfos.get(queuePath));
    }

    /**
     * Returns a list of queues.
     *
     * @return A <code>ListQueuesResult</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ListQueuesResult listQueues() throws ServiceException {
        ArrayList<QueueInfo> queues = new ArrayList<>();
        _dummyQueueInfos.values().forEach(queues::add);

        ListQueuesResult result = new ListQueuesResult();
        result.setItems(queues);
        return result;
    }

    /**
     * Returns a list of queues.
     *
     * @param options A <code>ListQueueOptions</code> object that represents the
     *                options to list the queue.
     * @return A <code>ListQueuesResult</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ListQueuesResult listQueues(ListQueuesOptions options) throws ServiceException {
        return listQueues();
    }

    /**
     * Updates the information of a queue.
     *
     * @param queueInfo The information of a queue to be updated.
     * @return A <code>QueueInfo</code> object that represents the updated
     * queue.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public QueueInfo updateQueue(QueueInfo queueInfo) throws ServiceException {
        _dummyQueueInfos.put(queueInfo.getPath(),queueInfo);
        return queueInfo;
    }

    /**
     * Creates an event hub.
     *
     * @param eventHub A <code>EventHub</code> object that represents the event hub to
     *                 create.
     * @return A <code>CreateEventHubResult</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public CreateEventHubResult createEventHub(EventHubInfo eventHub) throws ServiceException {
        _dummyEventHubs.put(eventHub.getPath(),eventHub);
        return new CreateEventHubResult(eventHub);
    }

    /**
     * Deletes an event hub.
     *
     * @param eventHubPath A <code>String</code> object that represents the name of the
     *                     event hub to delete.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void deleteEventHub(String eventHubPath) throws ServiceException {
        _dummyEventHubs.remove(eventHubPath);

    }

    /**
     * Retrieves an event hub.
     *
     * @param eventHubPath A <code>String</code> object that represents the name of the
     *                     event hub to retrieve.
     * @return A <code>GetEventHubResult</code> object that represents the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public GetEventHubResult getEventHub(String eventHubPath) throws ServiceException {
        return new GetEventHubResult(_dummyEventHubs.get(eventHubPath));
    }

    /**
     * Returns a list of event hubs.
     *
     * @return A <code>ListEventHubsResult</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ListEventHubsResult listEventHubs() throws ServiceException {
        ArrayList<EventHubInfo> hubs = new ArrayList<>();
        _dummyEventHubs.values().forEach(hubs::add);
        ListEventHubsResult result = new ListEventHubsResult();
        result.setItems(hubs);
        return result;
    }

    /**
     * Returns a list of event hubs.
     *
     * @param options A <code>ListEventHubsOptions</code> object that represents the
     *                options to list the topic.
     * @return A <code>ListEventHubsOptions</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ListEventHubsResult listEventHubs(ListEventHubsOptions options) throws ServiceException {
        return listEventHubs();
    }

    /**
     * Creates a topic.
     *
     * @param topic A <code>Topic</code> object that represents the topic to
     *              create.
     * @return A <code>CreateTopicResult</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public CreateTopicResult createTopic(TopicInfo topic) throws ServiceException {
        _dummyTopicInfos.put(topic.getPath(),topic);
        return new CreateTopicResult(topic);
    }

    /**
     * Deletes a topic.
     *
     * @param topicPath A <code>String</code> object that represents the name of the
     *                  queue to delete.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void deleteTopic(String topicPath) throws ServiceException {
        _dummyTopicInfos.remove(topicPath);
    }

    /**
     * Retrieves a topic.
     *
     * @param topicPath A <code>String</code> object that represents the name of the
     *                  topic to retrieve.
     * @return A <code>GetTopicResult</code> object that represents the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public GetTopicResult getTopic(String topicPath) throws ServiceException {
        return new GetTopicResult(_dummyTopicInfos.get(topicPath));
    }

    /**
     * Returns a list of topics.
     *
     * @return A <code>ListTopicsResult</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ListTopicsResult listTopics() throws ServiceException {
        ArrayList<TopicInfo> topics = new ArrayList<>();
        _dummyTopicInfos.values().forEach(topics::add);
        ListTopicsResult result = new ListTopicsResult();
        result.setItems(topics);
        return result;
    }

    /**
     * Returns a list of topics.
     *
     * @param options A <code>ListTopicsOptions</code> object that represents the
     *                options to list the topic.
     * @return A <code>ListTopicsResult</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ListTopicsResult listTopics(ListTopicsOptions options) throws ServiceException {
        return listTopics();
    }

    /**
     * Updates a topic.
     *
     * @param topicInfo A <code>TopicInfo</code> object that represents the topic to
     *                  be updated.
     * @return A <code>TopicInfo</code> object that represents the update topic
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public TopicInfo updateTopic(TopicInfo topicInfo) throws ServiceException {
        _dummyTopicInfos.put(topicInfo.getPath(), topicInfo);
        return topicInfo;
    }

    /**
     * Creates a subscription.
     *
     * @param topicPath    A <code>String</code> object that represents the name of the
     *                     topic for the subscription.
     * @param subscription A <code>Subscription</code> object that represents the
     *                     subscription to create.
     * @return A <code>CreateSubscriptionResult</code> object that represents
     * the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public CreateSubscriptionResult createSubscription(String topicPath, SubscriptionInfo subscription) throws ServiceException {
        _dummySubscriptionInfos.put(getSubscriptionPath(topicPath,subscription.getName()), subscription);
        return new CreateSubscriptionResult(subscription);
    }

    /**
     * Deletes a subscription.
     *
     * @param topicPath        A <code>String</code> object that represents the name of the
     *                         topic for the subscription.
     * @param subscriptionName A <code>String</code> object that represents the name of the
     *                         subscription to delete.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void deleteSubscription(String topicPath, String subscriptionName) throws ServiceException {
        _dummySubscriptionInfos.remove(getSubscriptionPath(topicPath,subscriptionName));
    }

    /**
     * Retrieves a subscription.
     *
     * @param topicPath        A <code>String</code> object that represents the name of the
     *                         topic for the subscription.
     * @param subscriptionName A <code>String</code> object that represents the name of the
     *                         subscription to retrieve.
     * @return A <code>GetSubscriptionResult</code> object that represents the
     * result. A <code>String</code> object that represents the name of
     * the subscription to retrieve.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public GetSubscriptionResult getSubscription(String topicPath, String subscriptionName)
            throws ServiceException {
        return new GetSubscriptionResult(_dummySubscriptionInfos.get(getSubscriptionPath(topicPath,subscriptionName)));
    }

    /**
     * Returns a list of subscriptions.
     *
     * @param topicPath A <code>String</code> object that represents the name of the
     *                  topic for the subscriptions to retrieve.
     * @return A <code>ListSubscriptionsResult</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ListSubscriptionsResult listSubscriptions(String topicPath) throws ServiceException {
        ArrayList<SubscriptionInfo> subscriptions = new ArrayList<>();
        Set<String> subscriptionsPaths = _dummySubscriptionInfos.keySet();
        subscriptionsPaths.forEach(subscriptionPath->{
            if (subscriptionPath.startsWith(getSubscriptionPathPrefix(topicPath))){
                subscriptions.add(_dummySubscriptionInfos.get(subscriptionPath));
            }
        });
        ListSubscriptionsResult result = new ListSubscriptionsResult();
        result.setItems(subscriptions);
        return result;
    }

    /**
     * Returns a list of subscriptions.
     *
     * @param topicPath A <code>String</code> object that represents the name of the
     *                  topic for the subscriptions to retrieve.
     * @param options   A <code>ListSubscriptionsOptions</code> object that represents
     *                  the options to list subscriptions.
     * @return A <code>ListSubscriptionsResult</code> object that represents the
     * result.
     * @throws ServiceException the service exception
     */
    @Override
    public ListSubscriptionsResult listSubscriptions(String topicPath, ListSubscriptionsOptions options)
            throws ServiceException {
        return listSubscriptions(topicPath);
    }

    /**
     * Updates a subscription.
     *
     * @param topicName        A <code>String</code> option which represents the name of the
     *                         topic.
     * @param subscriptionInfo A <code>SubscriptionInfo</code> option which represents the
     *                         information of the subscription.
     * @return A <code>SubscriptionInfo</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public SubscriptionInfo updateSubscription(String topicName, SubscriptionInfo subscriptionInfo)
            throws ServiceException {
        _dummySubscriptionInfos.put(getSubscriptionPath(topicName, subscriptionInfo.getName()),subscriptionInfo);
        return subscriptionInfo;
    }

    /**
     * Creates a rule.
     *
     * @param topicPath        A <code>String</code> object that represents the name of the
     *                         topic for the subscription.
     * @param subscriptionName A <code>String</code> object that represents the name of the
     *                         subscription for which the rule will be created.
     * @param rule             A <code>Rule</code> object that represents the rule to create.
     * @return A <code>CreateRuleResult</code> object that represents the
     * result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public CreateRuleResult createRule(String topicPath, String subscriptionName, RuleInfo rule) throws ServiceException {
        _dummyRules.put(getRulePath(topicPath,subscriptionName,rule.getName()),rule);
        return new CreateRuleResult(rule);
    }

    /**
     * Deletes a rule.
     *
     * @param topicPath        A <code>String</code> object that represents the name of the
     *                         topic for the subscription.
     * @param subscriptionName A <code>String</code> object that represents the name of the
     *                         subscription for which the rule will be deleted.
     * @param ruleName         A <code>String</code> object that represents the name of the
     *                         rule to delete.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void deleteRule(String topicPath, String subscriptionName, String ruleName) throws ServiceException {
        _dummyRules.remove(getRulePath(topicPath,subscriptionName,ruleName));
    }

    /**
     * Retrieves a rule.
     *
     * @param topicPath        A <code>String</code> object that represents the name of the
     *                         topic for the subscription.
     * @param subscriptionName A <code>String</code> object that represents the name of the
     *                         subscription for which the rule will be retrieved.
     * @param ruleName         A <code>String</code> object that represents the name of the
     *                         rule to retrieve.
     * @return A <code>GetRuleResult</code> object that represents the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public GetRuleResult getRule(String topicPath, String subscriptionName, String ruleName) throws ServiceException {
        return new GetRuleResult(_dummyRules.get(getRulePath(topicPath,subscriptionName,ruleName)));
    }

    /**
     * Returns a list of rules.
     *
     * @param topicPath        A <code>String</code> object that represents the name of the
     *                         topic for the subscription.
     * @param subscriptionName A <code>String</code> object that represents the name of the
     *                         subscription whose rules are being retrieved.
     * @return A <code>ListRulesResult</code> object that represents the result.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ListRulesResult listRules(String topicPath, String subscriptionName) throws ServiceException {
        ArrayList<RuleInfo> rules = new ArrayList<>();
        Set<String> rulePaths = _dummyRules.keySet();
        rulePaths.forEach(rulePath->{
            if (rulePath.startsWith(getRulePathPrefix(topicPath, subscriptionName))){
                rules.add(_dummyRules.get(rulePath));
            }
        });
        ListRulesResult result = new ListRulesResult();
        result.setItems(rules);
        return result;
    }

    /**
     * Returns a list of rules.
     *
     * @param topicPath        A <code>String</code> object that represents the name of the
     *                         topic for the subscription.
     * @param subscriptionName A <code>String</code> object that represents the name of the
     *                         subscription whose rules are being retrieved.
     * @param options          A <code>ListRulesOptions</code> object that represents the
     *                         options to retrieve rules.
     * @return the list rules result
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public ListRulesResult listRules(String topicPath, String subscriptionName, ListRulesOptions options) throws ServiceException {
        return listRules(topicPath, subscriptionName);
    }

    /**
     * Renew queue lock.
     *
     * @param queueName A <code>String</code> object that represents the name of the
     *                  queue.
     * @param messageId A <code>String</code> object that represents the ID of the
     *                  message.
     * @param lockToken A <code>String</code> object that represents the token of the
     *                  lock.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void renewQueueLock(String queueName, String messageId, String lockToken) throws ServiceException {
        //do nothing
    }

    /**
     * Renew subscription lock.
     *
     * @param topicName         A <code>String</code> object that represents the name of the topic.
     * @param subscriptionName  A <code>String</code> object that represents the name of the Azure subscription
     * @param messageId         A <code>String</code> object that represents the ID of the message.
     * @param lockToken         A <code>String</code> object that represents the token of the lock.
     * @throws ServiceException If a service exception is encountered.
     */
    @Override
    public void renewSubscriptionLock(String topicName, String subscriptionName, String messageId, String lockToken) throws ServiceException {
        //do nothing
    }

    @Override
    public ServiceBusContract withFilter(ServiceFilter serviceFilter) {
        return this;
    }

    @Override
    public ServiceBusContract withRequestFilterFirst(ServiceRequestFilter serviceRequestFilter) {
        return this;
    }

    @Override
    public ServiceBusContract withRequestFilterLast(ServiceRequestFilter serviceRequestFilter) {
        return this;
    }

    @Override
    public ServiceBusContract withResponseFilterFirst(ServiceResponseFilter serviceResponseFilter) {
        return this;
    }

    @Override
    public ServiceBusContract withResponseFilterLast(ServiceResponseFilter serviceResponseFilter) {
        return this;
    }
}

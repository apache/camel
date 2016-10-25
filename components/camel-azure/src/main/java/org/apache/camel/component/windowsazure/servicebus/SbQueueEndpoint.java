package org.apache.camel.component.windowsazure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.models.CreateQueueResult;
import com.microsoft.windowsazure.services.servicebus.models.QueueInfo;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

import java.util.List;

/**
 * Created by alan on 17/10/16.
 */
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
        if (configuration.getAutoDeleteOnIdle()!=null)
            newInfo.setAutoDeleteOnIdle(configuration.getAutoDeleteOnIdle());
        if (configuration.getDeadLetteringOnMessageExpiration()!=null)
            newInfo.setDeadLetteringOnMessageExpiration(configuration.getDeadLetteringOnMessageExpiration());
        if (configuration.getDefaultMessageTimeToLive()!=null)
            newInfo.setDefaultMessageTimeToLive(configuration.getDefaultMessageTimeToLive());
        if (configuration.getDuplicateDetectionHistoryTimeWindow()!=null)
            newInfo.setDuplicateDetectionHistoryTimeWindow(configuration.getDuplicateDetectionHistoryTimeWindow());
        if (configuration.getEnableBatchedOperations()!=null)
            newInfo.setEnableBatchedOperations(configuration.getEnableBatchedOperations());
        if (configuration.getForwardTo()!=null)
            newInfo.setForwardTo(configuration.getForwardTo());
        if (configuration.getIsAnonymousAccessible()!=null)
            newInfo.setIsAnonymousAccessible(configuration.getIsAnonymousAccessible());
        if (configuration.getLockDuration()!=null)
            newInfo.setLockDuration(configuration.getLockDuration());
        if (configuration.getMaxDeliveryCount()!=null)
            newInfo.setMaxDeliveryCount(configuration.getMaxDeliveryCount());
        if (configuration.getMaxSizeInMegabytes()!=null)
            newInfo.setMaxSizeInMegabytes(configuration.getMaxSizeInMegabytes());
        if (configuration.getPartitioningPolicy()!=null)
            newInfo.setPartitioningPolicy(configuration.getPartitioningPolicy());
        if (configuration.getRequiresDuplicateDetection()!=null)
            newInfo.setRequiresDuplicateDetection(configuration.getRequiresDuplicateDetection());
        if (configuration.getRequiresSession()!=null)
            newInfo.setRequiresSession(configuration.getRequiresSession());
        if (configuration.getStatus()!=null)
            newInfo.setStatus(configuration.getStatus());
        if (configuration.getSupportOrdering()!=null)
            newInfo.setSupportOrdering(configuration.getSupportOrdering());
        if (configuration.getUserMetadata()!=null)
            newInfo.setUserMetadata(configuration.getUserMetadata());

        CreateQueueResult createQueueResult = client.createQueue(newInfo);
        if(createQueueResult.getValue() == null){
            throw new Exception("Failed to create a queue <" + configuration.getQueueName() + ">");
        }
    }
    private boolean isQueueExisted() throws ServiceException {
        List<QueueInfo> items = client.listQueues().getItems();
        for (int i = 0; i <items.size() ; i++) {
            if (items.get(i).getPath().equalsIgnoreCase(configuration.getQueueName())){
                return true;
            }
        }
        return false;
    }
}

package org.apache.camel.component.windowsazure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import org.apache.camel.NoFactoryAvailableException;

/**
 * Created by alan on 18/10/16.
 */
public class SbQueueProducer extends AbstractSbProducer {
    public SbQueueProducer(SbQueueEndpoint sbEndpoint) throws NoFactoryAvailableException {
        super(sbEndpoint);
    }

    @Override
    protected void sendMessage(BrokeredMessage brokeredMessage) throws ServiceException {
        getClient().sendQueueMessage(getQueueName(), brokeredMessage);
    }
    private String getQueueName() {
        return getEndpoint().getConfiguration().getQueueName();
    }

}

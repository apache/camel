package org.apache.camel.component.windowsazure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveMessageOptions;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveQueueMessageResult;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;

/**
 * Created by alan on 17/10/16.
 */
public class SbQueueConsumer extends AbstractSbConsumer {
    public SbQueueConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected BrokeredMessage pollBrokeredMessage(ReceiveMessageOptions opts) throws ServiceException {
        ReceiveQueueMessageResult queueMessageResult = getClient().receiveQueueMessage(getConfiguration().getQueueName(), opts);
        return queueMessageResult.getValue();
    }
}

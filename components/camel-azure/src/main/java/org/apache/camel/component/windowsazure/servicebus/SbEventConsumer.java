package org.apache.camel.component.windowsazure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveMessageOptions;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;

/**
 * Created by alan on 18/10/16.
 */
public class SbEventConsumer extends AbstractSbConsumer {
    public SbEventConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected BrokeredMessage pollBrokeredMessage(ReceiveMessageOptions opts) throws ServiceException {
        throw new ServiceException("Event hub consumer is not support yet.");
    }
}

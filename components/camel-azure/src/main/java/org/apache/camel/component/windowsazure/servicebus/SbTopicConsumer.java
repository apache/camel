package org.apache.camel.component.windowsazure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveMessageOptions;
import com.microsoft.windowsazure.services.servicebus.models.ReceiveSubscriptionMessageResult;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;

/**
 * Created by alan on 18/10/16.
 */
public class SbTopicConsumer extends AbstractSbConsumer {
    public SbTopicConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected BrokeredMessage pollBrokeredMessage(ReceiveMessageOptions opts) throws ServiceException {
        System.out.println("$$$$$ SbTopicConsumer#pollBrokeredMessage $$$$$");



        ReceiveSubscriptionMessageResult subscriptionMessageResult = getClient().receiveSubscriptionMessage(getConfiguration().getTopicPath(), getConfiguration().getSubscriptionName(), opts);
        System.out.println("$$$$$ SbTopicConsumer#pollBrokeredMessage $$$$$ topicPath:" + getConfiguration().getTopicPath() + "; SubscriptionName:" + getConfiguration().getSubscriptionName());

        return subscriptionMessageResult.getValue();
    }
}

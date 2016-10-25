package org.apache.camel.component.windowsazure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import org.apache.camel.NoFactoryAvailableException;

/**
 * Created by alan on 18/10/16.
 */
public class SbTopicProducer extends AbstractSbProducer {
    public SbTopicProducer(SbTopicEndpoint sbEndpoint) throws NoFactoryAvailableException {
        super(sbEndpoint);
    }

    @Override
    protected void sendMessage(BrokeredMessage brokeredMessage) throws ServiceException {
        System.out.println("$$$$$ SbTopicProducer#sendMessage $$$$$");

        getClient().sendTopicMessage(getTopicPath(), brokeredMessage);
        System.out.println("$$$$$ SbTopicProducer#sendTopicMessage $$$$$ topicPath:" + getTopicPath());

    }
    private String getTopicPath() {
        return getEndpoint().getConfiguration().getTopicPath();
    }

}

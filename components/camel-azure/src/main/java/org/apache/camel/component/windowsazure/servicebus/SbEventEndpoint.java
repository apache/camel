package org.apache.camel.component.windowsazure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.implementation.EventHubDescription;
import com.microsoft.windowsazure.services.servicebus.models.CreateEventHubResult;
import com.microsoft.windowsazure.services.servicebus.models.EventHubInfo;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

import java.util.List;

/**
 * Created by alan on 17/10/16.
 */
public class SbEventEndpoint extends AbstractSbEndpoint {
    public SbEventEndpoint(String uri, SbComponent component, SbConfiguration configuration) {
        super(uri, component, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AbstractSbConsumer sbConsumer = new SbEventConsumer(this, processor);
        configureConsumer(sbConsumer);
        return sbConsumer;
    }
    @Override
    public Producer createProducer() throws Exception {
        return new SbEventProducer(this);
    }
    private void createEventHub() throws ServiceException {
        EventHubInfo newInfo = new EventHubInfo(configuration.getEventHubPath());
        EventHubDescription eventHubDescription = new EventHubDescription();
        if(configuration.getUserMetadata() != null)
        eventHubDescription.setUserMetadata(configuration.getUserMetadata());
        if(configuration.getDefaultMessageRetention() != null)
            eventHubDescription.setDefaultMessageRetention(configuration.getDefaultMessageRetention());
        newInfo.setModel(eventHubDescription);
        if (configuration.getUserMetadata() != null)
            newInfo.setUserMetadata(configuration.getUserMetadata());

        CreateEventHubResult createEventHubResult = client.createEventHub(newInfo);
        if(createEventHubResult.getValue() == null){
            throw new ServiceException("Failed to create a event hub <" + configuration.getEventHubPath() + ">");
        }    }

    private boolean isEventHubExisted() throws ServiceException {
        List<EventHubInfo> items = client.listEventHubs().getItems();
        for (int i = 0; i <items.size() ; i++) {
            if (items.get(i).getPath().equalsIgnoreCase(configuration.getEventHubPath())){
                return true;
            }
        }
        return false;
    }
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (!isEventHubExisted()) {
            createEventHub();
        }

    }
}

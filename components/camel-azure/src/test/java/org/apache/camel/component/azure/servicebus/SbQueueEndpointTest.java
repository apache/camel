package org.apache.camel.component.azure.servicebus;

import com.microsoft.windowsazure.services.servicebus.ServiceBusContract;
import com.microsoft.windowsazure.services.servicebus.models.ListQueuesResult;
import com.microsoft.windowsazure.services.servicebus.models.QueueInfo;
import org.apache.camel.impl.DefaultCamelContext;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alan on 16/10/16.
 */
public class SbQueueEndpointTest {
    private AbstractSbEndpoint endpoint;
    private ServiceBusContract sbContract;
    @Before
    public void setUp() throws Exception {
        sbContract = EasyMock.createMock(ServiceBusContract.class);

        SbConfiguration config = new SbConfiguration();
        config.setServiceBusContract(sbContract);
        config.setQueueName("MyQueue");
        config.setEntities(EntityType.QUEUE);

        endpoint = new SbQueueEndpoint("azure-sb://queue?queueName=MyQueue", new SbComponent(new DefaultCamelContext()), config);

    }
    @Test
    public void doStartShouldNotCallUpdateQueueAttributesIfQueueExistAndNoOptionIsSpecified() throws Exception {
        List<QueueInfo>items = new ArrayList<>();
        ListQueuesResult lqr = new ListQueuesResult();
        items.add(new QueueInfo("MyQueue"));
        lqr.setItems(items);
        EasyMock.expect(sbContract.listQueues())
                .andReturn(lqr);

        EasyMock.replay(sbContract);

        endpoint.doStart();

        EasyMock.verify(sbContract);
    }


}

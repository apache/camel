package org.apache.camel.component.azure.servicebus;

import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import com.microsoft.windowsazure.services.servicebus.models.CreateQueueResult;
import com.microsoft.windowsazure.services.servicebus.models.GetQueueResult;
import com.microsoft.windowsazure.services.servicebus.models.QueueInfo;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Created by alan on 16/10/16.
 */
public class SbQueueEndpointUseExistingQueueTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint mock;

    @Test
    public void defaultsToDisabled() throws Exception {
        this.mock.expectedMessageCount(1);

        assertMockEndpointsSatisfied(); // Wait for message to arrive.
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();

        AzureSbContractMock clientMock = new SbQueueEndpointUseExistingQueueTest.AzureSbContractMock();
        registry.bind("MyServiceBusContract", clientMock);

        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("azure-sb://queue?queueName=MyQueue&ServiceBusContract=#MyServiceBusContract")
                        .to("mock:result");
            }
        };
    }

    static class AzureSbContractMock extends ServiceBusContractMock {

        AzureSbContractMock() {
            super();
        }

        @Override
        public GetQueueResult getQueue(String queuePath) throws ServiceException {
            return new GetQueueResult(new QueueInfo(queuePath));
        }

        @Override
        public CreateQueueResult createQueue(QueueInfo queueInfo) throws ServiceException {
            throw new ServiceException("forced exception for test if this method is called");
        }

        @Override
        protected BrokeredMessage getBrokeredMessage(String queuePath) {
            BrokeredMessage result = new BrokeredMessage("This is my message.");

            return result;
        }
    }
}

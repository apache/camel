package org.apache.camel.component.azure.servicebus;

import com.microsoft.windowsazure.services.servicebus.models.BrokeredMessage;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class SbBatchConsumerTest extends CamelTestSupport {
    @EndpointInject(uri = "mock:result")
    private MockEndpoint mock;

    @Test
    public void receiveBatch() throws Exception {
        mock.expectedMessageCount(5);
        assertMockEndpointsSatisfied();
        mock.message(0).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(0);
        mock.message(1).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(1);
        mock.message(2).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(2);
        mock.message(3).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(3);
        mock.message(4).exchangeProperty(Exchange.BATCH_INDEX).isEqualTo(4);
        mock.message(0).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(1).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(2).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(3).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(false);
        mock.message(4).exchangeProperty(Exchange.BATCH_COMPLETE).isEqualTo(true);
        mock.expectedPropertyReceived(Exchange.BATCH_SIZE, 5);
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        ServiceBusContractMock myServiceBusContractMock = new ServiceBusContractMock();
        // add 6 messages, one more we will poll
        for (int counter = 0; counter < 6; counter++) {
            BrokeredMessage message = new BrokeredMessage("Message " + counter);
            message.setMessageId("f6fb6f99-5eb2-4be4-9b15-144774141458");
            myServiceBusContractMock.setQueueMessage("MyQueue", message);
        }
        registry.bind("MyServiceBusContract", myServiceBusContractMock);
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("azure-sb://queue?queueName=MyQueue&ServiceBusContract=#MyServiceBusContract&timeout=2000&peekLock=true")
                        .to("mock:result");
            }
        };
    }

}

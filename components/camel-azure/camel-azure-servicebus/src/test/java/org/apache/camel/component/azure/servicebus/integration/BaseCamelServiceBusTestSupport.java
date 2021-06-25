package org.apache.camel.component.azure.servicebus.integration;

import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;
import org.apache.camel.CamelContext;
import org.apache.camel.component.azure.servicebus.ServiceBusComponent;
import org.apache.camel.component.azure.servicebus.ServiceBusTestUtils;
import org.apache.camel.component.azure.servicebus.ServiceBusType;
import org.apache.camel.test.junit5.CamelTestSupport;

public class BaseCamelServiceBusTestSupport extends CamelTestSupport {

    protected ServiceBusSenderAsyncClient senderAsyncClient;
    protected ServiceBusReceiverAsyncClient receiverAsyncClient;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        senderAsyncClient = ServiceBusTestUtils.createServiceBusSenderAsyncClient(ServiceBusType.topic);
        receiverAsyncClient = ServiceBusTestUtils.createServiceBusReceiverAsyncClient(ServiceBusType.topic);

        final CamelContext context = super.createCamelContext();
        final ServiceBusComponent component = new ServiceBusComponent(context);

        component.init();
        component.getConfiguration().setReceiverAsyncClient(receiverAsyncClient);
        component.getConfiguration().setSenderAsyncClient(senderAsyncClient);
        context.addComponent("azure-servicebus", component);

        return context;
    }
}

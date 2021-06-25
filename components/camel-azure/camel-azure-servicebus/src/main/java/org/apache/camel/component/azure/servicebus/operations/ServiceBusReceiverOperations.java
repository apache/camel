package org.apache.camel.component.azure.servicebus.operations;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import org.apache.camel.component.azure.servicebus.client.ServiceBusReceiverAsyncClientWrapper;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Flux;

public class ServiceBusReceiverOperations {

    private final ServiceBusReceiverAsyncClientWrapper client;

    public ServiceBusReceiverOperations(final ServiceBusReceiverAsyncClientWrapper client) {
        ObjectHelper.notNull(client, "client");

        this.client = client;
    }

    public Flux<ServiceBusReceivedMessage> receiveMessages() {
        return client.receiveMessages();
    }

    public Flux<ServiceBusReceivedMessage> peekMessages(final Integer numMaxMessages) {
        if (ObjectHelper.isEmpty(numMaxMessages)) {
            return client.peekMessage()
                    .flux();
        }

        return client.peekMessages(numMaxMessages);
    }
}

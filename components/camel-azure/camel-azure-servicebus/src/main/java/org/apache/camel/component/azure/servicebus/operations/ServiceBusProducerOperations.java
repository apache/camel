package org.apache.camel.component.azure.servicebus.operations;

import java.time.OffsetDateTime;

import com.azure.messaging.servicebus.ServiceBusTransactionContext;
import org.apache.camel.component.azure.servicebus.ServiceBusConfigurationOptionsProxy;
import org.apache.camel.component.azure.servicebus.client.ServiceBusSenderAsyncClientWrapper;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Mono;

public class ServiceBusProducerOperations {

    private final ServiceBusSenderAsyncClientWrapper client;

    public ServiceBusProducerOperations(ServiceBusSenderAsyncClientWrapper client) {
        ObjectHelper.isNotEmpty(client);

        this.client = client;
    }

    public Mono<Void> sendMessages(final Object data, final ServiceBusTransactionContext context) {

        return null;
    }


    public Mono<Long> scheduleMessages(final Object data, final OffsetDateTime scheduledEnqueueTime, final ServiceBusTransactionContext context) {

        return null;
    }
}

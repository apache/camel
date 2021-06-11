package org.apache.camel.component.azure.servicebus;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusMessage;

public class ServiceBusUtils {

    private ServiceBusUtils() {
    }

    public static ServiceBusMessage createServiceBusMessage(final Object data) {
        if (data instanceof String) {
            return new ServiceBusMessage((String) data);
        } else if (data instanceof byte[]) {
            return new ServiceBusMessage((byte[]) data);
        } else if (data instanceof BinaryData) {
            return new ServiceBusMessage((BinaryData) data);
        } else {
            return new ServiceBusMessage(BinaryData.fromObject(data));
        }
    }

    public static Iterable<ServiceBusMessage> createServiceBusMessages(final Iterable<Object> data) {
        return StreamSupport.stream(data.spliterator(), false)
                .map(ServiceBusUtils::createServiceBusMessage)
                .collect(Collectors.toList());
    }
}

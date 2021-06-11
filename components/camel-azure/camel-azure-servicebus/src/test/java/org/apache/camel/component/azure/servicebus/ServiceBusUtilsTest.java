package org.apache.camel.component.azure.servicebus;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.StreamSupport;

import com.azure.messaging.servicebus.ServiceBusMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ServiceBusUtilsTest {

    @Test
    void testCreateServiceBusMessage() {
        // test string
        final ServiceBusMessage message1 = ServiceBusUtils.createServiceBusMessage("test string");

        assertEquals("test string", message1.getBody().toString());

        // test int
        final ServiceBusMessage message2 = ServiceBusUtils.createServiceBusMessage(String.valueOf(12345));

        assertEquals("12345", message2.getBody().toString());
    }

    @Test
    void testCreateServiceBusMessages() {
        final List<Object> inputMessages = new LinkedList<>();
        inputMessages.add("test data");
        inputMessages.add(String.valueOf(12345));

        final Iterable<ServiceBusMessage> busMessages = ServiceBusUtils.createServiceBusMessages(inputMessages);

        assertTrue(StreamSupport.stream(busMessages.spliterator(), false)
                .anyMatch(record -> record.getBody().toString().equals("test data")));
        assertTrue(StreamSupport.stream(busMessages.spliterator(), false)
                .anyMatch(record -> record.getBody().toString().equals("12345")));
    }
}

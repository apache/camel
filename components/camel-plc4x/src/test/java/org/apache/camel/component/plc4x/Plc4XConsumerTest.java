package org.apache.camel.component.plc4x;

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Plc4XConsumerTest {

    private Plc4XEndpoint endpoint;
    private Processor processor;
    private Plc4XConsumer consumer;

    @BeforeEach
    void setUp() {
        endpoint = mock(Plc4XEndpoint.class);
        processor = mock(Processor.class);

        when(endpoint.getTags()).thenReturn(Collections.emptyMap());
        when(endpoint.getTrigger()).thenReturn(null); // untriggered
        when(endpoint.getCamelContext()).thenReturn(new DefaultCamelContext());

        consumer = new Plc4XConsumer(endpoint, processor);
    }


    @Test
    void doStart() throws Exception {
        doNothing().when(endpoint).setupConnection();

        consumer.doStart();

        verify(endpoint, times(1)).setupConnection();
    }

    @Test
    void doStartBadStart() throws Exception {
        doThrow(new PlcConnectionException("fail"))
                .when(endpoint).setupConnection();

        consumer.doStart();

        verify(endpoint).setupConnection();
        assertFalse(consumer.isStarted());
    }

    @Test
    void doStop() throws Exception {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        var field = Plc4XConsumer.class.getDeclaredField("future");
        field.setAccessible(true);
        field.set(consumer, future);

        consumer.doStop();

        verify(future).cancel(true);
    }

}

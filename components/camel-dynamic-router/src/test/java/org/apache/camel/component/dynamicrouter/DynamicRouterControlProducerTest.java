package org.apache.camel.component.dynamicrouter;

import org.apache.camel.component.dynamicrouter.support.DynamicRouterTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.when;

class DynamicRouterControlProducerTest extends DynamicRouterTestSupport {

    @BeforeEach
    void localSetup() throws Exception {
        super.setup();
        controlProducer = new DynamicRouterControlProducer(endpoint);
    }

    @Test
    void testProcessSynchronous() {
        when(endpoint.getConfiguration().isSynchronous()).thenReturn(true);
        boolean result = controlProducer.process(exchange, asyncCallback);
        Assertions.assertTrue(result);
    }

    @Test
    void testProcessAynchronous() {
        when(endpoint.getConfiguration().isSynchronous()).thenReturn(false);
        boolean result = controlProducer.process(exchange, asyncCallback);
        Assertions.assertTrue(result);
    }
}

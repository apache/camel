package org.apache.camel.component.paho.mqtt5.integration;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeExtension;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Synchronization;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

public class PahoMqtt5ManualAcksIT extends PahoMqtt5ITSupport {
    @EndpointInject("mock:test")
    MockEndpoint mock;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:test")
                        .to("paho-mqtt5:queue?brokerUrl=tcp://localhost:" + mqttPort + "&qos=2&manualAcksEnabled=true");
                from("paho-mqtt5:queue?brokerUrl=tcp://localhost:" + mqttPort + "&qos=2&manualAcksEnabled=true")
                        .to("mock:test");
            }
        };
    }

    @Test
    public void testSynchronizationCallbackForManualAcks() throws Exception {

        Exchange exchange = mock(Exchange.class);
        ExchangeExtension exchangeExtension = mock(ExchangeExtension.class);
        when(exchange.getExchangeExtension()).thenReturn(exchangeExtension);

        mock.expectedMessageCount(1);
        template.sendBody("direct:test", "Test Message");

        verify(exchangeExtension).addOnCompletion(any(Synchronization.class));
    }
}

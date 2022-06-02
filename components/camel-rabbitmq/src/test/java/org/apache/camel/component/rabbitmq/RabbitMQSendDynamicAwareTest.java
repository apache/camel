package org.apache.camel.component.rabbitmq;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RabbitMQSendDynamicAwareTest extends CamelTestSupport {

    RabbitMQSendDynamicAware rabbitMQSendDynamicAware;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        this.rabbitMQSendDynamicAware = new RabbitMQSendDynamicAware();
    }

    @Test
    public void testUriParsing() throws Exception {
        this.rabbitMQSendDynamicAware.setScheme("rabbitmq");
        Exchange exchange = createExchangeWithBody("The Body");
        SendDynamicAware.DynamicAwareEntry entry = new SendDynamicAware.DynamicAwareEntry("rabbitmq:destination", "rabbitmq:${header.test}", null, null);
        Processor processor = this.rabbitMQSendDynamicAware.createPreProcessor(createExchangeWithBody("Body"), entry);
        processor.process(exchange);
        assertEquals("destination", exchange.getMessage().getHeader(RabbitMQConstants.EXCHANGE_OVERRIDE_NAME));
    }

    @Test
    public void testSlashedUriParsing() throws Exception {
        this.rabbitMQSendDynamicAware.setScheme("rabbitmq");
        Exchange exchange = createExchangeWithBody("The Body");
        SendDynamicAware.DynamicAwareEntry entry = new SendDynamicAware.DynamicAwareEntry("rabbitmq://destination", "rabbitmq://${header.test}", null, null);
        Processor processor = this.rabbitMQSendDynamicAware.createPreProcessor(createExchangeWithBody("Body"), entry);
        processor.process(exchange);
        assertEquals("destination", exchange.getMessage().getHeader(RabbitMQConstants.EXCHANGE_OVERRIDE_NAME));
    }
}

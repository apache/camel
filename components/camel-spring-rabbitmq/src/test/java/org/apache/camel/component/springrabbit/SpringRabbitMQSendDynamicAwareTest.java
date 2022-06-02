package org.apache.camel.component.springrabbit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SpringRabbitMQSendDynamicAwareTest extends CamelTestSupport {

    SpringRabbitMQSendDynamicAware springRabbitMQSendDynamicAware;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        this.springRabbitMQSendDynamicAware = new SpringRabbitMQSendDynamicAware();
    }

    @Test
    public void testUriParsing() throws Exception {
        this.springRabbitMQSendDynamicAware.setScheme("rabbitmq");
        Exchange exchange = createExchangeWithBody("The Body");
        SendDynamicAware.DynamicAwareEntry entry = new SendDynamicAware.DynamicAwareEntry("rabbitmq:destination", "rabbitmq:${header.test}", null, null);
        Processor processor = this.springRabbitMQSendDynamicAware.createPreProcessor(createExchangeWithBody("Body"), entry);
        processor.process(exchange);
        assertEquals("destination", exchange.getMessage().getHeader(SpringRabbitMQConstants.EXCHANGE_OVERRIDE_NAME));
    }

    @Test
    public void testSlashedUriParsing() throws Exception {
        this.springRabbitMQSendDynamicAware.setScheme("rabbitmq");
        Exchange exchange = createExchangeWithBody("The Body");
        SendDynamicAware.DynamicAwareEntry entry = new SendDynamicAware.DynamicAwareEntry("rabbitmq://destination", "rabbitmq://${header.test}", null, null);
        Processor processor = this.springRabbitMQSendDynamicAware.createPreProcessor(createExchangeWithBody("Body"), entry);
        processor.process(exchange);
        assertEquals("destination", exchange.getMessage().getHeader(SpringRabbitMQConstants.EXCHANGE_OVERRIDE_NAME));
    }
}

package org.apache.camel.component.vertx.kafka;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxKafkaSendDynamicAwareTest extends CamelTestSupport {

    VertxKafkaSendDynamicAware vertxKafkaSendDynamicAware;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        this.vertxKafkaSendDynamicAware = new VertxKafkaSendDynamicAware();
    }

    @Test
    public void testUriParsing() throws Exception {
        this.vertxKafkaSendDynamicAware.setScheme("vertx-kafka");
        Exchange exchange = createExchangeWithBody("The Body");
        SendDynamicAware.DynamicAwareEntry entry = new SendDynamicAware.DynamicAwareEntry("vertx-kafka:destination", "vertx-kafka:${header.test}", null, null);
        Processor processor = this.vertxKafkaSendDynamicAware.createPreProcessor(createExchangeWithBody("Body"), entry);
        processor.process(exchange);
        assertEquals("destination", exchange.getMessage().getHeader(VertxKafkaConstants.OVERRIDE_TOPIC));
    }

    @Test
    public void testSlashedUriParsing() throws Exception {
        this.vertxKafkaSendDynamicAware.setScheme("vertx-kafka");
        Exchange exchange = createExchangeWithBody("The Body");
        SendDynamicAware.DynamicAwareEntry entry = new SendDynamicAware.DynamicAwareEntry("vertx-kafka://destination", "vertx-kafka://${header.test}", null, null);
        Processor processor = this.vertxKafkaSendDynamicAware.createPreProcessor(createExchangeWithBody("Body"), entry);
        processor.process(exchange);
        assertEquals("destination", exchange.getMessage().getHeader(VertxKafkaConstants.OVERRIDE_TOPIC));
    }
}

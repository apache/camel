package org.apache.camel.component.sjms;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SjmsSendDynamicAwareTest extends CamelTestSupport {

    SjmsSendDynamicAware sjmsSendDynamicAware;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        this.sjmsSendDynamicAware = new SjmsSendDynamicAware();
    }

    @Test
    public void testUriParsing() throws Exception {
        this.sjmsSendDynamicAware.setScheme("sjms");
        Exchange exchange = createExchangeWithBody("The Body");
        SendDynamicAware.DynamicAwareEntry entry = new SendDynamicAware.DynamicAwareEntry("sjms:destination", "sjms:${header.test}", null, null);
        Processor processor = this.sjmsSendDynamicAware.createPreProcessor(createExchangeWithBody("Body"), entry);
        processor.process(exchange);
        assertEquals("destination", exchange.getMessage().getHeader(SjmsConstants.JMS_DESTINATION_NAME));
    }

    @Test
    public void testSlashedUriParsing() throws Exception {
        this.sjmsSendDynamicAware.setScheme("sjms");
        Exchange exchange = createExchangeWithBody("The Body");
        SendDynamicAware.DynamicAwareEntry entry = new SendDynamicAware.DynamicAwareEntry("sjms://destination", "sjms://${header.test}", null, null);
        Processor processor = this.sjmsSendDynamicAware.createPreProcessor(createExchangeWithBody("Body"), entry);
        processor.process(exchange);
        assertEquals("destination", exchange.getMessage().getHeader(SjmsConstants.JMS_DESTINATION_NAME));
    }
}

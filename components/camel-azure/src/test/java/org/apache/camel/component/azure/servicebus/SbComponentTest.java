package org.apache.camel.component.azure.servicebus;

import org.apache.camel.*;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Created by alan on 14/10/16.
 */
public class SbComponentTest extends CamelTestSupport {

    @EndpointInject(uri = "direct:start")
    private ProducerTemplate template;

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    @Test
    public void sendInOnly() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });

        assertMockEndpointsSatisfied();

        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals("This is my message text.", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.BROKER_PROPERTIES));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.CONTENT_TYPE));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.DATE));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.CUSTOM_PROPERTIES));

        assertNotNull(exchange.getIn().getHeader(SbConstants.BROKER_PROPERTIES));
        assertEquals("This is my message text.", exchange.getIn().getBody());
    }

    @Test
    public void sendInOut() throws Exception {
        result.expectedMessageCount(1);

        Exchange exchange = template.send("direct:start", ExchangePattern.InOut, new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });

        assertMockEndpointsSatisfied();

        Exchange resultExchange = result.getExchanges().get(0);
        assertEquals("This is my message text.", resultExchange.getIn().getBody());
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.BROKER_PROPERTIES));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.CONTENT_TYPE));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.DATE));
        assertNotNull(resultExchange.getIn().getHeader(SbConstants.CUSTOM_PROPERTIES));

        assertNotNull(exchange.getOut().getHeader(SbConstants.BROKER_PROPERTIES));
        assertEquals("This is my message text.", exchange.getOut().getBody());
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("MyServiceBusContract", new ServiceBusContractMock());

        return registry;
    }


}

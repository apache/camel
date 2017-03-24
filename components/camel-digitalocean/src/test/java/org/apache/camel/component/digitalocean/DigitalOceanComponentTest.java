package org.apache.camel.component.digitalocean;

import com.myjeeva.digitalocean.impl.DigitalOceanClient;
import com.myjeeva.digitalocean.pojo.Account;
import org.apache.camel.component.digitalocean.constants.DigitalOceanHeaders;
import org.apache.camel.component.digitalocean.constants.DigitalOceanOperations;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class DigitalOceanComponentTest extends CamelTestSupport {



    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:getAccountInfo")
                        .setHeader(DigitalOceanHeaders.OPERATION, constant(DigitalOceanOperations.get))
                        .to("digitalocean:account?digitalOceanClient=#digitalOceanClient")
                        .to("mock:result");
            }
        };
    }

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint mockResultEndpoint;

    @Test
    public void testGetAccountInfo() throws Exception {

        mockResultEndpoint.expectedMinimumMessageCount(1);
        Exchange exchange = template.request("direct:getAccountInfo", null);
        assertMockEndpointsSatisfied();
        assertIsInstanceOf(Account.class, exchange.getOut().getBody());
        assertEquals(exchange.getIn().getBody(Account.class).getEmail(), "camel@apache.org");
    }




    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        DigitalOceanClient digitalOceanClient = new DigitalOceanClientMock();
        registry.bind("digitalOceanClient", digitalOceanClient);
        return registry;
    }
}

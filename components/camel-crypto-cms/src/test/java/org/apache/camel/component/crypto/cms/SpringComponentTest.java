package org.apache.camel.component.crypto.cms;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spring.SpringCamelContext;

public class SpringComponentTest extends ComponentTest {

    protected CamelContext createCamelContext() throws Exception {

        return SpringCamelContext.springCamelContext("SpringCryptoCmsTests.xml");
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        return super.createRegistry();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // no routes added by default
            }
        };
    }

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {};
    }

}

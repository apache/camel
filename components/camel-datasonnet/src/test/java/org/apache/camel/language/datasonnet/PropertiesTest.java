package org.apache.camel.language.datasonnet;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Properties;

public class PropertiesTest extends CamelTestSupport {
    @Test
    public void testPropertiesBuiltin() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:camel");
        mock.expectedBodiesReceived("bar");

        template.send("direct:in", new DefaultExchange(context));

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                Properties prop = new Properties();
                prop.setProperty("foo", "bar");
                context.getPropertiesComponent().setInitialProperties(prop);

                from("direct:in")
                        .setBody(datasonnet("cml.properties('foo')", "text/plain", "text/plain"))
                        .to("mock:camel");
            }
        };
    }
}

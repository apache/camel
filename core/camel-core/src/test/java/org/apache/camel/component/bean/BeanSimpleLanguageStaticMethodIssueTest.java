package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class BeanSimpleLanguageStaticMethodIssueTest extends ContextTestSupport {

    @Test
    public void testStaticMethod() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");
        getMockEndpoint("mock:result").message(0).exchangeProperty("foo").isNotNull();
        getMockEndpoint("mock:result").message(0).exchangeProperty("bar").isNotNull();

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .setProperty("foo").method(System.class, "currentTimeMillis")
                    .setProperty("bar").simple("${bean:type:java.lang.System?method=currentTimeMillis}")
                    .to("mock:result");
            }
        };
    }
}

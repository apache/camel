package org.apache.camel.component.jms.issues;

import java.util.HashMap;
import java.util.Map;

import static org.apache.activemq.camel.component.ActiveMQComponent.activeMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 *
 */
public class JmsHeaderAsObjectTest extends ContextTestSupport {

    public void testSendHeaderAsPrimitiveOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).header("foo").isEqualTo("bar");
        mock.message(0).header("number").isEqualTo(23);

        Map headers = new HashMap();
        headers.put("foo", "bar");
        headers.put("number", 23);
        template.sendBodyAndHeaders("activemq:in", "Hello World", headers);

        mock.assertIsSatisfied();
    }

    public void testSendHeaderAsObject() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        mock.message(0).header("foo").isEqualTo("bar");
        mock.message(0).header("order").isNull();

        DummyOrder order = new DummyOrder();
        order.setItemId(4444);
        order.setOrderId(333);
        order.setQuantity(2);

        Map headers = new HashMap();
        headers.put("foo", "bar");
        headers.put("order", order);
        template.sendBodyAndHeaders("activemq:in", "Hello World", headers);

        mock.assertIsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        camelContext.addComponent("activemq", activeMQComponent("vm://localhost?broker.persistent=false&broker.useJmx=false"));
        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:in").to("mock:result");
            }
        };
    }
}

package org.apache.camel.component.jms.issues;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;

/**
 * Unit test to verify DLC and JSM based on user reporting
 */
@ContextConfiguration
public class JmsRedeliveryWithInitialRedeliveryDelayTest extends AbstractJUnit38SpringContextTests {

    @Autowired
    protected CamelContext context;

    public void testDLCSpringConfiguredRedeliveryPolicy() throws Exception {
        MockEndpoint dead = context.getEndpoint("mock:dead", MockEndpoint.class);
        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);

        dead.expectedBodiesReceived("Hello World");
        dead.message(0).header("org.apache.camel.Redelivered").isEqualTo(true);
        dead.message(0).header("org.apache.camel.RedeliveryCounter").isEqualTo(4);
        result.expectedMessageCount(0);

        context.createProducerTemplate().sendBody("activemq:in", "Hello World");

        result.assertIsSatisfied();
        dead.assertIsSatisfied();
    }
}

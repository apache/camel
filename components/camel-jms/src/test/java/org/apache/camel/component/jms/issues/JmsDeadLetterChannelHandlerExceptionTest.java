package org.apache.camel.component.jms.issues;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import javax.jms.ConnectionFactory;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentTransacted;

public class JmsDeadLetterChannelHandlerExceptionTest extends CamelTestSupport {
    public class BadErrorHandler {
        @Handler
        public void onException(Exchange exchange, Exception exception) throws Exception {
            throw new RuntimeException("error in errorhandler");
        }
    }

    private final String testingEndpoint = "activemq:test." + getClass().getName();

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("bean:org.apache.camel.component.jms.issues.JmsDeadLetterChannelHandlerExceptionTest.BadErrorHandler"));

                from(testingEndpoint).throwException(new RuntimeException("bad error"));
            }
        };
    }

    @Test
    public void should_not_lose_messages_on_exception_in_errorhandler() throws Exception {
        String message = getTestMethodName();
        template.sendBody(testingEndpoint, message);

        Thread.sleep(3000);

        Object dlqBody = consumer.receiveBody("activemq:ActiveMQ.DLQ", 3000);
        Object testingEndpointBody = consumer.receiveBody(testingEndpoint, 3000);
        Object testingEndpointSpecificDlqBody = consumer.receiveBody(testingEndpoint + ".DLQ", 3000);

        assertTrue("no messages in any queues", dlqBody != null || testingEndpointBody != null || testingEndpointSpecificDlqBody != null);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentTransacted(connectionFactory));
        return camelContext;
    }
}

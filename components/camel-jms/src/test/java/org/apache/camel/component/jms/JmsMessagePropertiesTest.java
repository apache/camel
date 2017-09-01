package org.apache.camel.component.jms;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Tests for unsupported JMS property types such as byte[].
 * JMS provider only needs to support standard JMS property types as per
 * @see http://java.sun.com/j2ee/1.4/docs/api/javax/jms/Message.html
 * However certain JMS providers allow extensions that offer non-JMS compliant
 * property types to be set on a JMS message. We want to allow such extensions
 * and leave it to the JMS provider to reject any message types that the 
 * provider does not support.
 */
public class JmsMessagePropertiesTest extends CamelTestSupport {

    /** 
     * Testing against embedded ActiveMQ broker, it should not allow 
     * properties of type byte[] or of type JmsMessagePropertiesTest.
     * @throws Exception
     */
    @Test
    public void testUnsupportedMessagePropertyTypes() throws Exception {
        Date now = new Date();
        byte[] bArray = new byte[] {1,2,3,4,5,6,7,8,9,0};
        getMockEndpoint("mock:foo").expectedBodiesReceived("World");
        getMockEndpoint("mock:done").expectedBodiesReceived("World");
        getMockEndpoint("mock:foo").expectedHeaderReceived("MyBigIntegerHeader", "1");
        getMockEndpoint("mock:foo").expectedHeaderReceived("MyBigDecimalHeader", "1");
        getMockEndpoint("mock:foo").expectedHeaderReceived("MyCharSeqHeader", "1");
        getMockEndpoint("mock:foo").expectedHeaderReceived("MyDateHeader", now.toString());
        getMockEndpoint("mock:foo").expectedHeaderReceived("MyByteArrayHeader", null);
        getMockEndpoint("mock:foo").expectedHeaderReceived("MyUnsupportedHeader", null);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("MyBigIntegerHeader", new BigInteger("1"));
        headers.put("MyBigDecimalHeader", new BigDecimal("1"));
        headers.put("MyCharSeqHeader", new StringBuffer("1"));
        headers.put("MyDateHeader", now);
        headers.put("MyByteArrayHeader", bArray);
        headers.put("MyUnsupportedHeader", this);
        template.sendBodyAndHeaders("direct:start", ExchangePattern.InOnly, "World", headers);

        assertMockEndpointsSatisfied();
    }


    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("activemq:queue:foo")
                    .to("mock:done");

                from("activemq:queue:foo")
                    .to("log:foo?showAll=true", "mock:foo")
                    .transform(body().prepend("Bye "));
            }
        };
    }
}
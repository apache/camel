package org.apache.camel.component.sjms;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.jms.DefaultDestinationCreationStrategy;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

/**
 * @author
 */
public class SjmsDestinationCreationStrategyTest extends JmsTestSupport {

    private boolean createDestinationCalled = false;
    private boolean createTemporaryDestination = false;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUri);
        SjmsComponent component = new SjmsComponent();
        component.setConnectionFactory(connectionFactory);
        component.setDestinationCreationStrategy(new TestDestinationCreationStrategyTest());
        camelContext.addComponent("sjms", component);
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sjms:queue:inout?prefillPool=false&exchangePattern=InOut").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody("response");
                    }
                });
            }
        };
    }

    @Test
    public void testSjmsComponentUsesCustomDestinationCreationStrategy() throws Exception {
        assertFalse(createDestinationCalled);
        template.sendBody("sjms:queue:inonly?prefillPool=false", "hello world");
        assertTrue(createDestinationCalled);

        assertFalse(createTemporaryDestination);
        String response = (String)template.sendBody("sjms:queue:inout?prefillPool=false&exchangePattern=InOut", ExchangePattern.InOut, "hello world 2" );
        assertTrue(createTemporaryDestination);
        assertEquals("response", response);
    }

    class TestDestinationCreationStrategyTest extends DefaultDestinationCreationStrategy {
        @Override
        public Destination createDestination(Session session, String name, boolean topic) throws JMSException {
            if (name.equals("inonly")) {
                createDestinationCalled = true;
            }
            return super.createDestination(session, name, topic);
        }

        @Override
        public Destination createTemporaryDestination(Session session, boolean topic) throws JMSException {
            createTemporaryDestination = true;
            return super.createTemporaryDestination(session, topic);
        }
    }
}
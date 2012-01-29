package org.apache.camel.itest.jms;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration
public class JmsConsumerShutdownTest extends AbstractJUnit4SpringContextTests {

    @Produce(uri = "activemq:start")
    protected ProducerTemplate activemq;

    @Produce(uri = "seda:start")
    protected ProducerTemplate seda;

    @EndpointInject(uri = "mock:end")
    protected MockEndpoint end;

    @EndpointInject(uri = "mock:exception")
    protected MockEndpoint exception;

    // Camel context will never shut down. Regardless of the settings in DefaultShutdownStrategy
    // JmsConsumer does not correctly shut down direct subroutes
    @Test(timeout = 20000)
    @DirtiesContext
    public void testJmsConsumerShutdownWithMessageInFlight() throws InterruptedException {

        end.expectedMessageCount(0);
        end.setResultWaitTime(2000);

        // direct:dir route always fails
        exception.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new Exception("Kaboom!");
            }
        });

        activemq.sendBody("activemq:start", "Hello");

        end.assertIsSatisfied();
    }

    // For comparison, SedaConsumer will correctly shut down direct subroutes
    @Test(timeout = 20000)
    @DirtiesContext
    public void testSedaConsumerShutdownWithMessageInFlight() throws InterruptedException {

        end.expectedMessageCount(0);
        end.setResultWaitTime(2000);

        // direct:dir route always fails
        exception.whenAnyExchangeReceived(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                throw new Exception("Kaboom!");
            }
        });

        seda.sendBody("activemq:start", "Hello");

        end.assertIsSatisfied();
    }


    public static class MyRouteBuilder extends RouteBuilder {
        @Override
        public void configure() throws Exception {
            from("activemq:start")
                    .to("direct:dir")
                    .to("mock:end");

            from("seda:start")
                    .to("direct:dir")
                    .to("mock:end");

            from("direct:dir")
                    .onException(Exception.class)
                        .redeliveryDelay(1000)
                        .maximumRedeliveries(-1) // forever
                    .end()
                    .to("mock:exception");

        }
    }

}

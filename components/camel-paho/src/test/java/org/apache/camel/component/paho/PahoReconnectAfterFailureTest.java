package org.apache.camel.component.paho;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.broker.BrokerService;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Test;

public class PahoReconnectAfterFailureTest extends CamelTestSupport {

    public static final String TESTING_ROUTE_ID = "testingRoute";
    BrokerService broker;

    int mqttPort = AvailablePortFinder.getNextAvailable();
    CountDownLatch routeStartedLatch = new CountDownLatch(1);

    @EndpointInject("mock:test")
    MockEndpoint mock;

    @Override
    protected boolean useJmx() {
        return false;
    }

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();
        broker = new BrokerService();
        broker.setPersistent(false);
        broker.addConnector("mqtt://localhost:" + mqttPort);
        // Broker will be started later, after camel context is started,
        // to ensure first consumer connection fails
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // Setup supervisor to restart routes because paho consumer 
        // is not able to recover automatically on startup
        SupervisingRouteController supervising = context.getRouteController().supervising();
        supervising.setBackOffDelay(500);
        supervising.setIncludeRoutes("paho:*");
        return context;
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        broker.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:test").to("paho:queue?lazyStartProducer=true&brokerUrl=tcp://localhost:" + mqttPort);
                from("paho:queue?brokerUrl=tcp://localhost:" + mqttPort)
                    .id(TESTING_ROUTE_ID)
                    .routePolicy(new RoutePolicySupport() {
                        @Override
                        public void onStart(Route route) {
                            routeStartedLatch.countDown();
                        }
                    })
                    .to("mock:test");
            }
        };
    }


    @Test
    public void startConsumerShouldReconnectMqttClientAfterFailures() throws Exception {
        RouteController routeController = context.getRouteController();

        assertNotEquals("Broker down, expecting  route not to be started", ServiceStatus.Started, routeController.getRouteStatus(TESTING_ROUTE_ID));

        // Start broker and wait for supervisor to restart route
        // consumer should now connect
        broker.start();
        routeStartedLatch.await(5, TimeUnit.SECONDS);
        assertEquals("Expecting consumer connected to broker and route started", ServiceStatus.Started, routeController.getRouteStatus(TESTING_ROUTE_ID));

        // Given
        String msg = "msg";
        mock.expectedBodiesReceived(msg);

        // When
        template.sendBody("paho:queue?lazyStartProducer=true&brokerUrl=tcp://localhost:" + mqttPort, msg);

        // Then
        mock.assertIsSatisfied();

    }

    @Test
    public void startProducerShouldReconnectMqttClientAfterFailures() throws Exception {
        String msg = "msg";
        mock.expectedBodiesReceived(msg);

        try {
            template.sendBody("direct:test", "notSentMessage");
            fail("Broker is down, paho producer should fail");
        }catch (Exception e) {
            // ignore
        }

        broker.start();

        template.sendBody("direct:test", msg);

        mock.assertIsSatisfied();
    }
}

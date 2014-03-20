/**
 * 
 */
package org.apache.camel.component.ws;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;

/**
 *
 */
public class WsProducerConsumerTest extends CamelTestSupport {
    protected static final String TEST_MESSAGE = "Hello World!";

    protected Server server;
    protected int PORT = AvailablePortFinder.getNextAvailable();
    protected List<Object> messages;
    
    public void startTestServer() throws Exception {
        // start a simple websocket echo service
        server = new Server();
        Connector connector = new SelectChannelConnector();
        connector.setHost("localhost");
        connector.setPort(PORT);
        server.addConnector(connector);
        
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
 
        messages = new ArrayList<Object>();
        ServletHolder servletHolder = new ServletHolder(new TestServlet(messages));
        context.addServlet(servletHolder, "/*");
        
        server.start();
        System.out.println("started");
        assertTrue(server.isStarted());
        
    }
    
    public void stopTestServer() throws Exception {
        server.stop();
        server.destroy();
    }

    @Override
    public void setUp() throws Exception {
        startTestServer();
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        stopTestServer();
    }

    @Test
    public void testTwoRoutes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(TEST_MESSAGE);

        template.sendBody("direct:input", TEST_MESSAGE);

        mock.assertIsSatisfied();
    }

    
    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        RouteBuilder[] rbs = new RouteBuilder[2];
        rbs[0] = new RouteBuilder() {
            public void configure() {
                from("direct:input")
                    .to("ws://localhost:" + PORT);
            }
        };
        rbs[1] = new RouteBuilder() {
            public void configure() {
                from("ws://localhost:" + PORT)
                    .to("mock:result");
            }
        };
        return rbs;
    }
}

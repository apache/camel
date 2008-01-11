package org.apache.camel.component.cxf;

import java.util.ArrayList;
import java.util.List;

import javax.xml.ws.Endpoint;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.hello_world_soap_http.GreeterImpl;

public class CxfProducerRouterTest extends ContextTestSupport {
    private static final transient Log LOG = LogFactory.getLog(CxfProducerRouterTest.class);
    private final static String SIMPLE_SERVER_ADDRESS = "http://localhost:28080/test";
    private ServerImpl simpleServer;
    private final static String ECHO_OPERATION = "echo";
    private final static String TEST_MESSAGE = "Hello World!";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // start a simple front service
        ServerFactoryBean svrBean = new ServerFactoryBean();
        svrBean.setAddress(SIMPLE_SERVER_ADDRESS);
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
        svrBean.setBus(CXFBusFactory.getDefaultBus());

        simpleServer = (ServerImpl)svrBean.create();
        simpleServer.start();


    }

    @Override
    protected void tearDown() throws Exception {
        if (simpleServer != null) {
            simpleServer.stop();
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:EndpointA").to(getSimpleEndpointUri());
            }
        };
    }

    public void testInvokingSimpleServerWithParams() throws Exception {
        Exchange senderExchange = new DefaultExchange(context, ExchangePattern.InOut);
        final List<String> params = new ArrayList<String>();
        params.add(TEST_MESSAGE);
        senderExchange.getIn().setBody(params);
        senderExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, ECHO_OPERATION);

        Exchange exchange = template.send("direct:EndpointA", senderExchange);

        org.apache.camel.Message out = exchange.getOut();
        Object[] output = (Object[])out.getBody();
        LOG.info("Received output text: " + output[0]);
        assertEquals("reply body on Camel", "echo " + TEST_MESSAGE, output[0]);
    }


    private String getSimpleEndpointUri() {
        return "cxf://" + SIMPLE_SERVER_ADDRESS
        + "?serviceClass=org.apache.camel.component.cxf.HelloService";
    }

}

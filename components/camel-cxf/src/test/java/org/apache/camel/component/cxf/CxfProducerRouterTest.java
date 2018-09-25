/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.cxf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.URISupport;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.MessageContentsList;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CxfProducerRouterTest extends CamelTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CxfProducerRouterTest.class);
    private static final String SIMPLE_SERVER_ADDRESS = "http://localhost:" + CXFTestSupport.getPort1() + "/CxfProducerRouterTest/test";
    private static final String REQUEST_MESSAGE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soap:Body><ns1:echo xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
            + "<arg0 xmlns=\"http://cxf.component.camel.apache.org/\">Hello World!</arg0>"
            + "</ns1:echo></soap:Body></soap:Envelope>";
    private static final String REQUEST_PAYLOAD = "<ns1:echo xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
        + "<arg0 xmlns=\"http://cxf.component.camel.apache.org/\">Hello World!</arg0></ns1:echo>";

    private static final String ECHO_OPERATION = "echo";
    private static final String TEST_MESSAGE = "Hello World!";
    
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @BeforeClass
    public static void startServer() throws Exception {        
        // start a simple front service
        ServerFactoryBean svrBean = new ServerFactoryBean();
        svrBean.setAddress(SIMPLE_SERVER_ADDRESS);
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
        svrBean.setBus(BusFactory.getDefaultBus());
        svrBean.create();
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:EndpointA").to(getSimpleEndpointUri());
                from("direct:EndpointB").to(getSimpleEndpointUri() + "&dataFormat=MESSAGE");
                from("direct:EndpointC").to(getSimpleEndpointUri() + "&dataFormat=PAYLOAD");
                // This route is for checking camel-cxf producer throwing exception
                from("direct:start")
                    .doTry()
                         .to("cxf://http://localhost:10000/false?serviceClass=org.apache.camel.component.cxf.HelloService")
                    .doCatch(org.apache.cxf.interceptor.Fault.class)
                         .to("mock:error");
            }
        };
    }
    
    @Test
    public void testCannotSendRequest() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(1);
    
        Exchange senderExchange = new DefaultExchange(context, ExchangePattern.InOut);
        final List<String> params = new ArrayList<>();
        // Prepare the request message for the camel-cxf procedure
        params.add(TEST_MESSAGE);
        senderExchange.getIn().setBody(params);
        senderExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, ECHO_OPERATION);
        template.send("direct:start", senderExchange);
        error.assertIsSatisfied();
    }
    
    @Test
    public void testCxfEndpointUris() throws Exception {
        CxfEndpoint endpoint = context.getEndpoint(getSimpleEndpointUri(), CxfEndpoint.class);
        assertEquals("Get a wrong endpoint uri", getSimpleEndpointUri(), endpoint.getEndpointUri());
        
        endpoint = context.getEndpoint(getSimpleEndpointUri() + "&dataFormat=MESSAGE", CxfEndpoint.class);
        assertEquals("Get a wrong endpoint uri", URISupport.normalizeUri(getSimpleEndpointUri() + "&dataFormat=MESSAGE"), endpoint.getEndpointUri());

    }

    @Test
    public void testInvokingSimpleServerWithParams() throws Exception {
     // START SNIPPET: sending
        Exchange senderExchange = new DefaultExchange(context, ExchangePattern.InOut);
        final List<String> params = new ArrayList<>();
        // Prepare the request message for the camel-cxf procedure
        params.add(TEST_MESSAGE);
        senderExchange.getIn().setBody(params);
        senderExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, ECHO_OPERATION);

        Exchange exchange = template.send("direct:EndpointA", senderExchange);

        org.apache.camel.Message out = exchange.getOut();
        // The response message's body is an MessageContentsList which first element is the return value of the operation,
        // If there are some holder parameters, the holder parameter will be filled in the reset of List.
        // The result will be extract from the MessageContentsList with the String class type
        MessageContentsList result = (MessageContentsList)out.getBody();
        LOG.info("Received output text: " + result.get(0));
        Map<String, Object> responseContext = CastUtils.cast((Map<?, ?>)out.getHeader(Client.RESPONSE_CONTEXT));
        assertNotNull(responseContext);
        assertEquals("We should get the response context here", "UTF-8", responseContext.get(org.apache.cxf.message.Message.ENCODING));
        assertEquals("Reply body on Camel is wrong", "echo " + TEST_MESSAGE, result.get(0));
     // END SNIPPET: sending
    }

    @Test
    public void testInvokingSimpleServerWithMessageDataFormat() throws Exception {
        Exchange senderExchange = new DefaultExchange(context, ExchangePattern.InOut);
        senderExchange.getIn().setBody(REQUEST_MESSAGE);
        Exchange exchange = template.send("direct:EndpointB", senderExchange);

        org.apache.camel.Message out = exchange.getOut();
        String response = out.getBody(String.class);
        assertTrue("It should has the echo message", response.indexOf("echo " + TEST_MESSAGE) > 0);
        assertTrue("It should has the echoResponse tag", response.indexOf("echoResponse") > 0);

    }
    
    @Test
    public void testInvokingSimpleServerWithPayLoadDataFormat() throws Exception {
        Exchange senderExchange = new DefaultExchange(context, ExchangePattern.InOut);
        senderExchange.getIn().setBody(REQUEST_PAYLOAD);
        // We need to specify the operation name to help CxfProducer to look up the BindingOperationInfo
        senderExchange.getIn().setHeader(CxfConstants.OPERATION_NAME, "echo");
        Exchange exchange = template.send("direct:EndpointC", senderExchange);

        org.apache.camel.Message out = exchange.getOut();
        String response = out.getBody(String.class);
        assertTrue("It should has the echo message", response.indexOf("echo " + TEST_MESSAGE) > 0);
        assertTrue("It should has the echoResponse tag", response.indexOf("echoResponse") > 0);
        
        senderExchange = new DefaultExchange(context, ExchangePattern.InOut);
        senderExchange.getIn().setBody(REQUEST_PAYLOAD);
        // Don't specify operation information here
        exchange = template.send("direct:EndpointC", senderExchange);
        
        assertNotNull("Expect exception here.", exchange.getException());
        assertTrue(exchange.getException() instanceof IllegalArgumentException);

    }

    private String getSimpleEndpointUri() {
        return "cxf://" + SIMPLE_SERVER_ADDRESS
            + "?serviceClass=org.apache.camel.component.cxf.HelloService";
    }

}

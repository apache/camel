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

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.ws.Endpoint;

import org.w3c.dom.Document;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.converter.CxfPayloadConverter;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.helpers.CastUtils;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version 
 */
public class CxfProducerTest extends Assert {
    protected static final String ECHO_OPERATION = "echo";
    protected static final String GREET_ME_OPERATION = "greetMe";
    protected static final String TEST_MESSAGE = "Hello World!";

    private static final Logger LOG = LoggerFactory.getLogger(CxfProducerTest.class);

    protected CamelContext camelContext;
    protected ProducerTemplate template;
    protected Server server;
    protected Endpoint endpoint;
    
    protected String getSimpleServerAddress() {
        return "http://localhost:" + CXFTestSupport.getPort1() + "/" + getClass().getSimpleName() + "/test";
    }
    protected String getJaxWsServerAddress() {
        return "http://localhost:" + CXFTestSupport.getPort2() + "/" + getClass().getSimpleName() + "/test";
    }
    protected String getWrongServerAddress() {
        // Avoiding the test error on camel-cxf module
        return "http://localhost:" + AvailablePortFinder.getNextAvailable() + "/" + getClass().getSimpleName() + "/test";
    }
    
    @Before
    public void startService() throws Exception {
        // start a simple front service
        ServerFactoryBean svrBean = new ServerFactoryBean();
        svrBean.setAddress(getSimpleServerAddress());
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
        svrBean.setBus(BusFactory.getDefaultBus());
        server = svrBean.create();
        
        GreeterImpl greeterImpl = new GreeterImpl();
        endpoint = Endpoint.publish(getJaxWsServerAddress(), greeterImpl);
    }
    
    @After
    public void stopServices() throws Exception {
        endpoint.stop();
        server.stop();
        server.destroy();
    }

    @Before
    public void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.start();
        template = camelContext.createProducerTemplate();
    }

    @After
    public void tearDown() throws Exception {
        template.stop();
        camelContext.stop();
    }

    @Test
    public void testInvokingSimpleServerWithParams() throws Exception {
        Exchange exchange = sendSimpleMessage();

        org.apache.camel.Message out = exchange.getOut();
        String result = out.getBody(String.class);
        LOG.info("Received output text: " + result);
        Map<String, Object> responseContext = CastUtils.cast((Map<?, ?>)out.getHeader(Client.RESPONSE_CONTEXT));
        assertNotNull(responseContext);
        assertEquals("We should get the response context here", "UTF-8", responseContext.get(org.apache.cxf.message.Message.ENCODING));
        assertEquals("reply body on Camel", "echo " + TEST_MESSAGE, result);
        
        // check the other camel header copying
        String fileName = out.getHeader(Exchange.FILE_NAME, String.class);
        assertEquals("Should get the file name from out message header", "testFile", fileName);
        
        // check if the header object is turned into String
        Object requestObject = out.getHeader("requestObject");
        assertTrue("We should get the right requestObject.", requestObject instanceof DefaultCxfBinding);
    }

    @Test
    public void testInvokingAWrongServer() throws Exception {
        Exchange reply = sendSimpleMessage(getWrongEndpointUri());
        assertNotNull("We should get the exception here", reply.getException());
        assertTrue(reply.getException().getCause() instanceof ConnectException);
        
        
        //Test the data format PAYLOAD
        reply = sendSimpleMessageWithPayloadMessage(getWrongEndpointUri() + "&dataFormat=PAYLOAD");
        assertNotNull("We should get the exception here", reply.getException());
        assertTrue(reply.getException().getCause() instanceof ConnectException);
        
        //Test the data format MESSAGE
        reply = sendSimpleMessageWithRawMessage(getWrongEndpointUri() + "&dataFormat=MESSAGE");
        assertNotNull("We should get the exception here", reply.getException());
        assertTrue(reply.getException().getCause() instanceof ConnectException);
    }

    @Test
    public void testInvokingJaxWsServerWithParams() throws Exception {
        Exchange exchange = sendJaxWsMessage();

        org.apache.camel.Message out = exchange.getOut();
        String result = out.getBody(String.class);
        LOG.info("Received output text: " + result);
        Map<String, Object> responseContext = CastUtils.cast((Map<?, ?>)out.getHeader(Client.RESPONSE_CONTEXT));
        assertNotNull(responseContext);
        assertEquals("Get the wrong wsdl opertion name", "{http://apache.org/hello_world_soap_http}greetMe", responseContext.get("javax.xml.ws.wsdl.operation").toString());
        assertEquals("reply body on Camel", "Hello " + TEST_MESSAGE, result);
        
        // check the other camel header copying
        String fileName = out.getHeader(Exchange.FILE_NAME, String.class);
        assertEquals("Should get the file name from out message header", "testFile", fileName);
    }

    protected String getSimpleEndpointUri() {
        return "cxf://" + getSimpleServerAddress()
            + "?serviceClass=org.apache.camel.component.cxf.HelloService";
    }

    protected String getJaxwsEndpointUri() {
        return "cxf://" + getJaxWsServerAddress() + "?serviceClass=org.apache.hello_world_soap_http.Greeter";
    }

    protected String getWrongEndpointUri() {
        return "cxf://" + getWrongServerAddress() + "?serviceClass=org.apache.camel.component.cxf.HelloService";
    }

    protected Exchange sendSimpleMessage() {
        return sendSimpleMessage(getSimpleEndpointUri());
    }

    private Exchange sendSimpleMessage(String endpointUri) {
        Exchange exchange = template.request(endpointUri, new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add(TEST_MESSAGE);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, ECHO_OPERATION);
                exchange.getIn().setHeader(Exchange.FILE_NAME, "testFile");
                exchange.getIn().setHeader("requestObject", new DefaultCxfBinding());
            }
        });
        return exchange;

    }
    
    private Exchange sendSimpleMessageWithRawMessage(String endpointUri) {
        Exchange exchange = template.request(endpointUri, new Processor() {
            public void process(final Exchange exchange) {
                exchange.getIn().setBody("<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                 + "<soap:Body><ns1:echo xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
                                 + "<arg0 xmlns=\"http://cxf.component.camel.apache.org/\">hello world</arg0>"
                                 + "</ns1:echo></soap:Body></soap:Envelope>");
            }
        });
        return exchange;
    }
    
    private Exchange sendSimpleMessageWithPayloadMessage(String endpointUri) {
        Exchange exchange = template.request(endpointUri, new Processor() {
            public void process(final Exchange exchange) throws Exception {
                Document document = new XmlConverter().toDOMDocument("<ns1:echo xmlns:ns1=\"http://cxf.component.camel.apache.org/\">"
                                 + "<arg0 xmlns=\"http://cxf.component.camel.apache.org/\">hello world</arg0>"
                                 + "</ns1:echo>", exchange);
                exchange.getIn().setBody(CxfPayloadConverter.documentToCxfPayload(document, exchange));
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, ECHO_OPERATION);
                
            }
        });
        return exchange;
    }
    
    protected Exchange sendJaxWsMessage() {
        Exchange exchange = template.request(getJaxwsEndpointUri(), new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add(TEST_MESSAGE);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, GREET_ME_OPERATION);
                exchange.getIn().setHeader(Exchange.FILE_NAME, "testFile");
            }
        });
        return exchange;
    }
    
    
}

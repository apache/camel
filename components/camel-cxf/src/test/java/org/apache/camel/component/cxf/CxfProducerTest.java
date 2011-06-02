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

import javax.xml.ws.Endpoint;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.helpers.CastUtils;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
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
    protected static final String SIMPLE_SERVER_ADDRESS = "http://localhost:28080/test";
    protected static final String JAXWS_SERVER_ADDRESS = "http://localhost:28081/test";
    protected static final String WRONG_SERVER_ADDRESS = "http://localhost:9999/test";

    private static final transient Logger LOG = LoggerFactory.getLogger(CxfProducerTest.class);

    protected CamelContext camelContext;
    protected ProducerTemplate template;

    @BeforeClass
    public static void startService() throws Exception {
        // start a simple front service
        ServerFactoryBean svrBean = new ServerFactoryBean();
        svrBean.setAddress(SIMPLE_SERVER_ADDRESS);
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
        svrBean.setBus(CXFBusFactory.getDefaultBus());
        svrBean.create();
        
        GreeterImpl greeterImpl = new GreeterImpl();
        Endpoint.publish(JAXWS_SERVER_ADDRESS, greeterImpl);
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
        Map<String, Object> responseContext = CastUtils.cast((Map)out.getHeader(Client.RESPONSE_CONTEXT));
        assertNotNull(responseContext);
        assertEquals("We should get the response context here", "UTF-8", responseContext.get(org.apache.cxf.message.Message.ENCODING));
        assertEquals("reply body on Camel", "echo " + TEST_MESSAGE, result);
        
        // check the other camel header copying
        String fileName = out.getHeader(Exchange.FILE_NAME, String.class);
        assertEquals("Should get the file name from out message header", "testFile", fileName);
    }

    @Test
    public void testInvokingAWrongServer() throws Exception {
        Exchange reply = sendSimpleMessage(getWrongEndpointUri());
        assertNotNull("We should get the exception here", reply.getException());
    }

    @Test
    public void testInvokingJaxWsServerWithParams() throws Exception {
        Exchange exchange = sendJaxWsMessage();

        org.apache.camel.Message out = exchange.getOut();
        String result = out.getBody(String.class);
        LOG.info("Received output text: " + result);
        Map<String, Object> responseContext = CastUtils.cast((Map)out.getHeader(Client.RESPONSE_CONTEXT));
        assertNotNull(responseContext);
        assertEquals("Get the wrong wsdl opertion name", "{http://apache.org/hello_world_soap_http}greetMe", responseContext.get("javax.xml.ws.wsdl.operation").toString());
        assertEquals("reply body on Camel", "Hello " + TEST_MESSAGE, result);
        
        // check the other camel header copying
        String fileName = out.getHeader(Exchange.FILE_NAME, String.class);
        assertEquals("Should get the file name from out message header", "testFile", fileName);
    }

    protected String getSimpleEndpointUri() {
        return "cxf://" + SIMPLE_SERVER_ADDRESS
            + "?serviceClass=org.apache.camel.component.cxf.HelloService";
    }

    protected String getJaxwsEndpointUri() {
        return "cxf://" + JAXWS_SERVER_ADDRESS + "?serviceClass=org.apache.hello_world_soap_http.Greeter";
    }

    protected String getWrongEndpointUri() {
        return "cxf://" + WRONG_SERVER_ADDRESS + "?serviceClass=org.apache.camel.component.cxf.HelloService";
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

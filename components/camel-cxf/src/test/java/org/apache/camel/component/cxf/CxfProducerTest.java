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

import javax.xml.ws.Endpoint;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.bus.CXFBusFactory;
import org.apache.cxf.endpoint.ServerImpl;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.hello_world_soap_http.GreeterImpl;

/**
 * @version $Revision$
 */
public class CxfProducerTest extends TestCase {
    private static final transient Log LOG = LogFactory.getLog(CxfProducerTest.class);
    protected CamelContext camelContext = new DefaultCamelContext();
    protected CamelTemplate<CxfExchange> template = new CamelTemplate<CxfExchange>(camelContext);

    private final static String SIMPLE_SERVER_ADDRESS = "http://localhost:28080/test";
    private final static String JAXWS_SERVER_ADDRESS = "http://localhost:28081/test";
    private final static String ECHO_OPERATION = "echo";
    private final static String GREET_ME_OPERATION ="greetMe";
    private final static String TEST_MESSAGE = "Hello World!";
    private ServerImpl simpleServer;
    private Endpoint jaxwsEndpoint;

    @Override
    protected void setUp() throws Exception {

        // start a simple front service
        ServerFactoryBean svrBean = new ServerFactoryBean();
        svrBean.setAddress(SIMPLE_SERVER_ADDRESS);
        svrBean.setServiceClass(HelloService.class);
        svrBean.setServiceBean(new HelloServiceImpl());
        svrBean.setBus(CXFBusFactory.getDefaultBus());

        simpleServer = (ServerImpl)svrBean.create();
        simpleServer.start();
        
        GreeterImpl greeterImpl = new GreeterImpl();
        jaxwsEndpoint = Endpoint.publish(JAXWS_SERVER_ADDRESS, greeterImpl);
                
    }

    @Override
    protected void tearDown() throws Exception {
        if (simpleServer != null) {
            simpleServer.stop();
        }
        if (jaxwsEndpoint != null) {
            jaxwsEndpoint.stop();
        }
    }

    public void testInvokingSimpleServerWithParams() throws Exception {    	

        CxfExchange exchange = (CxfExchange)template.send(getSimpleEndpointUri(), new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();                
                params.add(TEST_MESSAGE);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, ECHO_OPERATION);
            }
        });
       
        org.apache.camel.Message out = exchange.getOut();
        Object[] output = (Object[])out.getBody();
        LOG.info("Received output text: " + output[0]);
        assertEquals("reply body on Camel", TEST_MESSAGE, output[0]);
    }
   
       
    public void testInvokingJawsServerWithParams() throws Exception {
        CxfExchange exchange = (CxfExchange)template.send(getJaxwsEndpointUri(), new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();                
                params.add(TEST_MESSAGE);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, GREET_ME_OPERATION);
            }
        });
       
        org.apache.camel.Message out = exchange.getOut();
        Object[] output = (Object[])out.getBody();
        LOG.info("Received output text: " + output[0]);
        assertEquals("reply body on Camel", "Hello " + TEST_MESSAGE, output[0]);
    }
        
    private String getSimpleEndpointUri() {
        return "cxf://" + SIMPLE_SERVER_ADDRESS
        + "?serviceClass=org.apache.camel.component.cxf.HelloService";
    }
    
    private String getJaxwsEndpointUri() {
        return "cxf://" + JAXWS_SERVER_ADDRESS + "?serviceClass=org.apache.hello_world_soap_http.Greeter";
       
    }
}

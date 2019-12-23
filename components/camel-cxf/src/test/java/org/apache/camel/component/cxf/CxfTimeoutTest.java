/*
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

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.ws.Endpoint;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.frontend.AbstractWSDLBasedEndpointFactory;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.hello_world_soap_http.Greeter;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfTimeoutTest extends CamelSpringTestSupport {

    protected static final String GREET_ME_OPERATION = "greetMe";
    protected static final String TEST_MESSAGE = "Hello World!";
    protected static final String JAXWS_SERVER_ADDRESS 
        = "http://localhost:" + CXFTestSupport.getPort1() + "/CxfTimeoutTest/SoapContext/SoapPort";

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @BeforeClass
    public static void startService() {
        Greeter implementor = new GreeterImplWithSleep();
        Endpoint.publish(JAXWS_SERVER_ADDRESS, implementor); 
    }

    @Test
    public void testInvokingJaxWsServerWithBusUriParams() throws Exception {
        sendTimeOutMessage("cxf://" + JAXWS_SERVER_ADDRESS + "?serviceClass=org.apache.hello_world_soap_http.Greeter&bus=#cxf");
    }
    
    @Test
    public void testInvokingJaxWsServerWithoutBusUriParams() throws Exception {
        sendTimeOutMessage("cxf://" + JAXWS_SERVER_ADDRESS + "?serviceClass=org.apache.hello_world_soap_http.Greeter");
    }
    
    @Test
    public void testInvokingJaxWsServerWithCxfEndpoint() throws Exception {
        sendTimeOutMessage("cxf://bean:springEndpoint");
    }
    
    @Test
    public void testInvokingJaxWsServerWithCxfEndpointWithConfigurer() throws Exception {
        Exchange reply = sendJaxWsMessage("cxf://bean:springEndpoint?cxfConfigurer=#myConfigurer");
        // we don't expect the exception here
        assertFalse("We don't expect the exception here", reply.isFailed());
        assertEquals("Get a wrong response", "Greet Hello World!", reply.getOut().getBody(String.class));
    }
    
    @Test
    public void testInvokingFromCamelRoute() throws Exception {
        sendTimeOutMessage("direct:start");
    }
    
    @Test
    public void testDoCatchWithTimeOutException() throws Exception {
        sendTimeOutMessage("direct:doCatch");
    }
    
    protected void sendTimeOutMessage(String endpointUri) throws Exception {
        Exchange reply = sendJaxWsMessage(endpointUri);
        Exception e = reply.getException();
        assertNotNull("We should get the exception cause here", e);
        assertTrue("We should get the socket time out exception here", e instanceof SocketTimeoutException);
    }

    protected Exchange sendJaxWsMessage(String endpointUri) throws InterruptedException {
        Exchange exchange = template.send(endpointUri, new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<>();
                params.add(TEST_MESSAGE);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, GREET_ME_OPERATION);
            }
        });
        return exchange;
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        // we can put the http conduit configuration here
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/cxfConduitTimeOutContext.xml");
    }
    
    public static class MyCxfConfigurer implements CxfConfigurer {

        @Override
        public void configure(AbstractWSDLBasedEndpointFactory factoryBean) {
            // Do nothing here
        }

        @Override
        public void configureClient(Client client) {
            // reset the timeout option to override the spring configuration one
            HTTPConduit conduit = (HTTPConduit) client.getConduit();
            HTTPClientPolicy policy = new HTTPClientPolicy();
            policy.setReceiveTimeout(60000);
            conduit.setClient(policy);
            
        }

        @Override
        public void configureServer(Server server) {
            // Do nothing here
            
        }
        
    }

}

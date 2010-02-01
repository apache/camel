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

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.ws.Endpoint;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.hello_world_soap_http.Greeter;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfTimeoutTest extends CxfRouterTestSupport {
    
    protected static final String GREET_ME_OPERATION = "greetMe";
    protected static final String TEST_MESSAGE = "Hello World!";
    protected static final String JAXWS_SERVER_ADDRESS = "http://localhost:9023/SoapContext/SoapPort";

    protected AbstractXmlApplicationContext applicationContext;
    
    @Override
    protected void setUp() throws Exception {
        applicationContext = createApplicationContext();
        super.setUp();
        assertNotNull("Should have created a valid spring context", applicationContext);


    }

    @Override
    protected void tearDown() throws Exception {
        if (applicationContext != null) {
            applicationContext.destroy();
        }
        super.tearDown();
    }
    
    @Override
    protected void startService() {
        Greeter implementor = new GreeterImplWithSleep();
        Endpoint.publish(JAXWS_SERVER_ADDRESS, implementor); 
    }

    
    public void testInvokingJaxWsServerWithBusUriParams() throws Exception {
        sendTimeOutMessage("cxf://" + JAXWS_SERVER_ADDRESS + "?serviceClass=org.apache.hello_world_soap_http.Greeter&bus=#cxf");
    }
    
    
    public void testInvokingJaxWsServerWithoutBusUriParams() throws Exception {
        sendTimeOutMessage("cxf://" + JAXWS_SERVER_ADDRESS + "?serviceClass=org.apache.hello_world_soap_http.Greeter");
    }
    
    
    public void testInvokingJaxWsServerWithCxfEndpoint() throws Exception {
        sendTimeOutMessage("cxf://bean:springEndpoint");
    }
    
    protected void sendTimeOutMessage(String endpointUri) throws Exception {
        try {
            sendJaxWsMessage(endpointUri);
            fail("Expecting the exception here");
        } catch (RuntimeCamelException e) {
            assertNotNull("We should get the exception cause here", e.getCause());
            assertTrue("We should get the socket time out exception here", e.getCause().getCause() instanceof SocketTimeoutException);            
        }
    }

    protected Exchange sendJaxWsMessage(String endpointUri) {
        Exchange exchange = template.send(endpointUri, new Processor() {
            public void process(final Exchange exchange) {
                final List<String> params = new ArrayList<String>();
                params.add(TEST_MESSAGE);
                exchange.getIn().setBody(params);
                exchange.getIn().setHeader(CxfConstants.OPERATION_NAME, GREET_ME_OPERATION);
            }
        });
        return exchange;
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext);
    }


    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/cxfConduitTimeOutContext.xml");
    }
   

}
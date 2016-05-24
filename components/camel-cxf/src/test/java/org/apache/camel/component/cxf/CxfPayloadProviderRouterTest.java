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

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;

import org.apache.camel.builder.RouteBuilder;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.hello_world_soap_http.Greeter;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfPayloadProviderRouterTest extends AbstractCXFGreeterRouterTest {
    protected static Endpoint endpoint;
    protected static GreeterImpl implementor;
    
    private final QName serviceName = new QName("http://apache.org/hello_world_soap_http",
                                                "SOAPService");
    private final QName routerPortName = new QName("http://apache.org/hello_world_soap_http",
                                                "RouterPort");
    
    @AfterClass
    public static void stopService() {
        if (endpoint != null) {
            endpoint.stop();
        }
    }

    @BeforeClass
    public static void startService() {
        implementor = new GreeterImpl();
        String address = "http://localhost:" + getPort1() + "/CxfPayLoadProviderRouterTest/SoapContext/SoapPort";
        endpoint = Endpoint.publish(address, implementor); 
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf:bean:routerEndpoint?synchronous=true&dataFormat=PAYLOAD")
                    .setHeader("operationNamespace", constant("http://camel.apache.org/cxf/jaxws/dispatch"))
                    .setHeader("operationName", constant("Invoke"))
                    .to("cxf:bean:serviceEndpoint?synchronous=true&dataFormat=PAYLOAD");
            }
        };
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/CxfProviderRouterBeans.xml");
    }

    @Test
    public void testPublishEndpointUrl() throws Exception {
        final String path = getClass().getSimpleName() + "/CamelContext/RouterPort/" + getClass().getSimpleName();
        String response = template.requestBody("http://localhost:" + getPort2() + "/" + path
                                               + "?wsdl", null, String.class);
        assertTrue("Can't find the right service location.", response.indexOf(path) > 0);
    }

    @Test
    public void testInvokeGreetMeOverProvider() throws Exception {
        Service service = Service.create(serviceName);
        service.addPort(routerPortName, "http://schemas.xmlsoap.org/soap/",
                        "http://localhost:" + getPort2() + "/"
                        + getClass().getSimpleName() + "/CamelContext/RouterPort");
        Greeter greeter = service.getPort(routerPortName, Greeter.class);
        org.apache.cxf.endpoint.Client client = org.apache.cxf.frontend.ClientProxy.getClient(greeter);
        VerifyInboundInterceptor icp = new VerifyInboundInterceptor();
        client.getInInterceptors().add(icp);
        
        int ic = implementor.getInvocationCount();

        icp.setCalled(false);
        String reply = greeter.greetMe("test");
        assertEquals("Got the wrong reply ", "Hello test", reply);
        assertTrue("No Inbound message received", icp.isCalled());
        assertEquals("The target service not invoked", ++ic, implementor.getInvocationCount());
        
        icp.setCalled(false);
        greeter.greetMeOneWay("call greetMe OneWay !");
        assertFalse("An unnecessary inbound message", icp.isCalled());
        // wait a few seconds for the async oneway service to be invoked
        Thread.sleep(3000);
        assertEquals("The target service not invoked", ++ic, implementor.getInvocationCount());
    }
    
    static class VerifyInboundInterceptor extends AbstractPhaseInterceptor<Message> {
        private boolean called;
        
        VerifyInboundInterceptor() {
            super(Phase.USER_PROTOCOL);
        }

        @Override
        public void handleMessage(Message message) throws Fault {
            called = true;
        }
        
        public boolean isCalled() {
            return called;
        }
        
        public void setCalled(boolean b) {
            called = b;
        }
        
    }
}

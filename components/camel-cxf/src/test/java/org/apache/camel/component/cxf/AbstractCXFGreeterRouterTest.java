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

import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.hello_world_soap_http.Greeter;
import org.apache.hello_world_soap_http.NoSuchCodeLitFault;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public abstract class AbstractCXFGreeterRouterTest extends CamelTestSupport {
    protected AbstractXmlApplicationContext applicationContext;
    
    private final QName serviceName = new QName("http://apache.org/hello_world_soap_http",
                                                "SOAPService");
    private final QName routerPortName = new QName("http://apache.org/hello_world_soap_http",
                                                "RouterPort");
    
    private final String testDocLitFaultBody = 
        "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<soap:Body><testDocLitFault xmlns=\"http://apache.org/hello_world_soap_http/types\">"
        + "<faultType>NoSuchCodeLitFault</faultType></testDocLitFault>"
        + "</soap:Body></soap:Envelope>";
    
    public static int getPort1() {
        return CXFTestSupport.getPort1();
    }

    public static int getPort2() {
        return CXFTestSupport.getPort2();
    }

    protected abstract ClassPathXmlApplicationContext createApplicationContext();
    
    @Override
    @Before
    public void setUp() throws Exception {
        applicationContext = createApplicationContext();
        super.setUp();
        assertNotNull("Should have created a valid spring context", applicationContext);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        
        IOHelper.close(applicationContext);
        super.tearDown();
    }
    
    
    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {
        Service service = Service.create(serviceName);
        service.addPort(routerPortName, "http://schemas.xmlsoap.org/soap/",
                        "http://localhost:" + getPort2() + "/"
                        + getClass().getSimpleName() + "/CamelContext/RouterPort");
        Greeter greeter = service.getPort(routerPortName, Greeter.class);

        String reply = greeter.greetMe("test");
        assertNotNull("No response received from service", reply);
        assertEquals("Got the wrong reply ", "Hello test", reply);
        reply = greeter.sayHi();
        assertNotNull("No response received from service", reply);
        assertEquals("Got the wrong reply ", "Bonjour", reply);

        greeter.greetMeOneWay("call greetMe OneWay !");

        // test throw the exception
        try {
            greeter.testDocLitFault("NoSuchCodeLitFault");
            // should get the exception here
            fail("Should get the NoSuchCodeLitFault here.");
        } catch (NoSuchCodeLitFault fault) {
            // expect the fault here
            assertNotNull("The fault info should not be null", fault.getFaultInfo());
        }

    }
    
    @Test
    public void testRoutingSOAPFault() throws Exception {
        try {
            template.sendBody("http://localhost:" + getPort2() + "/"
                              + getClass().getSimpleName()
                              + "/CamelContext/RouterPort/",
                              testDocLitFaultBody);
            fail("Should get an exception here.");
        } catch (RuntimeCamelException exception) {
            assertTrue("It should get the response error", exception.getCause() instanceof HttpOperationFailedException);
            assertEquals("Get a wrong response code", ((HttpOperationFailedException)exception.getCause()).getStatusCode(), 500);
        }
    }
    
    @Test
    public void testPublishEndpointUrl() throws Exception {
        String response = template.requestBody("http://localhost:" + getPort2() + "/" + getClass().getSimpleName()
                                               + "/CamelContext/RouterPort/"
            + getClass().getSimpleName() + "?wsdl", null, String.class);
        assertTrue("Can't find the right service location.", response.indexOf("http://www.simple.com/services/test") > 0);
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(applicationContext, true);
    }

   
}




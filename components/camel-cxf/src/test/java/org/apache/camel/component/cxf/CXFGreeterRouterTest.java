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

import java.lang.reflect.UndeclaredThrowableException;

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.processor.SpringTestHelper;
import org.apache.hello_world_soap_http.Greeter;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.apache.hello_world_soap_http.NoSuchCodeLitFault;

public class CXFGreeterRouterTest extends CxfSpringRouterTest {
    private final QName serviceName = new QName("http://apache.org/hello_world_soap_http",
                                                "SOAPService");
    private final QName routerPortName = new QName("http://apache.org/hello_world_soap_http",
                                                "RouterPort");
    @Override
    protected void startService() {
        Object implementor = new GreeterImpl();
        String address = "http://localhost:9000/SoapContext/SoapPort";
        Endpoint.publish(address, implementor);
    }

    @Override
    public void testInvokingServiceFromCXFClient() throws Exception {
        Service service = Service.create(serviceName);
        service.addPort(routerPortName, "http://schemas.xmlsoap.org/soap/",
                        "http://localhost:9003/CamelContext/RouterPort");
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
        }

    }

    @Override
    public void testOnwayInvocation() throws Exception {
        Service service = Service.create(serviceName);
        service.addPort(routerPortName, "http://schemas.xmlsoap.org/soap/",
                        "http://localhost:9003/CamelContext/RouterPort");
        Greeter greeter = service.getPort(routerPortName, Greeter.class);
        greeter.greetMeOneWay("call greetMe OneWay !");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringTestHelper.createSpringCamelContext(this, "org/apache/camel/component/cxf/GreeterEndpointsRouterContext.xml");
    }
}




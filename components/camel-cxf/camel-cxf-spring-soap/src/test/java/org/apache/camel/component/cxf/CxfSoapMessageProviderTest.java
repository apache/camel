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

import java.lang.reflect.UndeclaredThrowableException;
import java.net.URL;

import jakarta.xml.ws.BindingProvider;

import javax.xml.namespace.QName;

import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.camel.wsdl_first.JaxwsTestHandler;
import org.apache.hello_world_soap_http.Greeter;
import org.apache.hello_world_soap_http.SOAPService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CxfSoapMessageProviderTest extends CamelSpringTestSupport {

    static int port = CXFTestSupport.getPort1();

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/SoapMessageProviderContext.xml");
    }

    @Test
    public void testSOAPMessageModeDocLit() throws Exception {
        JaxwsTestHandler fromHandler = getMandatoryBean(JaxwsTestHandler.class, "fromEndpointJaxwsHandler");
        fromHandler.reset();

        QName serviceName = new QName("http://apache.org/hello_world_soap_http", "SOAPProviderService");
        QName portName = new QName("http://apache.org/hello_world_soap_http", "SoapProviderPort");

        URL wsdl = getClass().getResource("/wsdl/hello_world.wsdl");
        assertNotNull(wsdl);

        SOAPService service = new SOAPService(wsdl, serviceName);
        assertNotNull(service);

        String response1 = new String("TestSOAPOutputPMessage");
        String response2 = new String("Bonjour");
        try {
            Greeter greeter = service.getPort(portName, Greeter.class);
            ((BindingProvider) greeter).getRequestContext()
                    .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                            "http://localhost:" + port + "/CxfSoapMessageProviderTest/SoapContext/SoapProviderPort");
            for (int idx = 0; idx < 2; idx++) {
                String greeting = greeter.greetMe("Milestone-" + idx);
                assertNotNull(greeting, "no response received from service");
                assertEquals(response1, greeting);

                String reply = greeter.sayHi();
                assertNotNull(reply, "no response received from service");
                assertEquals(response2, reply);
            }
        } catch (UndeclaredThrowableException ex) {
            throw (Exception) ex.getCause();
        }

        assertEquals(8, fromHandler.getMessageCount(), "Can't get the right message count");
        assertEquals(0, fromHandler.getFaultCount(), "Can't get the right fault count");
        //From CXF 2.2.7 the soap handler's getHeader() method will not be called if the SOAP message don't have headers
        //assertEquals(fromHandler.getGetHeadersCount(), 4, "Can't get the right headers count");

    }

}

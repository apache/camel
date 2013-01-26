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
import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.apache.camel.wsdl_first.JaxwsTestHandler;
import org.apache.hello_world_soap_http.Greeter;
import org.apache.hello_world_soap_http.SOAPService;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;


public class CxfSoapMessageProviderTest extends CamelSpringTestSupport {

    static int port = CXFTestSupport.getPort1();
    
    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/SoapMessageProviderContext.xml");
    }
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Test
    public void testSOAPMessageModeDocLit() throws Exception {
        JaxwsTestHandler fromHandler = getMandatoryBean(JaxwsTestHandler.class, "fromEndpointJaxwsHandler");
        fromHandler.reset();
        
        QName serviceName =
            new QName("http://apache.org/hello_world_soap_http", "SOAPProviderService");
        QName portName =
            new QName("http://apache.org/hello_world_soap_http", "SoapProviderPort");

        URL wsdl = getClass().getResource("/wsdl/hello_world.wsdl");
        assertNotNull(wsdl);

        SOAPService service = new SOAPService(wsdl, serviceName);
        assertNotNull(service);

        String response1 = new String("TestSOAPOutputPMessage");
        String response2 = new String("Bonjour");
        try {
            Greeter greeter = service.getPort(portName, Greeter.class);
            ((BindingProvider)greeter).getRequestContext()
                .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                     "http://localhost:" + port + "/CxfSoapMessageProviderTest/SoapContext/SoapProviderPort");
            for (int idx = 0; idx < 2; idx++) {
                String greeting = greeter.greetMe("Milestone-" + idx);
                assertNotNull("no response received from service", greeting);
                assertEquals(response1, greeting);

                String reply = greeter.sayHi();
                assertNotNull("no response received from service", reply);
                assertEquals(response2, reply);
            }
        } catch (UndeclaredThrowableException ex) {
            throw (Exception)ex.getCause();
        }
        
        assertEquals("Can't get the right message count", fromHandler.getMessageCount(), 8);
        assertEquals("Can't get the right fault count", fromHandler.getFaultCount(), 0);
        //From CXF 2.2.7 the soap handler's getHeader() method will not be called if the SOAP message don't have headers
        //assertEquals("Can't get the right headers count", fromHandler.getGetHeadersCount(), 4);
        
    }



}

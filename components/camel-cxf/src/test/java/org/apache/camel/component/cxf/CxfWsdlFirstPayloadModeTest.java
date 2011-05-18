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

import java.net.URL;

import javax.xml.namespace.QName;
import javax.xml.ws.Holder;

import org.apache.camel.wsdl_first.JaxwsTestHandler;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.camel.wsdl_first.UnknownPersonFault;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfWsdlFirstPayloadModeTest extends CxfWsdlFirstTest {

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/WsdlFirstBeansPayloadMode.xml");
    }
    
    @Test
    @Override
    public void testInvokingServiceFromCXFClient() throws Exception {

        JaxwsTestHandler fromHandler = getMandatoryBean(JaxwsTestHandler.class, "fromEndpointJaxwsHandler");
        fromHandler.reset();
        
        JaxwsTestHandler toHandler = getMandatoryBean(JaxwsTestHandler.class, "toEndpointJaxwsHandler");
        toHandler.reset();

        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, new QName("http://camel.apache.org/wsdl-first", "PersonService"));
        Person client = ss.getSoap();
        Holder<String> personId = new Holder<String>();
        personId.value = "hello";
        Holder<String> ssn = new Holder<String>();
        Holder<String> name = new Holder<String>();

        client.getPerson(personId, ssn, name);
        assertEquals("we should get the right answer from router", "Bonjour", name.value);

        Throwable t = null;
        personId.value = "";
        try {
            client.getPerson(personId, ssn, name);
            fail("We expect to get the UnknowPersonFault here");
        } catch (UnknownPersonFault fault) {
            // We expect to get fault here
            t = fault;
        }
        
        assertTrue(t instanceof UnknownPersonFault);
        
        // schema validation will throw a parse exception
        personId.value = "Invoking getPerson with invalid length string, expecting exception...xxxxxxxxx";
        try {            
            client.getPerson(personId, ssn, name);
            fail("We expect to get a message schema validation failure");        
        } catch (Exception ex) {
            assertEquals("Could not parse the XML stream.", ex.getMessage());         
        }

        verifyJaxwsHandlers(fromHandler, toHandler);
    }

    @Test
    public void testInvokingServiceWithCamelProducer() throws Exception {
        // this test does not apply to PAYLOAD mode
    }
    
    @Override
    protected void verifyJaxwsHandlers(JaxwsTestHandler fromHandler, JaxwsTestHandler toHandler) { 
        assertEquals(2, fromHandler.getFaultCount());
        assertEquals(4, fromHandler.getMessageCount());
        // Changed to use noErrorhandler and now the message will not be sent again.
        assertEquals(3, toHandler.getMessageCount());
        assertEquals(1, toHandler.getFaultCount());
    }

}

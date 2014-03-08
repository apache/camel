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
import javax.xml.ws.BindingProvider;

import org.apache.camel.CamelContext;
import org.apache.camel.non_wrapper.Person;
import org.apache.camel.non_wrapper.PersonService;
import org.apache.camel.non_wrapper.UnknownPersonFault;
import org.apache.camel.non_wrapper.types.GetPerson;
import org.apache.camel.non_wrapper.types.GetPersonResponse;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfNonWrapperTest extends CamelSpringTestSupport {
    int port1 = CXFTestSupport.getPort1(); 
    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/nonWrapperProcessor.xml");
    }

    protected void assertValidContext(CamelContext context) {
        assertNotNull("No context found!", context);
    }

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {

        URL wsdlURL = getClass().getClassLoader().getResource("person-non-wrapper.wsdl");
        PersonService ss = new PersonService(wsdlURL, new QName("http://camel.apache.org/non-wrapper", "PersonService"));
        Person client = ss.getSoap();
        ((BindingProvider)client).getRequestContext()
            .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                 "http://localhost:" + port1 + "/CxfNonWrapperTest/PersonService/");
        
        GetPerson request = new GetPerson();
        request.setPersonId("hello");
        GetPersonResponse response = client.getPerson(request);

        assertEquals("we should get the right answer from router", "Bonjour", response.getName());

        request.setPersonId("");
        try {
            client.getPerson(request);
            fail("We expect to get the UnknowPersonFault here");
        } catch (UnknownPersonFault fault) {
            // We expect to get fault here
        }
    }



}

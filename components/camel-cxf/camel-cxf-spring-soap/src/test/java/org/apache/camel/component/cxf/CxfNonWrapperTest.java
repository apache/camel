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

import java.net.URL;

import jakarta.xml.ws.BindingProvider;

import javax.xml.namespace.QName;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.non_wrapper.Person;
import org.apache.camel.non_wrapper.PersonService;
import org.apache.camel.non_wrapper.UnknownPersonFault;
import org.apache.camel.non_wrapper.types.GetPerson;
import org.apache.camel.non_wrapper.types.GetPersonResponse;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CxfNonWrapperTest extends CamelSpringTestSupport {
    int port1 = CXFTestSupport.getPort1();

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/nonWrapperProcessor.xml");
    }

    @Override
    protected void assertValidContext(CamelContext context) {
        assertNotNull(context, "No context found!");
    }

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {

        URL wsdlURL = getClass().getClassLoader().getResource("person-non-wrapper.wsdl");
        PersonService ss = new PersonService(wsdlURL, new QName("http://camel.apache.org/non-wrapper", "PersonService"));
        Person client = ss.getSoap();
        ((BindingProvider) client).getRequestContext()
                .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                        "http://localhost:" + port1 + "/CxfNonWrapperTest/PersonService/");

        GetPerson request = new GetPerson();
        request.setPersonId("hello");
        GetPersonResponse response = client.getPerson(request);

        assertEquals("Bonjour", response.getName(), "we should get the right answer from router");

        request.setPersonId("");
        try {
            client.getPerson(request);
            fail("We expect to get the UnknowPersonFault here");
        } catch (UnknownPersonFault fault) {
            // We expect to get fault here
        }
    }

}

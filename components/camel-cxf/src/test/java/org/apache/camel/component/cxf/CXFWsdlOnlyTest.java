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
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Holder;

import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonImpl;
import org.apache.camel.wsdl_first.PersonService;
import org.apache.camel.wsdl_first.UnknownPersonFault;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CXFWsdlOnlyTest extends CamelSpringTestSupport {

    private Endpoint endpoint1;
    private Endpoint endpoint2;

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/WsdlOnlyBeans.xml");
    }

    protected void assertValidContext(CamelContext context) {
        assertNotNull("No context found!", context);
    }

    @Before
    public void startServices() {
        Object implementor = new PersonImpl();
        String address = "http://localhost:9000/PersonService/";
        endpoint1 = Endpoint.publish(address, implementor);

        address = "http://localhost:9001/PersonService/";
        endpoint2 = Endpoint.publish(address, implementor);
    }
    
    @After
    public void stopServices() {
        if (endpoint1 != null) {
            endpoint1.stop();
        }
        
        if (endpoint2 != null) {
            endpoint2.stop();
        }
    }

    @Test
    public void testRoutes() throws Exception {
        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, new QName("http://camel.apache.org/wsdl-first",
                                                                "PersonService"));
        Person client = ss.getSoap();
        Holder<String> personId = new Holder<String>();
        personId.value = "hello";
        Holder<String> ssn = new Holder<String>();
        Holder<String> name = new Holder<String>();
        System.out.println(">>>>>>>>>");
        client.getPerson(personId, ssn, name);
        System.out.println("<<<<<<<<");
        assertEquals("Bonjour", name.value);

        // TODO: camel-cxf invokes async callback 2 times, there is a problem with this kind of using CXF

/*        Person client2 = ss.getSoap2();
        Holder<String> personId2 = new Holder<String>();
        personId2.value = "hello";
        Holder<String> ssn2 = new Holder<String>();
        Holder<String> name2 = new Holder<String>();
        client2.getPerson(personId2, ssn2, name2);
        assertEquals("Bonjour", name2.value);*/
    }
    
    @Test
    @Ignore
    public void testSoapFaultRoutes() {
        URL wsdlURL = getClass().getClassLoader().getResource("person.wsdl");
        PersonService ss = new PersonService(wsdlURL, new QName("http://camel.apache.org/wsdl-first",
                                                                "PersonService"));
        // test message mode
        Person client = ss.getSoap();
        Holder<String> personId = new Holder<String>();
        personId.value = "";
        Holder<String> ssn = new Holder<String>();
        Holder<String> name = new Holder<String>();
        Throwable t = null;
        try {
            client.getPerson(personId, ssn, name);
            fail("Expect exception");
        } catch (UnknownPersonFault e) {
            t = e;
        }
        assertTrue(t instanceof UnknownPersonFault);

        // test PAYLOAD mode
        Person client2 = ss.getSoap2();
        Holder<String> personId2 = new Holder<String>();
        personId2.value = "";
        Holder<String> ssn2 = new Holder<String>();
        Holder<String> name2 = new Holder<String>();
        try {
            client2.getPerson(personId2, ssn2, name2);
            fail("Expect exception");
        } catch (UnknownPersonFault e) {
            t = e;
        }
        assertTrue(t instanceof UnknownPersonFault);
    }

}

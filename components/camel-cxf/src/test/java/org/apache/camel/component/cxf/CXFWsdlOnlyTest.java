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
import javax.xml.ws.Endpoint;
import javax.xml.ws.Holder;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.spring.CxfEndpointBean;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.wsdl_first.Person;
import org.apache.camel.wsdl_first.PersonImpl;
import org.apache.camel.wsdl_first.PersonService;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;



public class CXFWsdlOnlyTest extends CamelSpringTestSupport {

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/WsdlOnlyBeans.xml");
    }

    protected void assertValidContext(CamelContext context) {
        assertNotNull("No context found!", context);
    }

    @BeforeClass
    public static void startService() {
        Object implementor = new PersonImpl();
        String address = "http://localhost:9000/PersonService/";
        Endpoint.publish(address, implementor);

        address = "http://localhost:9001/PersonService/";
        Endpoint.publish(address, implementor);
    }

    @Test
    public void testCreateWSDLOnly() {
        CxfEndpointBean ep = getMandatoryBean(CxfEndpointBean.class, "serviceEndpoint");

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
        client.getPerson(personId, ssn, name);
        System.out.println("NAME: " + name.value);
        assertEquals("Bonjour", name.value);

        Person client2 = ss.getSoap2();
        Holder<String> personId2 = new Holder<String>();
        personId2.value = "hello";
        Holder<String> ssn2 = new Holder<String>();
        Holder<String> name2 = new Holder<String>();
        client.getPerson(personId2, ssn2, name2);
        System.out.println("NAME: " + name2.value);
        assertEquals("Bonjour", name2.value);
    }

    public <T> T getMandatoryBean(Class<T> type, String name) {
        Object value = applicationContext.getBean(name);
        assertNotNull("No spring bean found for name <" + name + ">", value);
        if (type.isInstance(value)) {
            return type.cast(value);
        } else {
            fail("Spring bean <" + name + "> is not an instanceof " + type.getName() + " but is of type "
                 + ObjectHelper.className(value));
            return null;
        }
    }

}

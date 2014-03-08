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
package org.apache.camel.dataformat.castor;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringMarshalDomainObjectTest extends CamelSpringTestSupport {

    @Test
    public void testMarshalDomainObject() throws Exception {
        // some platform cannot test using Castor as it uses a SUN dependent Xerces
        if (isJavaVendor("IBM")) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        PurchaseOrder order = new PurchaseOrder();
        order.setName("Tiger");
        order.setAmount(1);
        order.setPrice(99.95);

        template.sendBody("direct:in", order);

        mock.assertIsSatisfied();
    }

    @Test
    public void testMappingOfDomainObject() throws Exception {
        // some platform cannot test using Castor as it uses a SUN dependent Xerces
        if (isJavaVendor("IBM")) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        Student expectedStudent = new Student();
        expectedStudent.setStuFirstName("John");
        expectedStudent.setStuLastName("Doe");
        expectedStudent.setStuAge(21);

        String expectedString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<student><firstname>John</firstname><lastname>Doe</lastname><age>21</age></student>";

        template.sendBody("direct:unmarshal", expectedString);
        template.sendBody("direct:marshal", expectedStudent);

        mock.assertIsSatisfied();
        Student actualStudent = mock.getExchanges().get(0).getIn().getBody(Student.class);
        String actualString = mock.getExchanges().get(1).getIn().getBody(String.class);

        // compare objects
        assertEquals("The expected student does not match the unmarshal XML student.", expectedStudent, actualStudent);
        // compare XML
        assertEquals("The expected XML does not match the marshal student XML.", expectedString, actualString);
    }

    @Test
    public void testMarshalDomainObjectTwice() throws Exception {
        // some platform cannot test using Castor as it uses a SUN dependent Xerces
        if (isJavaVendor("IBM")) {
            return;
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);

        PurchaseOrder order = new PurchaseOrder();
        order.setName("Tiger");
        order.setAmount(1);
        order.setPrice(99.95);

        template.sendBody("direct:in", order);
        template.sendBody("direct:in", order);

        mock.assertIsSatisfied();

        String body1 = mock.getExchanges().get(0).getIn().getBody(String.class);
        String body2 = mock.getExchanges().get(1).getIn().getBody(String.class);
        assertEquals("The body should marshalled to the same", body1, body2);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/castor/SpringMarshalDomainObjectTest.xml");
    }

}

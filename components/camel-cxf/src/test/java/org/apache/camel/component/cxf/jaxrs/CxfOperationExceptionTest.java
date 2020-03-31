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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfOperationExceptionTest extends CamelSpringTestSupport {
    private static final int PORT1 = CXFTestSupport.getPort1();

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/CxfOperationException.xml");
    }

    @Test(expected = CamelExecutionException.class)
    public void testRestServerDirectlyAddCustomer() {
        Customer input = new Customer();
        input.setName("Donald Duck");

        // we cannot convert directly to Customer as we need camel-jaxb
        String response = template.requestBodyAndHeader("cxfrs:http://localhost:" + PORT1 + "/CxfOperationExceptionTest/customerservice/customers?throwExceptionOnFailure=true", input,
            Exchange.HTTP_METHOD, "POST", String.class);

        assertNotNull(response);
        assertTrue(response.endsWith("<name>Donald Duck</name></Customer>"));
    }

    @Test
    public void testRestServerDirectlyAddCustomerWithExceptionsTurnedOff() {
        Customer input = new Customer();
        input.setName("Donald Duck");

        // we cannot convert directly to Customer as we need camel-jaxb
        String response = template.requestBodyAndHeader("cxfrs:bean:rsClient?throwExceptionOnFailure=false", input,
            Exchange.HTTP_METHOD, "POST", String.class);

        assertNotNull(response);
        assertTrue(response.contains("CxfOperationExceptionTest/rest"));
    }
}

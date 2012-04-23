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

package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.apache.cxf.BusFactory;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CxfOperationExceptionTest extends CamelSpringTestSupport {

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/jaxrs/CxfRsSpringRouter.xml");
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    protected void doPostSetup() throws Exception {
        //clean up the default bus for template to use
        BusFactory.setDefaultBus(null);
    }

    /**
     * Calling an invalid URL on a running server. So we expect a 404 response in the CamelExecutionException
     */
    @Test(expected = CamelExecutionException.class)
    public void testRestServerDirectlyAddCustomer() {
        Customer input = new Customer();
        input.setName("Donald Duck");

        template.requestBodyAndHeader("cxfrs:http://localhost:9002/wrongurl?throwExceptionOnFailure=true", input,
            Exchange.HTTP_METHOD, "POST", String.class);
    }

    @Test
    public void testRestServerDirectlyAddCustomerWithExceptionsTurnedOff() {
        Customer input = new Customer();
        input.setName("Donald Duck");

        String response = template.requestBodyAndHeader("cxfrs:http://localhost:9002/wrongurl?throwExceptionOnFailure=false", input,
            Exchange.HTTP_METHOD, "POST", String.class);

        assertNotNull(response);
        assertTrue(response.contains("Problem accessing /wrongurl"));
    }
}

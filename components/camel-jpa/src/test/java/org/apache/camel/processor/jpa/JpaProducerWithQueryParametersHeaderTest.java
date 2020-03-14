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
package org.apache.camel.processor.jpa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jpa.JpaConstants;
import org.apache.camel.examples.Customer;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class JpaProducerWithQueryParametersHeaderTest extends Assert {
    
    protected DefaultCamelContext camelContext;
    protected ProducerTemplate template;

    @Test
    @SuppressWarnings("rawtypes")
    public void testProducerWithNamedQuery() throws Exception {
        template.sendBody("direct:deleteCustomers", "");
        Customer c1 = new Customer();
        c1.setName("Willem");
        template.sendBody("direct:addCustomer", c1);
        Customer c2 = new Customer();
        c2.setName("Dummy");
        template.sendBody("direct:addCustomer", c2);
        
        Map<String, Object> params = new HashMap<>();
        params.put("custName", "${body}");

        List list = template.requestBodyAndHeader("direct:namedQuery", "Willem", JpaConstants.JPA_PARAMETERS_HEADER, params, List.class);
        assertEquals(1, list.size());
        assertEquals("Willem", ((Customer)list.get(0)).getName());

        int integer = template.requestBody("direct:deleteCustomers", null, int.class);
        assertEquals(2, integer);
    }

    @Before
    public void setUp() throws Exception {
        camelContext = new DefaultCamelContext();

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:namedQuery")
                    .to("jpa://" + Customer.class.getName() + "?namedQuery=findAllCustomersWithName");
                
                from("direct:addCustomer")
                    .to("jpa://" + Customer.class.getName());
                from("direct:deleteCustomers")
                    .to("jpa://" + Customer.class.getName() + "?query=delete from " + Customer.class.getName());

            }
        });

        camelContext.start();
        template = camelContext.createProducerTemplate();
    }

    @After
    public void tearDown() throws Exception {
        camelContext.stop();
    }
}

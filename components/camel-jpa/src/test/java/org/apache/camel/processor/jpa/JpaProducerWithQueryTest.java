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
import org.apache.camel.examples.Customer;
import org.apache.camel.examples.MultiSteps;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JpaProducerWithQueryTest {

    protected DefaultCamelContext camelContext;
    protected ProducerTemplate template;

    @Test
    public void testProducerWithNamedQuery() {
        template.sendBody("direct:deleteCustomers", "");
        Customer c1 = new Customer();
        c1.setName("Willem");
        template.sendBody("direct:addCustomer", c1);
        Customer c2 = new Customer();
        c2.setName("Dummy");
        template.sendBody("direct:addCustomer", c2);

        Object answer = template.requestBody("direct:namedQuery", "Willem");
        List list = (List) answer;
        assertEquals(1, list.size());
        assertEquals("Willem", ((Customer) list.get(0)).getName());

        answer = template.requestBody("direct:deleteCustomers", "");
        assertEquals(2, ((Integer) answer).intValue());
    }

    @Test
    public void testProducerWithQuery() {
        template.sendBody("direct:deleteMultiSteps", "");
        MultiSteps m1 = new MultiSteps();
        m1.setStep(1);
        template.sendBody("direct:addMultiSteps", m1);
        MultiSteps m2 = new MultiSteps();
        m2.setStep(2);
        template.sendBody("direct:addMultiSteps", m2);

        Object answer = template.requestBody("direct:query", "");
        List list = (List) answer;
        assertEquals(1, list.size());
        assertEquals(1, ((MultiSteps) list.get(0)).getStep());

        answer = template.requestBody("direct:deleteMultiSteps", "");
        assertEquals(2, ((Integer) answer).intValue());
    }

    @Test
    public void testProducerWithNativeQuery() {
        template.sendBody("direct:deleteMultiSteps", "");
        MultiSteps m1 = new MultiSteps();
        m1.setStep(1);
        template.sendBody("direct:addMultiSteps", m1);
        MultiSteps m2 = new MultiSteps();
        m2.setStep(2);
        template.sendBody("direct:addMultiSteps", m2);

        Object answer = template.requestBody("direct:nativeQuery", "");
        List list = (List) answer;
        assertEquals(1, list.size());
        assertEquals(1, ((Object[]) list.get(0))[2]);

        answer = template.requestBody("direct:deleteMultiSteps", "");
        assertEquals(2, ((Integer) answer).intValue());
    }

    @Test
    public void testProducerWithNativeQueryAndResultClass() {
        template.sendBody("direct:deleteMultiSteps", "");
        MultiSteps m1 = new MultiSteps();
        m1.setStep(1);
        template.sendBody("direct:addMultiSteps", m1);
        MultiSteps m2 = new MultiSteps();
        m2.setStep(2);
        template.sendBody("direct:addMultiSteps", m2);

        Object answer = template.requestBody("direct:nativeQueryWithResultClass", "");
        List list = (List) answer;
        assertEquals(1, list.size());
        assertEquals(1, ((MultiSteps) list.get(0)).getStep());

        answer = template.requestBody("direct:deleteMultiSteps", "");
        assertEquals(2, ((Integer) answer).intValue());
    }

    @BeforeEach
    public void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        SimpleRegistry registry = new SimpleRegistry();
        Map<String, Object> params = new HashMap<>();
        params.put("custName", "${body}");
        // bind the params
        registry.bind("params", params);
        camelContext.getCamelContextExtension().setRegistry(registry);

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:query")
                        .to("jpa://" + MultiSteps.class.getName() + "?query=select o from " + MultiSteps.class.getName()
                            + " o where o.step = 1");
                from("direct:namedQuery")
                        .to("jpa://" + Customer.class.getName() + "?namedQuery=findAllCustomersWithName&parameters=#params");
                from("direct:nativeQuery")
                        .to("jpa://" + MultiSteps.class.getName() + "?nativeQuery=select * from MultiSteps where step = 1");
                from("direct:nativeQueryWithResultClass")
                        .to("jpa://" + MultiSteps.class.getName()
                            + "?resultClass=org.apache.camel.examples.MultiSteps&nativeQuery=select * from MultiSteps where step = 1");

                from("direct:addCustomer")
                        .to("jpa://" + Customer.class.getName());
                from("direct:deleteCustomers")
                        .to("jpa://" + Customer.class.getName() + "?query=delete from " + Customer.class.getName());
                from("direct:addMultiSteps")
                        .to("jpa://" + MultiSteps.class.getName());
                from("direct:deleteMultiSteps")
                        .to("jpa://" + MultiSteps.class.getName() + "?nativeQuery=delete from MultiSteps");
            }
        });

        camelContext.start();
        template = camelContext.createProducerTemplate();
    }

    @AfterEach
    public void tearDown() {
        camelContext.stop();
    }
}

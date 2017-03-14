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
package org.apache.camel.processor.jpa;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityManager;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.examples.Customer;
import org.apache.camel.examples.MultiSteps;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.util.ServiceHelper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class JpaProducerWithQueryTest extends Assert {
    
    protected static final Logger LOG = LoggerFactory.getLogger(JpaProducerWithQueryTest.class);
    
    protected DefaultCamelContext camelContext;
    protected ProducerTemplate template;

    @Test
    public void testProducerWithNamedQuery() throws Exception {
        template.sendBody("direct:deleteCustomers", "");
        Customer c1 = new Customer();
        c1.setName("Willem");
        template.sendBody("direct:addCustomer", c1);
        Customer c2 = new Customer();
        c2.setName("Dummy");
        template.sendBody("direct:addCustomer", c2);

        Object answer = template.requestBody("direct:namedQuery", "Willem");
        List list = (List)answer;
        assertEquals(1, list.size());
        assertEquals("Willem", ((Customer)list.get(0)).getName());

        answer = template.requestBody("direct:deleteCustomers", "");
        assertEquals(2, ((Integer)answer).intValue());
    }

    @Test
    public void testProducerWithQuery() throws Exception {
        template.sendBody("direct:deleteMultiSteps", "");
        MultiSteps m1 = new MultiSteps();
        m1.setStep(1);
        template.sendBody("direct:addMultiSteps", m1);
        MultiSteps m2 = new MultiSteps();
        m2.setStep(2);
        template.sendBody("direct:addMultiSteps", m2);

        Object answer = template.requestBody("direct:query", "");
        List list = (List)answer;
        assertEquals(1, list.size());
        assertEquals(1, ((MultiSteps)list.get(0)).getStep());

        answer = template.requestBody("direct:deleteMultiSteps", "");
        assertEquals(2, ((Integer)answer).intValue());
    }

    @Test
    public void testProducerWithNativeQuery() throws Exception {
        template.sendBody("direct:deleteMultiSteps", "");
        MultiSteps m1 = new MultiSteps();
        m1.setStep(1);
        template.sendBody("direct:addMultiSteps", m1);
        MultiSteps m2 = new MultiSteps();
        m2.setStep(2);
        template.sendBody("direct:addMultiSteps", m2);

        Object answer = template.requestBody("direct:nativeQuery", "");
        List list = (List)answer;
        assertEquals(1, list.size());
        assertEquals(1, ((Object[])list.get(0))[2]);

        answer = template.requestBody("direct:deleteMultiSteps", "");
        assertEquals(2, ((Integer)answer).intValue());
    }

    @Test
    public void testProducerWithNativeQueryAndResultClass() throws Exception {
        template.sendBody("direct:deleteMultiSteps", "");
        MultiSteps m1 = new MultiSteps();
        m1.setStep(1);
        template.sendBody("direct:addMultiSteps", m1);
        MultiSteps m2 = new MultiSteps();
        m2.setStep(2);
        template.sendBody("direct:addMultiSteps", m2);


        Object answer = template.requestBody("direct:nativeQueryWithResultClass", "");
        List list = (List)answer;
        assertEquals(1, list.size());
        assertEquals(1, ((MultiSteps)list.get(0)).getStep());

        answer = template.requestBody("direct:deleteMultiSteps", "");
        assertEquals(2, ((Integer)answer).intValue());
    }

    @Before
    public void setUp() throws Exception {
        camelContext = new DefaultCamelContext();
        SimpleRegistry registry = new SimpleRegistry();
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("custName", "${body}");
        // bind the params
        registry.put("params", params);
        camelContext.setRegistry(registry);

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:query")
                    .to("jpa://" + MultiSteps.class.getName() + "?query=select o from " + MultiSteps.class.getName() + " o where o.step = 1");
                from("direct:namedQuery")
                    .to("jpa://" + Customer.class.getName() + "?namedQuery=findAllCustomersWithName&parameters=#params");
                from("direct:nativeQuery")
                    .to("jpa://" + MultiSteps.class.getName() + "?nativeQuery=select * from MultiSteps where step = 1");
                from("direct:nativeQueryWithResultClass")
                    .to("jpa://" + MultiSteps.class.getName() + "?resultClass=org.apache.camel.examples.MultiSteps&nativeQuery=select * from MultiSteps where step = 1");

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

        template = camelContext.createProducerTemplate();
        ServiceHelper.startServices(template, camelContext);
    }

    @After
    public void tearDown() throws Exception {
        ServiceHelper.stopServices(template, camelContext);
    }
}

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

import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.Customer;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Test;

public class JpaPollingConsumerTest extends AbstractJpaTest {
    protected static final String SELECT_ALL_STRING = "select x from " + Customer.class.getName() + " x";

    protected void assertEntitiesInDatabase(int count, String entity) {
        List<?> results = entityManager.createQuery("select o from " + entity + " o").getResultList();
        assertEquals(count, results.size());
    }

    @Test
    public void testPollingConsumer() throws Exception {
        Customer customer = new Customer();
        customer.setName("Donald Duck");
        saveEntityInDB(customer);
        Customer customer2 = new Customer();
        customer2.setName("Goofy");
        saveEntityInDB(customer2);

        assertEntitiesInDatabase(2, Customer.class.getName());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello Donald Duck how are you today?");

        template.sendBodyAndHeader("direct:start", "Hello NAME how are you today?", "name", "Donald%");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new SpringRouteBuilder() {
            public void configure() {
                from("direct:start")
                    .pollEnrich().simple("jpa://" + Customer.class.getName() + "?query=select c from Customer c where c.name like '${header.name}'")
                        .aggregationStrategy((a, b) -> {
                            String name = b.getIn().getBody(Customer.class).getName();
                            String phrase = a.getIn().getBody(String.class).replace("NAME", name);
                            a.getIn().setBody(phrase);
                            return a;
                        })
                    .to("mock:result");
            }
        };
    }

    @Override
    protected String routeXml() {
        return "org/apache/camel/processor/jpa/springJpaRouteTest.xml";
    }

    @Override
    protected String selectAllString() {
        return SELECT_ALL_STRING;
    }
}
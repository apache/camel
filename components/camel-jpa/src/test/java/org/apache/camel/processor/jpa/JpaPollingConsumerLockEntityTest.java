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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.Customer;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JpaPollingConsumerLockEntityTest extends AbstractJpaTest {
    protected static final String SELECT_ALL_STRING = "select x from " + Customer.class.getName() + " x";

    protected void save(final Customer customer) {
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                entityManager.joinTransaction();
                entityManager.persist(customer);
                entityManager.flush();
                return null;
            }
        });
    }

    protected void assertEntitiesInDatabase(int count, String entity) {
        List<?> results = entityManager.createQuery("select o from " + entity + " o").getResultList();
        assertEquals(count, results.size());
    }

    @Test
    public void testPollingConsumerHandlesLockedEntity() throws Exception {
        Customer customer = new Customer();
        customer.setName("Donald Duck");
        save(customer);

        Customer customer2 = new Customer();
        customer2.setName("Goofy");
        save(customer2);

        assertEntitiesInDatabase(2, Customer.class.getName());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived(
            "orders: 1",
            "orders: 2"
        );

        Map<String, Object> headers = new HashMap<>();
        headers.put("name", "Donald%");

        template.asyncRequestBodyAndHeaders("direct:start", "message", headers);
        template.asyncRequestBodyAndHeaders("direct:start", "message", headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new SpringRouteBuilder() {
            public void configure() {
                from("direct:start")
                    .transacted()
                    .pollEnrich().simple("jpa://" + Customer.class.getName() + "?joinTransaction=true&consumeLockEntity=true&query=select c from Customer c where c.name like '${header.name}'")
                    .aggregationStrategy((originalExchange, jpaExchange) -> {
                        Customer customer = jpaExchange.getIn().getBody(Customer.class);
                        customer.setOrderCount(customer.getOrderCount()+1);

                        return jpaExchange;
                    })
                    .to("jpa://" + Customer.class.getName() + "?joinTransaction=true&usePassedInEntityManager=true")
                    .setBody().simple("orders: ${body.orderCount}")
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
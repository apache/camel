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
import java.util.Map;

import javax.persistence.OptimisticLockException;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.examples.Customer;
import org.apache.camel.spring.SpringRouteBuilder;
import org.junit.Before;
import org.junit.Test;

public class JpaPollingConsumerLockEntityTest extends AbstractJpaTest {
    protected static final String SELECT_ALL_STRING = "select x from " + Customer.class.getName() + " x";

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        Customer customer = new Customer();
        customer.setName("Donald Duck");
        saveEntityInDB(customer);

        Customer customer2 = new Customer();
        customer2.setName("Goofy");
        saveEntityInDB(customer2);

        assertEntityInDB(2, Customer.class);
    }

    @Test
    public void testPollingConsumerWithLock() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:locked");
        mock.expectedBodiesReceived(
            "orders: 1",
            "orders: 2"
        );

        Map<String, Object> headers = new HashMap<>();
        headers.put("name", "Donald%");

        template.asyncRequestBodyAndHeaders("direct:locked", "message", headers);
        template.asyncRequestBodyAndHeaders("direct:locked", "message", headers);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testPollingConsumerWithoutLock() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:not-locked");
        MockEndpoint errMock = getMockEndpoint("mock:error");

        mock.expectedBodiesReceived("orders: 1");

        errMock.expectedMessageCount(1);
        errMock.message(0).body().isInstanceOf(OptimisticLockException.class);

        Map<String, Object> headers = new HashMap<>();
        headers.put("name", "Donald%");

        template.asyncRequestBodyAndHeaders("direct:not-locked", "message", headers);
        template.asyncRequestBodyAndHeaders("direct:not-locked", "message", headers);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new SpringRouteBuilder() {
            public void configure() {

                AggregationStrategy enrichStrategy = new AggregationStrategy() {
                    @Override
                    public Exchange aggregate(Exchange originalExchange, Exchange jpaExchange) {
                        Customer customer = jpaExchange.getIn().getBody(Customer.class);
                        customer.setOrderCount(customer.getOrderCount() + 1);

                        return jpaExchange;
                    }
                };

                onException(Exception.class)
                    .setBody().simple("${exception}")
                    .to("mock:error")
                    .handled(true);

                from("direct:locked")
                    .onException(OptimisticLockException.class)
                        .redeliveryDelay(60)
                        .maximumRedeliveries(2)
                    .end()
                    .pollEnrich().simple("jpa://" + Customer.class.getName() + "?lockModeType=OPTIMISTIC_FORCE_INCREMENT&query=select c from Customer c where c.name like '${header.name}'")
                    .aggregationStrategy(enrichStrategy)
                    .to("jpa://" + Customer.class.getName())
                    .setBody().simple("orders: ${body.orderCount}")
                    .to("mock:locked");

                from("direct:not-locked")
                    .pollEnrich().simple("jpa://" + Customer.class.getName() + "?query=select c from Customer c where c.name like '${header.name}'")
                    .aggregationStrategy(enrichStrategy)
                    .to("jpa://" + Customer.class.getName())
                    .setBody().simple("orders: ${body.orderCount}")
                    .to("mock:not-locked");
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

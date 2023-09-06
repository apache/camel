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
package org.apache.camel.component.jpa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.examples.Customer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.SimpleRegistry;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JpaWithNamedQueryAndParametersTest {

    protected static final Logger LOG = LoggerFactory.getLogger(JpaWithNamedQueryAndParametersTest.class);

    protected DefaultCamelContext camelContext;
    protected ProducerTemplate template;
    protected JpaEndpoint endpoint;
    protected EntityManager entityManager;
    protected TransactionTemplate transactionTemplate;
    protected Consumer consumer;
    protected Exchange receivedExchange;
    protected CountDownLatch latch = new CountDownLatch(1);
    protected String entityName = Customer.class.getName();
    protected String queryText = "select o from " + entityName + " o where o.name like 'Willem'";

    @Test
    public void testProducerInsertsIntoDatabaseThenConsumerFiresMessageExchange() throws Exception {
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                entityManager.joinTransaction();
                // lets delete any exiting records before the test
                entityManager.createQuery("delete from " + entityName).executeUpdate();
                // now lets create a dummy entry
                Customer dummy = new Customer();
                dummy.setName("Test");
                entityManager.persist(dummy);
                return null;
            }
        });

        List<?> results = entityManager.createQuery(queryText).getResultList();
        assertEquals(0, results.size(), "Should have no results: " + results);

        // lets produce some objects
        template.send("jpa://" + Customer.class.getName(), new Processor() {
            public void process(Exchange exchange) {
                Customer customer = new Customer();
                customer.setName("Willem");
                exchange.getIn().setBody(customer);
            }
        });

        // now lets assert that there is a result
        results = entityManager.createQuery(queryText).getResultList();
        assertEquals(1, results.size(), "Should have results: " + results);
        Customer customer = (Customer) results.get(0);
        assertEquals("Willem", customer.getName(), "name property");

        // now lets create a consumer to consume it
        consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange e) {
                LOG.info("Received exchange: {}", e.getIn());
                receivedExchange = e;
                latch.countDown();
            }
        });
        consumer.start();

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertReceivedResult(receivedExchange);

        JpaConsumer jpaConsumer = (JpaConsumer) consumer;
        assertURIQueryOption(jpaConsumer);
    }

    protected void assertReceivedResult(Exchange exchange) {
        assertNotNull(exchange);
        Customer result = exchange.getIn().getBody(Customer.class);
        assertNotNull(result, "Received a POJO");
        assertEquals("Willem", result.getName(), "name property");
    }

    protected void assertURIQueryOption(JpaConsumer jpaConsumer) {
        assertEquals("findAllCustomersWithName", jpaConsumer.getNamedQuery());
    }

    @BeforeEach
    public void setUp() {
        camelContext = new DefaultCamelContext();
        SimpleRegistry registry = new SimpleRegistry();
        Map<String, Object> params = new HashMap<>();
        params.put("custName", "Willem");
        // bind the params
        registry.bind("params", params);
        camelContext.getCamelContextExtension().setRegistry(registry);

        camelContext.start();

        template = camelContext.createProducerTemplate();

        Endpoint value = camelContext.getEndpoint(getEndpointUri());
        assertNotNull(value, "Could not find endpoint!");
        assertTrue(value instanceof JpaEndpoint, "Should be a JPA endpoint but was: " + value);
        endpoint = (JpaEndpoint) value;

        if (endpoint.getTransactionStrategy() instanceof DefaultTransactionStrategy strategy) {
            transactionTemplate = strategy.getTransactionTemplate();
        }
        entityManager = endpoint.getEntityManagerFactory().createEntityManager();
    }

    protected String getEndpointUri() {
        return "jpa://" + Customer.class.getName() + "?namedQuery=findAllCustomersWithName&parameters=#params";
    }

    @AfterEach
    public void tearDown() {
        ServiceHelper.stopService(consumer, template);
        camelContext.stop();
    }
}

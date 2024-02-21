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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.examples.SendEmail;
import org.apache.camel.impl.DefaultCamelContext;
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

public class JpaTest {
    private static final Logger LOG = LoggerFactory.getLogger(JpaTest.class);
    protected CamelContext camelContext = new DefaultCamelContext();
    protected ProducerTemplate template;
    protected JpaComponent component;
    protected JpaEndpoint endpoint;
    protected JpaEndpoint listEndpoint;
    protected EntityManager entityManager;
    protected TransactionTemplate transactionTemplate;
    protected Consumer consumer;
    protected Exchange receivedExchange;
    protected CountDownLatch latch = new CountDownLatch(1);
    protected String entityName = SendEmail.class.getName();
    protected String queryText = "select o from " + entityName + " o";

    @Test
    public void testProducerInsertsIntoDatabaseThenConsumerFiresMessageExchange() throws Exception {
        // lets produce some objects
        template.send(endpoint, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody(new SendEmail("foo@bar.com"));
            }
        });

        // now lets assert that there is a result
        List<?> results = entityManager.createQuery(queryText).getResultList();
        assertEquals(1, results.size(), "Should have results: " + results);
        SendEmail mail = (SendEmail) results.get(0);
        assertEquals("foo@bar.com", mail.getAddress(), "address property");

        // now lets create a consumer to consume it
        consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange e) {
                LOG.info("Received exchange: {}", e.getIn());
                receivedExchange = e;
                // should have a EntityManager
                EntityManager entityManager = e.getIn().getHeader(JpaConstants.ENTITY_MANAGER, EntityManager.class);
                assertNotNull(entityManager, "Should have a EntityManager as header");
                latch.countDown();
            }
        });
        consumer.start();

        assertTrue(latch.await(50, TimeUnit.SECONDS));

        assertNotNull(receivedExchange);
        SendEmail result = receivedExchange.getIn().getBody(SendEmail.class);
        assertNotNull(result, "Received a POJO");
        assertEquals("foo@bar.com", result.getAddress(), "address property");
    }

    @Test
    public void testProducerInsertsList() {
        // lets produce some objects
        template.send(listEndpoint, new Processor() {
            public void process(Exchange exchange) {
                // use a list
                List<Object> list = new ArrayList<>();
                list.add(new SendEmail("foo@bar.com"));
                list.add(new SendEmail("foo2@bar.com"));
                exchange.getIn().setBody(list);
            }
        });

        // now lets assert that there is a result
        List<?> results = entityManager.createQuery(queryText).getResultList();
        assertEquals(2, results.size(), "Should have results: " + results);
        SendEmail mail = (SendEmail) results.get(0);
        assertEquals("foo@bar.com", mail.getAddress(), "address property");
        assertNotNull(mail.getId(), "id");

        SendEmail mail2 = (SendEmail) results.get(1);
        assertEquals("foo2@bar.com", mail2.getAddress(), "address property");
        assertNotNull(mail2.getId(), "id");
    }

    @BeforeEach
    public void setUp() {
        camelContext.start();
        template = camelContext.createProducerTemplate();

        setUpComponent();

        Endpoint value = camelContext.getEndpoint(getEndpointUri());
        assertNotNull(value, "Could not find endpoint!");
        assertTrue(value instanceof JpaEndpoint, "Should be a JPA endpoint but was: " + value);
        endpoint = (JpaEndpoint) value;

        listEndpoint = camelContext.getEndpoint(getEndpointUri() + "&entityType=java.util.List", JpaEndpoint.class);

        if (endpoint.getTransactionStrategy() instanceof DefaultTransactionStrategy strategy) {
            transactionTemplate = strategy.getTransactionTemplate();
        }
        entityManager = endpoint.getEntityManagerFactory().createEntityManager();

        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                entityManager.joinTransaction();
                // lets delete any exiting records before the test
                entityManager.createQuery("delete from " + entityName).executeUpdate();
                return null;
            }
        });

        List<?> results = entityManager.createQuery(queryText).getResultList();
        assertEquals(0, results.size(), "Should have no results: " + results);
    }

    protected String getEndpointUri() {
        return "jpa://" + SendEmail.class.getName();
    }

    protected void setUpComponent() {
        // no set up in this test
    }

    @AfterEach
    public void tearDown() {
        ServiceHelper.stopService(consumer, template);
        camelContext.stop();
    }
}

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
import org.apache.camel.examples.MultiSteps;
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

public class JpaWithNamedQueryTest {

    protected static final Logger LOG = LoggerFactory.getLogger(JpaWithNamedQueryTest.class);

    protected CamelContext camelContext = new DefaultCamelContext();
    protected ProducerTemplate template;
    protected JpaEndpoint endpoint;
    protected EntityManager entityManager;
    protected TransactionTemplate transactionTemplate;
    protected Consumer consumer;
    protected Exchange receivedExchange;
    protected CountDownLatch latch = new CountDownLatch(1);
    protected String entityName = MultiSteps.class.getName();
    protected String queryText = "select o from " + entityName + " o where o.step = 1";

    @Test
    public void testProducerInsertsIntoDatabaseThenConsumerFiresMessageExchange() throws Exception {
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                entityManager.joinTransaction();
                // lets delete any exiting records before the test
                entityManager.createQuery("delete from " + entityName).executeUpdate();

                // now lets create a dummy entry
                MultiSteps dummy = new MultiSteps("cheese");
                dummy.setStep(4);
                entityManager.persist(dummy);
                return null;
            }
        });

        List<?> results = entityManager.createQuery(queryText).getResultList();
        assertEquals(0, results.size(), "Should have no results: " + results);

        // lets produce some objects
        template.send("jpa://" + MultiSteps.class.getName(), new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody(new MultiSteps("foo@bar.com"));
            }
        });

        // now lets assert that there is a result
        results = entityManager.createQuery(queryText).getResultList();
        assertEquals(1, results.size(), "Should have results: " + results);
        MultiSteps mail = (MultiSteps) results.get(0);
        assertEquals("foo@bar.com", mail.getAddress(), "address property");

        // now lets create a consumer to consume it
        consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange e) {
                LOG.info("Received exchange: {}", e.getIn());
                // make defensive copy
                receivedExchange = e.copy();
                latch.countDown();
            }
        });
        consumer.start();

        assertTrue(latch.await(50, TimeUnit.SECONDS));

        assertReceivedResult(receivedExchange);

        // lets now test that the database is updated
        // we need to sleep as we will be invoked from inside the transaction!
        // org.apache.openjpa.persistence.InvalidStateException: This operation cannot be performed while a Transaction is active.
        Thread.sleep(2000);

        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                // make use of the EntityManager having the relevant persistence-context
                EntityManager entityManager2
                        = receivedExchange.getIn().getHeader(JpaConstants.ENTITY_MANAGER, EntityManager.class);
                if (!entityManager2.isOpen()) {
                    entityManager2 = endpoint.getEntityManagerFactory().createEntityManager();
                }
                entityManager2.joinTransaction();

                // now lets assert that there are still 2 entities left
                List<?> rows = entityManager2.createQuery("select x from MultiSteps x").getResultList();
                assertEquals(2, rows.size(), "Number of entities: " + rows);

                int counter = 1;
                for (Object rowObj : rows) {
                    assertTrue(rowObj instanceof MultiSteps, "Rows are not instances of MultiSteps");
                    final MultiSteps row = (MultiSteps) rowObj;
                    LOG.info("entity: {} = {}", counter++, row);

                    if (row.getAddress().equals("foo@bar.com")) {
                        LOG.info("Found updated row: {}", row);
                        assertEquals(getUpdatedStepValue(), row.getStep(), "Updated row step for: " + row);
                    } else {
                        // dummy row
                        assertEquals(4, row.getStep(), "dummy row step for: " + row);
                        assertEquals("cheese", row.getAddress(), "Not the expected row: " + row);
                    }
                }
                return null;
            }
        });

        JpaConsumer jpaConsumer = (JpaConsumer) consumer;
        assertURIQueryOption(jpaConsumer);
    }

    protected void assertReceivedResult(Exchange exchange) {
        assertNotNull(exchange);
        MultiSteps result = exchange.getIn().getBody(MultiSteps.class);
        assertNotNull(result, "Received a POJO");
        assertEquals("foo@bar.com", result.getAddress(), "address property");
    }

    protected int getUpdatedStepValue() {
        return 2;
    }

    protected void assertURIQueryOption(JpaConsumer jpaConsumer) {
        assertEquals("step1", jpaConsumer.getNamedQuery());
    }

    @BeforeEach
    public void setUp() {
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
        return "jpa://" + MultiSteps.class.getName() + "?namedQuery=step1";
    }

    @AfterEach
    public void tearDown() {
        ServiceHelper.stopService(consumer, template);
        camelContext.stop();
    }
}

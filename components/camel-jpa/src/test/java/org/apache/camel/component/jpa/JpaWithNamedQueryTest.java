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
package org.apache.camel.component.jpa;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.examples.MultiSteps;
import org.apache.camel.impl.DefaultCamelContext;
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

/**
 * @version 
 */
public class JpaWithNamedQueryTest extends Assert {
    
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
        assertEquals("Should have no results: " + results, 0, results.size());

        // lets produce some objects
        template.send(endpoint, new Processor() {
            public void process(Exchange exchange) {
                exchange.getIn().setBody(new MultiSteps("foo@bar.com"));
            }
        });

        // now lets assert that there is a result
        results = entityManager.createQuery(queryText).getResultList();
        assertEquals("Should have results: " + results, 1, results.size());
        MultiSteps mail = (MultiSteps)results.get(0);
        assertEquals("address property", "foo@bar.com", mail.getAddress());

        // now lets create a consumer to consume it
        consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange e) {
                LOG.info("Received exchange: " + e.getIn());
                receivedExchange = e;
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
                EntityManager entityManager2 = receivedExchange.getIn().getHeader(JpaConstants.ENTITY_MANAGER, EntityManager.class);
                if (!entityManager2.isOpen()) {
                    entityManager2 = endpoint.getEntityManagerFactory().createEntityManager();
                }
                entityManager2.joinTransaction();

                // now lets assert that there are still 2 entities left
                List<?> rows = entityManager2.createQuery("select x from MultiSteps x").getResultList();
                assertEquals("Number of entities: " + rows, 2, rows.size());

                int counter = 1;
                for (Object rowObj : rows) {
                    assertTrue("Rows are not instances of MultiSteps",  rowObj instanceof MultiSteps);
                    final MultiSteps row = (MultiSteps) rowObj;
                    LOG.info("entity: " + counter++ + " = " + row);

                    if (row.getAddress().equals("foo@bar.com")) {
                        LOG.info("Found updated row: " + row);
                        assertEquals("Updated row step for: " + row, getUpdatedStepValue(), row.getStep());
                    } else {
                        // dummy row
                        assertEquals("dummy row step for: " + row, 4, row.getStep());
                        assertEquals("Not the expected row: " + row, "cheese", row.getAddress());
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
        assertNotNull("Received a POJO", result);
        assertEquals("address property", "foo@bar.com", result.getAddress());
    }
    
    protected int getUpdatedStepValue() {
        return 2;
    }
    
    protected void assertURIQueryOption(JpaConsumer jpaConsumer) {
        assertEquals("step1", jpaConsumer.getNamedQuery());
    }

    @Before
    public void setUp() throws Exception {
        template = camelContext.createProducerTemplate();
        ServiceHelper.startServices(template, camelContext);

        Endpoint value = camelContext.getEndpoint(getEndpointUri());
        assertNotNull("Could not find endpoint!", value);
        assertTrue("Should be a JPA endpoint but was: " + value, value instanceof JpaEndpoint);
        endpoint = (JpaEndpoint)value;

        transactionTemplate = endpoint.createTransactionTemplate();
        entityManager = endpoint.getEntityManagerFactory().createEntityManager();
    }

    protected String getEndpointUri() {
        return "jpa://" + MultiSteps.class.getName() + "?consumer.namedQuery=step1";
    }

    @After
    public void tearDown() throws Exception {
        ServiceHelper.stopServices(consumer, template, camelContext);
    }
}

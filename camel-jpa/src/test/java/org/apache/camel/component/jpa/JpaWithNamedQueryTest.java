/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jpa;

import junit.framework.TestCase;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.examples.MultiSteps;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.CamelClient;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.orm.jpa.JpaCallback;
import org.springframework.orm.jpa.JpaTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @version $Revision$
 */
public class JpaWithNamedQueryTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(JpaWithNamedQueryTest.class);
    protected CamelContext camelContext = new DefaultCamelContext();
    protected CamelClient client = new CamelClient(camelContext);
    protected JpaEndpoint endpoint;
    protected TransactionStrategy transactionStrategy;
    protected JpaTemplate template;
    protected Consumer<Exchange> consumer;
    protected Exchange receivedExchange;
    protected CountDownLatch latch = new CountDownLatch(1);
    protected String entityName = MultiSteps.class.getName();
    protected String queryText = "select o from " + entityName + " o where o.step = 1";

    public void testProducerInsertsIntoDatabaseThenConsumerFiresMessageExchange() throws Exception {
        transactionStrategy.execute(new JpaCallback() {
            public Object doInJpa(EntityManager entityManager) throws PersistenceException {
                // lets delete any exiting records before the test
                entityManager.createQuery("delete from " + entityName).executeUpdate();

                // now lets create a dummy entry
                MultiSteps dummy = new MultiSteps("cheese");
                dummy.setStep(4);
                entityManager.persist(dummy);
                return null;
            }
        });

        List results = template.find(queryText);
        assertEquals("Should have no results: " + results, 0, results.size());

        // lets produce some objects
        client.send(endpoint, new Processor<Exchange>() {
            public void onExchange(Exchange exchange) {
                exchange.getIn().setBody(new MultiSteps("foo@bar.com"));
            }
        });

        // now lets assert that there is a result
        results = template.find(queryText);
        assertEquals("Should have results: " + results, 1, results.size());
        MultiSteps mail = (MultiSteps) results.get(0);
        assertEquals("address property", "foo@bar.com", mail.getAddress());

        // now lets create a consumer to consume it
        consumer = endpoint.createConsumer(new Processor<Exchange>() {
            public void onExchange(Exchange e) {
                log.info("Received exchange: " + e.getIn());
                receivedExchange = e;
                latch.countDown();
            }
        });

        boolean received = latch.await(50, TimeUnit.SECONDS);
        assertTrue("Did not receive the message!", received);

        assertNotNull(receivedExchange);
        MultiSteps result = receivedExchange.getIn().getBody(MultiSteps.class);
        assertNotNull("Received a POJO", result);
        assertEquals("address property", "foo@bar.com", result.getAddress());


        // lets now test that the database is updated
        // TODO we need to sleep as we will be invoked from inside the transaction!
        Thread.sleep(1000);
        
        transactionStrategy.execute(new JpaCallback() {
            public Object doInJpa(EntityManager entityManager) throws PersistenceException {

                // now lets assert that there are still 2 entities left
                List<MultiSteps> rows = entityManager.createQuery("select x from MultiSteps x").getResultList();
                assertEquals("Number of entities: " + rows, 2, rows.size());

                int counter = 1;
                for (MultiSteps row : rows) {
                    log.info("entity: " + counter++ + " = " + row);

                    if (row.getAddress().equals("foo@bar.com")) {
                        log.info("Found updated row: " + row);

                        assertEquals("Updated row step for: " + row, 2, row.getStep());
                    }
                    else {
                        // dummy row
                        assertEquals("dummy row step for: " + row, 4, row.getStep());
                    }
                }
                return null;
            }
        });
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        ServiceHelper.startServices(client, camelContext);

        Endpoint value = camelContext.resolveEndpoint(getEndpointUri());
        assertNotNull("Could not find endpoint!", value);
        assertTrue("Should be a JPA endpoint but was: " + value, value instanceof JpaEndpoint);
        endpoint = (JpaEndpoint) value;

        transactionStrategy = endpoint.createTransactionStrategy();
        template = endpoint.getTemplate();
    }

    protected String getEndpointUri() {
        return "jpa://" + MultiSteps.class.getName() + "?consumer.namedQuery=step1";
    }

    @Override
    protected void tearDown() throws Exception {

        ServiceHelper.stopServices(consumer, client, camelContext);

        super.tearDown();
    }
}

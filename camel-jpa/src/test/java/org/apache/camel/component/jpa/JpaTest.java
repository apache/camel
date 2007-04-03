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
import org.apache.camel.examples.SendEmail;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.CamelClient;
import static org.apache.camel.util.ServiceHelper.startServices;
import static org.apache.camel.util.ServiceHelper.stopServices;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @version $Revision$
 */
public class JpaTest extends TestCase {
    private static final transient Log log = LogFactory.getLog(JpaTest.class);
    protected CamelContext camelContext = new DefaultCamelContext();
    protected CamelClient client = new CamelClient(camelContext);
    protected JpaEndpoint endpoint;
    protected EntityManager entityManager;
    protected Consumer<Exchange> consumer;
    protected Exchange receivedExchange;
    protected CountDownLatch latch = new CountDownLatch(1);
    protected String entityName = SendEmail.class.getName();
    protected String queryText = "select o from " + entityName + " o";
    protected EntityTransaction transaction;

    public void testProducerInsertsIntoDatabaseThenConsumerFiresMessageExchange() throws Exception {
        // lets assert that there are no existing send mail tasks
        transaction = entityManager.getTransaction();
        transaction.begin();

        // lets delete any exiting records before the test
        entityManager.createQuery("delete from " + entityName).executeUpdate();

        List results = entityManager.createQuery(queryText).getResultList();
        assertEquals("Should have no results: " + results, 0, results.size());
        transaction.commit();

        // lets produce some objects
        client.send(endpoint, new Processor<Exchange>() {
            public void onExchange(Exchange exchange) {
                exchange.getIn().setBody(new SendEmail("foo@bar.com"));
            }
        });

        // now lets assert that there is a result
        transaction.begin();
        results = entityManager.createQuery(queryText).getResultList();
        assertEquals("Should have no results: " + results, 1, results.size());
        SendEmail mail = (SendEmail) results.get(0);
        assertEquals("address property", "foo@bar.com", mail.getAddress());
        transaction.commit();
        transaction = null;

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
        SendEmail result = receivedExchange.getIn().getBody(SendEmail.class);
        assertNotNull("Received a POJO", result);
        assertEquals("address property", "foo@bar.com", result.getAddress());
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        camelContext.addComponent("foo", createJpaComponent());

        startServices(client, camelContext);

        Endpoint value = camelContext.resolveEndpoint("jpa:" + SendEmail.class.getName());
        assertNotNull("Could not find endpoint!", value);
        assertTrue("Should be a JPA endpoint but was: " + value, value instanceof JpaEndpoint);
        endpoint = (JpaEndpoint) value;

        entityManager = endpoint.createEntityManager();
    }

    protected JpaComponent createJpaComponent() {
        JpaComponent answer = new JpaComponent();
/*
        Properties properties = new Properties();
        properties.setProperty("openjpa.ConnectionDriverName", "org.apache.derby.jdbc.EmbeddedDriver");
        properties.setProperty("openjpa.ConnectionURL", "jdbc:derby:target/derby;create=true");
        properties.setProperty("openjpa.jdbc.SynchronizeMappings", "buildSchema");
        properties.setProperty("openjpa.Log", "DefaultLevel=WARN,SQL=TRACE");
        answer.setEntityManagerProperties(properties);
*/
        return answer;
    }

    @Override
    protected void tearDown() throws Exception {
        if (transaction != null) {
            transaction.rollback();
            transaction = null;
        }
        if (entityManager != null) {
            entityManager.close();
        }
        stopServices(consumer, client, camelContext);

        super.tearDown();
    }
}

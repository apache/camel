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
import javax.persistence.PersistenceException;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.examples.Address;
import org.apache.camel.examples.Customer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.orm.jpa.JpaCallback;
import org.springframework.orm.jpa.JpaTemplate;

import static org.apache.camel.util.ServiceHelper.startServices;
import static org.apache.camel.util.ServiceHelper.stopServices;
/**
 * @version $Revision: 931444 $
 */
public class JpaUsePersistTest extends Assert {
    
    protected CamelContext camelContext = new DefaultCamelContext();
    protected ProducerTemplate template;
    protected JpaEndpoint endpoint;
    protected TransactionStrategy transactionStrategy;
    protected JpaTemplate jpaTemplate;
    protected Consumer consumer;
    protected Exchange receivedExchange;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() throws Exception {
        template = camelContext.createProducerTemplate();
        startServices(template, camelContext);

        endpoint = camelContext.getEndpoint("jpa://" + Customer.class.getName() + "?usePersist=true", JpaEndpoint.class);

        transactionStrategy = endpoint.createTransactionStrategy();
        jpaTemplate = endpoint.getTemplate();
        
        transactionStrategy.execute(new JpaCallback() {
            public Object doInJpa(EntityManager entityManager) throws PersistenceException {
                entityManager.createQuery("delete from " + Customer.class.getName()).executeUpdate();
                return null;
            }
        });
        
        assertEntitiesInDatabase(0, Customer.class.getName());
        assertEntitiesInDatabase(0, Address.class.getName());
    }
    
    @After
    public void tearDown() throws Exception {
        stopServices(consumer, template, camelContext);
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void produceNewEntity() throws Exception {
        Customer customer = createDefaultCustomer();
        
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(customer);
        Exchange returnedExchange = template.send(endpoint, exchange);
        
        Customer receivedCustomer = returnedExchange.getIn().getBody(Customer.class);
        assertEquals(customer.getName(), receivedCustomer.getName());
        assertNotNull(receivedCustomer.getId());
        assertEquals(customer.getAddress().getAddressLine1(), receivedCustomer.getAddress().getAddressLine1());
        assertEquals(customer.getAddress().getAddressLine2(), receivedCustomer.getAddress().getAddressLine2());
        assertNotNull(receivedCustomer.getAddress().getId());
        
        List results = jpaTemplate.find("select o from " + Customer.class.getName() + " o");
        assertEquals(1, results.size());
        Customer persistedCustomer = (Customer) results.get(0);
        assertEquals(receivedCustomer.getName(), persistedCustomer.getName());
        assertEquals(receivedCustomer.getId(), persistedCustomer.getId());
        assertEquals(receivedCustomer.getAddress().getAddressLine1(), persistedCustomer.getAddress().getAddressLine1());
        assertEquals(receivedCustomer.getAddress().getAddressLine2(), persistedCustomer.getAddress().getAddressLine2());
        assertEquals(receivedCustomer.getAddress().getId(), persistedCustomer.getAddress().getId());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void produceExistingEntityShouldThowAnException() throws Exception {
        final Customer customer = createDefaultCustomer();
        transactionStrategy.execute(new JpaCallback() {
            public Object doInJpa(EntityManager entityManager) throws PersistenceException {
                entityManager.persist(customer);
                entityManager.flush();
                return null;
            }
        });
        
        assertEntitiesInDatabase(1, Customer.class.getName());
        assertEntitiesInDatabase(1, Address.class.getName());
        
        customer.setName("Max Mustermann");
        customer.getAddress().setAddressLine1("Musterstr. 1");
        customer.getAddress().setAddressLine2("11111 Enterhausen");
        
        Exchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(customer);
        Exchange returnedExchange = template.send(endpoint, exchange);
        
        assertTrue(returnedExchange.isFailed());
        assertNotNull(returnedExchange.getException());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void consumeEntity() throws Exception {
        final Customer customer = createDefaultCustomer();
        transactionStrategy.execute(new JpaCallback() {
            public Object doInJpa(EntityManager entityManager) throws PersistenceException {
                entityManager.persist(customer);
                entityManager.flush();
                return null;
            }
        });
        
        final CountDownLatch latch = new CountDownLatch(1);
        
        consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange e) {
                receivedExchange = e;
                assertNotNull(e.getIn().getHeader(JpaConstants.JPA_TEMPLATE, JpaTemplate.class));
                latch.countDown();
            }
        });
        consumer.start();
        
        boolean received = latch.await(50, TimeUnit.SECONDS);
        
        assertTrue(received);
        assertNotNull(receivedExchange);
        Customer receivedCustomer = receivedExchange.getIn().getBody(Customer.class);
        assertEquals(customer.getName(), receivedCustomer.getName());
        assertEquals(customer.getId(), receivedCustomer.getId());
        assertEquals(customer.getAddress().getAddressLine1(), receivedCustomer.getAddress().getAddressLine1());
        assertEquals(customer.getAddress().getAddressLine2(), receivedCustomer.getAddress().getAddressLine2());
        assertEquals(customer.getAddress().getId(), receivedCustomer.getAddress().getId());

        // give a bit tiem for consumer to delete after done
        Thread.sleep(1000);
        
        assertEntitiesInDatabase(0, Customer.class.getName());
        assertEntitiesInDatabase(0, Address.class.getName());
    }
    
    @SuppressWarnings("unchecked")
    private void assertEntitiesInDatabase(int count, String entity) {
        List results = jpaTemplate.find("select o from " + entity + " o");
        assertEquals(count, results.size());
    }

    private Customer createDefaultCustomer() {
        Customer customer = new Customer();
        customer.setName("Christian Mueller");
        Address address = new Address();
        address.setAddressLine1("Hahnstr. 1");
        address.setAddressLine2("60313 Frankfurt am Main");
        customer.setAddress(address);
        return customer;
    }
}
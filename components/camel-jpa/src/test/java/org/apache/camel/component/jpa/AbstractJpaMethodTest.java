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

import javax.persistence.EntityManager;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.examples.Address;
import org.apache.camel.examples.Customer;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public abstract class AbstractJpaMethodTest extends CamelTestSupport {
    
    protected JpaEndpoint endpoint;
    protected EntityManager entityManager;
    protected TransactionTemplate transactionTemplate;
    protected Consumer consumer;
    protected Exchange receivedExchange;
    
    abstract boolean usePersist();
    
    @Override
    @After
    public void tearDown() throws Exception {
        if (entityManager != null) {
            entityManager.close();
        }
    }
    
    @Test
    public void produceNewEntity() throws Exception {
        setUp("jpa://" + Customer.class.getName() + "?usePersist=" + (usePersist() ? "true" : "false"));
        
        Customer customer = createDefaultCustomer();
        Customer receivedCustomer = template.requestBody(endpoint, customer, Customer.class);
        
        assertEquals(customer.getName(), receivedCustomer.getName());
        assertNotNull(receivedCustomer.getId());
        assertEquals(customer.getAddress().getAddressLine1(), receivedCustomer.getAddress().getAddressLine1());
        assertEquals(customer.getAddress().getAddressLine2(), receivedCustomer.getAddress().getAddressLine2());
        assertNotNull(receivedCustomer.getAddress().getId());
        
        List<?> results = entityManager.createQuery("select o from " + Customer.class.getName() + " o").getResultList();
        assertEquals(1, results.size());
        Customer persistedCustomer = (Customer) results.get(0);
        assertEquals(receivedCustomer.getName(), persistedCustomer.getName());
        assertEquals(receivedCustomer.getId(), persistedCustomer.getId());
        assertEquals(receivedCustomer.getAddress().getAddressLine1(), persistedCustomer.getAddress().getAddressLine1());
        assertEquals(receivedCustomer.getAddress().getAddressLine2(), persistedCustomer.getAddress().getAddressLine2());
        assertEquals(receivedCustomer.getAddress().getId(), persistedCustomer.getAddress().getId());
    }

    @Test
    public void produceNewEntitiesFromList() throws Exception {
        setUp("jpa://" + List.class.getName() + "?usePersist=" + (usePersist() ? "true" : "false"));
        
        List<Customer> customers = new ArrayList<>();
        customers.add(createDefaultCustomer());
        customers.add(createDefaultCustomer());
        List<?> returnedCustomers = template.requestBody(endpoint, customers, List.class);
        
        assertEquals(2, returnedCustomers.size());
        
        assertEntitiesInDatabase(2, Customer.class.getName());
        assertEntitiesInDatabase(2, Address.class.getName());
    }
    
    @Test
    public void produceNewEntitiesFromArray() throws Exception {
        setUp("jpa://" + Customer[].class.getName() + "?usePersist=" + (usePersist() ? "true" : "false"));
        
        Customer[] customers = new Customer[] {createDefaultCustomer(), createDefaultCustomer()};
        Object reply = template.requestBody(endpoint, customers);
        
        Customer[] returnedCustomers = (Customer[]) reply;
        assertEquals(2, returnedCustomers.length);
        
        assertEntitiesInDatabase(2, Customer.class.getName());
        assertEntitiesInDatabase(2, Address.class.getName());
    }

    @Test
    public void consumeEntity() throws Exception {
        setUp("jpa://" + Customer.class.getName() + "?usePersist=" + (usePersist() ? "true" : "false"));
    
        final Customer customer = createDefaultCustomer();
        save(customer);

        assertEntitiesInDatabase(1, Customer.class.getName());
        assertEntitiesInDatabase(1, Address.class.getName());

        final CountDownLatch latch = new CountDownLatch(1);
        
        consumer = endpoint.createConsumer(new Processor() {
            public void process(Exchange e) {
                receivedExchange = e;
                assertNotNull(e.getIn().getHeader(JpaConstants.ENTITY_MANAGER, EntityManager.class));
                latch.countDown();
            }
        });
        consumer.start();
        
        assertTrue(latch.await(50, TimeUnit.SECONDS));

        consumer.stop();
        Thread.sleep(1000);
        
        assertNotNull(receivedExchange);
        Customer receivedCustomer = receivedExchange.getIn().getBody(Customer.class);
        assertEquals(customer.getName(), receivedCustomer.getName());
        assertEquals(customer.getId(), receivedCustomer.getId());
        assertEquals(customer.getAddress().getAddressLine1(), receivedCustomer.getAddress().getAddressLine1());
        assertEquals(customer.getAddress().getAddressLine2(), receivedCustomer.getAddress().getAddressLine2());
        assertEquals(customer.getAddress().getId(), receivedCustomer.getAddress().getId());
        
        // give a bit time for consumer to delete after done
        Thread.sleep(1000);
        
        assertEntitiesInDatabase(0, Customer.class.getName());
        assertEntitiesInDatabase(0, Address.class.getName());
    }

    protected void setUp(String endpointUri) throws Exception {
        endpoint = context.getEndpoint(endpointUri, JpaEndpoint.class);

        transactionTemplate = endpoint.createTransactionTemplate();
        entityManager = endpoint.getEntityManagerFactory().createEntityManager();
        
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                entityManager.joinTransaction();
                entityManager.createQuery("delete from " + Customer.class.getName()).executeUpdate();
                return null;
            }
        });
        
        assertEntitiesInDatabase(0, Customer.class.getName());
        assertEntitiesInDatabase(0, Address.class.getName());
    }
    
    protected void save(final Object persistable) {
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                entityManager.joinTransaction();
                entityManager.persist(persistable);
                entityManager.flush();
                return null;
            }
        });
    }
    
    protected void assertEntitiesInDatabase(int count, String entity) {
        List<?> results = entityManager.createQuery("select o from " + entity + " o").getResultList();
        assertEquals(count, results.size());
    }

    protected Customer createDefaultCustomer() {
        Customer customer = new Customer();
        customer.setName("Christian Mueller");
        Address address = new Address();
        address.setAddressLine1("Hahnstr. 1");
        address.setAddressLine2("60313 Frankfurt am Main");
        customer.setAddress(address);
        return customer;
    }
}

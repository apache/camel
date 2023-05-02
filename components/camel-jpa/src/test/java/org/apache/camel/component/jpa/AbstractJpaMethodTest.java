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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.examples.Address;
import org.apache.camel.examples.Customer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractJpaMethodTest extends AbstractJpaMethodSupport {

    protected Customer receivedCustomer;

    abstract boolean usePersist();

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

        Customer[] customers = new Customer[] { createDefaultCustomer(), createDefaultCustomer() };
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
                receivedCustomer = e.getIn().getBody(Customer.class);
                assertNotNull(e.getIn().getHeader(JpaConstants.ENTITY_MANAGER, EntityManager.class));
                latch.countDown();
            }
        });
        consumer.start();

        assertTrue(latch.await(50, TimeUnit.SECONDS));

        consumer.stop();
        Thread.sleep(1000);

        assertNotNull(receivedCustomer);
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

}

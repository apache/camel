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

import jakarta.persistence.EntityManager;

import org.apache.camel.Consumer;
import org.apache.camel.examples.Address;
import org.apache.camel.examples.Customer;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractJpaMethodSupport extends CamelTestSupport {

    protected JpaEndpoint endpoint;
    protected EntityManager entityManager;
    protected TransactionTemplate transactionTemplate;
    protected Consumer consumer;

    @AfterEach
    public void closeEntityManager() {
        if (entityManager != null) {
            entityManager.close();
        }
    }

    protected void setUp(String endpointUri) throws Exception {
        endpoint = context.getEndpoint(endpointUri, JpaEndpoint.class);

        if (endpoint.getTransactionStrategy() instanceof DefaultTransactionStrategy strategy) {
            transactionTemplate = strategy.getTransactionTemplate();
        }
        entityManager = endpoint.getEntityManagerFactory().createEntityManager();

        // use a plain resource-local transaction: em.joinTransaction() enlists with the Spring-managed
        // transaction only under OpenJPA; under Hibernate it silently begins a local transaction that
        // is never committed, so the cleanup would be lost
        entityManager.getTransaction().begin();
        entityManager.createQuery("delete from " + Customer.class.getName()).executeUpdate();
        // bulk delete does not cascade, so remove the orphaned addresses explicitly
        entityManager.createQuery("delete from " + Address.class.getName()).executeUpdate();
        entityManager.getTransaction().commit();

        assertEntitiesInDatabase(0, Customer.class.getName());
        assertEntitiesInDatabase(0, Address.class.getName());
    }

    protected void save(final Object persistable) {
        entityManager.getTransaction().begin();
        entityManager.persist(persistable);
        entityManager.flush();
        entityManager.getTransaction().commit();
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

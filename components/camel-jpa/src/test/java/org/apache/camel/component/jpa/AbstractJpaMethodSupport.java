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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractJpaMethodSupport extends CamelTestSupport {

    protected JpaEndpoint endpoint;
    protected EntityManager entityManager;
    protected TransactionTemplate transactionTemplate;
    protected Consumer consumer;

    @Override
    @AfterEach
    public void tearDown() {
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

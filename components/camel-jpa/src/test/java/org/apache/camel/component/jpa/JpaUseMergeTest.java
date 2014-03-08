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

import org.apache.camel.examples.Address;
import org.apache.camel.examples.Customer;
import org.junit.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

/**
 * @version 
 */
public class JpaUseMergeTest extends AbstractJpaMethodTest {
    
    public boolean usePersist() {
        return false;
    }

    @Test
    public void produceExistingEntity() throws Exception {
        setUp("jpa://" + Customer.class.getName() + "?usePersist=false");
        
        final Customer customer = createDefaultCustomer();
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                entityManager.joinTransaction();
                entityManager.persist(customer);
                entityManager.flush();
                return null;
            }
        });
        
        assertEntitiesInDatabase(1, Customer.class.getName());
        assertEntitiesInDatabase(1, Address.class.getName());

        // do detach the persisted entity first before modifying it as we intend to merge it later on below
        entityManager.detach(customer);
        customer.setName("Max Mustermann");
        customer.getAddress().setAddressLine1("Musterstr. 1");
        customer.getAddress().setAddressLine2("11111 Enterhausen");
        
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
}
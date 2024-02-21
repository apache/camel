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

import java.security.SecureRandom;

import jakarta.persistence.PersistenceException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.examples.Order;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JpaUsePersistTest extends AbstractJpaMethodTest {

    @Override
    public boolean usePersist() {
        return true;
    }

    @Test
    public void produceExistingEntityShouldThrowAnException() throws Exception {
        setUp("jpa://" + Order.class.getName() + "?usePersist=true");

        long id = new SecureRandom().nextLong();
        Order order2 = new Order();
        order2.setId(id);
        order2.setProductName("Beer");
        order2.setProductSku("12345");
        order2.setQuantity(5);
        save(order2);

        // we cannot store the 2nd order as its using the same id as the 1st
        Order order = new Order();
        order.setId(id);
        order.setProductName("Cheese");
        order.setProductSku("54321");
        order.setQuantity(2);
        assertIsInstanceOf(PersistenceException.class, assertThrows(CamelExecutionException.class,
                () -> template.requestBody(endpoint, order)).getCause());

        assertEntitiesInDatabase(1, Order.class.getName());
    }

    @Override
    protected void setUp(String endpointUri) throws Exception {
        super.setUp(endpointUri);
        transactionTemplate.execute(new TransactionCallback<Object>() {
            public Object doInTransaction(TransactionStatus status) {
                entityManager.joinTransaction();
                entityManager.createQuery("delete from " + Order.class.getName()).executeUpdate();
                return null;
            }
        });
    }

}

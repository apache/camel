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

import javax.persistence.PersistenceException;

import org.apache.camel.examples.Order;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @version 
 */
public class JpaUsePersistTest extends AbstractJpaMethodTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    public boolean usePersist() {
        return true;
    }
    
    @Test
    public void produceExistingEntityShouldThrowAnException() throws Exception {
        setUp("jpa://" + Order.class.getName() + "?usePersist=true");
        
        Order order = createOrder();
        save(order);

        // and adjust some values
        order = createOrder();
        order.setProductName("Cheese");
        order.setProductSku("54321");
        order.setQuantity(2);

        // we cannot store the 2nd order as its using the same id as the 1st
        expectedException.expectCause(CoreMatchers.instanceOf(PersistenceException.class));
        template.requestBody(endpoint, order);

        assertEntitiesInDatabase(1, Order.class.getName());
    }

    private Order createOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setProductName("Beer");
        order.setProductSku("12345");
        order.setQuantity(5);
        return order;
    }
}
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

import org.apache.camel.examples.Address;
import org.apache.camel.examples.Customer;
import org.junit.Test;

/**
 * @version 
 */
public class JpaUsePersistTest extends AbstractJpaMethodTest {
    
    public boolean usePersist() {
        return true;
    }
    
    @Test
    public void produceExistingEntityShouldThrowAnException() throws Exception {
        setUp("jpa://" + Customer.class.getName() + "?usePersist=true");
        
        Customer customer = createDefaultCustomer();
        save(customer);
        long id = customer.getId();

        // and adjust some values
        customer = createDefaultCustomer();
        customer.setId(id);
        customer.setName("Max Mustermann");
        customer.getAddress().setAddressLine1("Musterstr. 1");
        customer.getAddress().setAddressLine2("11111 Enterhausen");

        try {
            // we cannot store the 2nd customer as its using the same id as the 1st
            template.requestBody(endpoint, customer);
            fail("Should throw exception");
        } catch (Exception e) {
            // expected
        }
        
        assertEntitiesInDatabase(1, Customer.class.getName());
        assertEntitiesInDatabase(1, Address.class.getName());
    }
}
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
package org.apache.camel.component.resteasy.test.beans;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class CustomerList {
    static Set<Customer> customerList = new HashSet<>();

    public void addCustomer(Customer customer) {
        customerList.add(customer);
    }

    public Customer getCustomer(Integer id) {
        for (Customer c : customerList) {
            if (Objects.equals(c.getId(), id)) {
                return c;
            }
        }

        return null;
    }

    public Customer deleteCustomer(Integer id) {
        Customer delete = getCustomer(id);
        Customer customer = new Customer(delete.getName(), delete.getSurname(), delete.getId());
        customerList.remove(getCustomer(id));
        return customer;
    }

    public void add() {
        customerList.add(new Customer("Roman", "Jakubco", 1));
        customerList.add(new Customer("Camel", "Rider", 2));
    }

    public Set<Customer> getCustomerList() {
        return customerList;
    }
}

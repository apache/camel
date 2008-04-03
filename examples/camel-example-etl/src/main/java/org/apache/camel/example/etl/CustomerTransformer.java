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
package org.apache.camel.example.etl;

import java.util.List;

import org.apache.camel.Converter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.orm.jpa.JpaTemplate;

/**
 * A Message Transformer of an XML document to a Customer entity bean
 * 
 * @version $Revision$
 */
// START SNIPPET: example
@Converter
public class CustomerTransformer {
    private static final transient Log LOG = LogFactory.getLog(CustomerTransformer.class);
    private JpaTemplate template;

    public CustomerTransformer(JpaTemplate template) {
        this.template = template;
    }

    /**
     * A transformation method to convert a person document into a customer
     * entity
     */
    @Converter
    public CustomerEntity toCustomer(PersonDocument doc) {
        String user = doc.getUser();
        CustomerEntity customer = findCustomerByName(user);

        // let's convert information from the document into the entity bean

        customer.setFirstName(doc.getFirstName());
        customer.setSurname(doc.getLastName());
        customer.setCity(doc.getCity());

        LOG.debug("Created customer: " + customer);
        return customer;
    }

    /**
     * Finds a customer for the given username, or creates and inserts a new one
     */
    protected CustomerEntity findCustomerByName(String user) {
        List<CustomerEntity> list = template.find("select x from " + CustomerEntity.class.getName() + " x where x.userName = ?1", user);
        if (list.isEmpty()) {
            CustomerEntity answer = new CustomerEntity();
            answer.setUserName(user);
            template.persist(answer);
            return answer;
        } else {
            return list.get(0);
        }
    }
}
// END SNIPPET: example

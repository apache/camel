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

import javax.persistence.EntityManager;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.jpa.JpaConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A Message Transformer of an XML document to a Customer entity bean
 * 
 * @version 
 */
// START SNIPPET: example
@Converter
public final class CustomerTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerTransformer.class);

    private CustomerTransformer() {
    }

    /**
     * A transformation method to convert a person document into a customer
     * entity
     */
    @Converter
    public static CustomerEntity toCustomer(PersonDocument doc, Exchange exchange) throws Exception {
        EntityManager entityManager = exchange.getProperty(JpaConstants.ENTITY_MANAGER, EntityManager.class);
        TransactionTemplate transactionTemplate = exchange.getContext().getRegistry().lookupByNameAndType("transactionTemplate", TransactionTemplate.class);

        String user = doc.getUser();
        CustomerEntity customer = findCustomerByName(transactionTemplate, entityManager, user);

        // let's convert information from the document into the entity bean
        customer.setUserName(user);
        customer.setFirstName(doc.getFirstName());
        customer.setSurname(doc.getLastName());
        customer.setCity(doc.getCity());

        LOG.info("Created object customer: {}", customer);
        return customer;
    }

    /**
     * Finds a customer for the given username
     */
    private static CustomerEntity findCustomerByName(TransactionTemplate transactionTemplate, final EntityManager entityManager, final String userName) throws Exception {
        return transactionTemplate.execute(new TransactionCallback<CustomerEntity>() {
            public CustomerEntity doInTransaction(TransactionStatus status) {
                entityManager.joinTransaction();
                List<CustomerEntity> list = entityManager.createNamedQuery("findCustomerByUsername", CustomerEntity.class).setParameter("userName", userName).getResultList();
                CustomerEntity answer;
                if (list.isEmpty()) {
                    answer = new CustomerEntity();
                    answer.setUserName(userName);
                    LOG.info("Created a new CustomerEntity {} as no matching persisted entity found.", answer);
                } else {
                    answer = list.get(0);
                    LOG.info("Found a matching CustomerEntity {} having the userName {}.", answer, userName);
                }

                return answer;
            }
        });
    }

}
// END SNIPPET: example

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
package org.apache.camel.processor.idempotent.jpa;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @version 
 */
@ManagedResource(description = "JPA based message id repository")
public class JpaMessageIdRepository extends ServiceSupport implements IdempotentRepository<String> {
    protected static final String QUERY_STRING = "select x from " + MessageProcessed.class.getName() + " x where x.processorName = ?1 and x.messageId = ?2";
    private final String processorName;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    public JpaMessageIdRepository(EntityManagerFactory entityManagerFactory, String processorName) {
        this(entityManagerFactory, createTransactionTemplate(entityManagerFactory), processorName);
    }

    public JpaMessageIdRepository(EntityManagerFactory entityManagerFactory, TransactionTemplate transactionTemplate, String processorName) {
        this.entityManager = entityManagerFactory.createEntityManager();
        this.processorName = processorName;
        this.transactionTemplate = transactionTemplate;
    }

    public static JpaMessageIdRepository jpaMessageIdRepository(String persistenceUnit, String processorName) {
        return jpaMessageIdRepository(Persistence.createEntityManagerFactory(persistenceUnit), processorName);
    }

    public static JpaMessageIdRepository jpaMessageIdRepository(EntityManagerFactory entityManagerFactory, String processorName) {
        return new JpaMessageIdRepository(entityManagerFactory, processorName);
    }

    private static TransactionTemplate createTransactionTemplate(EntityManagerFactory entityManagerFactory) {
        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new JpaTransactionManager(entityManagerFactory));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return transactionTemplate;
    }

    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(final String messageId) {
        // Run this in single transaction.
        Boolean rc = transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus arg0) {
                entityManager.joinTransaction();

                List<?> list = query(messageId);
                if (list.isEmpty()) {
                    MessageProcessed processed = new MessageProcessed();
                    processed.setProcessorName(processorName);
                    processed.setMessageId(messageId);
                    processed.setCreatedAt(new Date());
                    entityManager.persist(processed);
                    entityManager.flush();
                    return Boolean.TRUE;
                } else {
                    return Boolean.FALSE;
                }
            }
        });
        return rc.booleanValue();
    }

    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(final String messageId) {
        // Run this in single transaction.
        Boolean rc = transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus arg0) {
                entityManager.joinTransaction();

                List<?> list = query(messageId);
                if (list.isEmpty()) {
                    return Boolean.FALSE;
                } else {
                    return Boolean.TRUE;
                }
            }
        });
        return rc.booleanValue();
    }

    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(final String messageId) {
        Boolean rc = transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus arg0) {
                entityManager.joinTransaction();

                List<?> list = query(messageId);
                if (list.isEmpty()) {
                    return Boolean.FALSE;
                } else {
                    MessageProcessed processed = (MessageProcessed)list.get(0);
                    entityManager.remove(processed);
                    entityManager.flush();
                    return Boolean.TRUE;
                }
            }
        });
        return rc.booleanValue();
    }
    
    private List<?> query(final String messageId) {
        Query query = entityManager.createQuery(QUERY_STRING);
        query.setParameter(1, processorName);
        query.setParameter(2, messageId);
        return query.getResultList();
    }

    public boolean confirm(String s) {
        // noop
        return true;
    }

    @ManagedAttribute(description = "The processor name")
    public String getProcessorName() {
        return processorName;
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
        entityManager.close();
    }
}

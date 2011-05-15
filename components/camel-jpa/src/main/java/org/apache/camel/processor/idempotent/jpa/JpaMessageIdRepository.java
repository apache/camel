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
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.IdempotentRepository;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @version 
 */
@ManagedResource("JpaMessageIdRepository")
public class JpaMessageIdRepository extends ServiceSupport implements IdempotentRepository<String> {
    protected static final String QUERY_STRING = "select x from " + MessageProcessed.class.getName() + " x where x.processorName = ?1 and x.messageId = ?2";
    private final JpaTemplate jpaTemplate;
    private final String processorName;
    private final TransactionTemplate transactionTemplate;

    public JpaMessageIdRepository(JpaTemplate template, String processorName) {
        this(template, createTransactionTemplate(template), processorName);
    }

    public JpaMessageIdRepository(JpaTemplate template, TransactionTemplate transactionTemplate, String processorName) {
        this.jpaTemplate = template;
        this.processorName = processorName;
        this.transactionTemplate = transactionTemplate;
    }

    public static JpaMessageIdRepository jpaMessageIdRepository(String persistenceUnit, String processorName) {
        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory(persistenceUnit);
        return jpaMessageIdRepository(new JpaTemplate(entityManagerFactory), processorName);
    }

    public static JpaMessageIdRepository jpaMessageIdRepository(JpaTemplate jpaTemplate, String processorName) {
        return new JpaMessageIdRepository(jpaTemplate, processorName);
    }

    private static TransactionTemplate createTransactionTemplate(JpaTemplate jpaTemplate) {
        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(new JpaTransactionManager(jpaTemplate.getEntityManagerFactory()));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return transactionTemplate;
    }

    @ManagedOperation(description = "Adds the key to the store")
    @SuppressWarnings("unchecked")
    public boolean add(final String messageId) {
        // Run this in single transaction.
        Boolean rc = (Boolean)transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus arg0) {
                List list = jpaTemplate.find(QUERY_STRING, processorName, messageId);
                if (list.isEmpty()) {
                    MessageProcessed processed = new MessageProcessed();
                    processed.setProcessorName(processorName);
                    processed.setMessageId(messageId);
                    processed.setCreatedAt(new Date());
                    jpaTemplate.persist(processed);
                    jpaTemplate.flush();
                    return Boolean.TRUE;
                } else {
                    return Boolean.FALSE;
                }
            }
        });
        return rc.booleanValue();
    }

    @ManagedOperation(description = "Does the store contain the given key")
    @SuppressWarnings("unchecked")
    public boolean contains(final String messageId) {
        // Run this in single transaction.
        Boolean rc = (Boolean)transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus arg0) {
                List list = jpaTemplate.find(QUERY_STRING, processorName, messageId);
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
    @SuppressWarnings("unchecked")
    public boolean remove(final String messageId) {
        Boolean rc = (Boolean)transactionTemplate.execute(new TransactionCallback() {
            public Object doInTransaction(TransactionStatus arg0) {
                List list = jpaTemplate.find(QUERY_STRING, processorName, messageId);
                if (list.isEmpty()) {
                    return Boolean.FALSE;
                } else {
                    MessageProcessed processoed = (MessageProcessed) list.get(0);
                    jpaTemplate.remove(processoed);
                    jpaTemplate.flush();
                    return Boolean.TRUE;
                }
            }
        });
        return rc.booleanValue();
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
    }
}

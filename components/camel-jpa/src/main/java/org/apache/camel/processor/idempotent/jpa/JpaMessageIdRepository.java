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
import java.util.Iterator;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.ExchangeIdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.apache.camel.component.jpa.JpaHelper.getTargetEntityManager;

/**
 * @version
 */
@ManagedResource(description = "JPA based message id repository")
public class JpaMessageIdRepository extends ServiceSupport implements ExchangeIdempotentRepository<String> {
    protected static final String QUERY_STRING = "select x from " + MessageProcessed.class.getName() + " x where x.processorName = ?1 and x.messageId = ?2";
    protected static final String QUERY_CLEAR_STRING = "select x from " + MessageProcessed.class.getName() + " x where x.processorName = ?1";
    private static final Logger LOG = LoggerFactory.getLogger(JpaMessageIdRepository.class);
    private final String processorName;
    private final EntityManagerFactory entityManagerFactory;
    private final TransactionTemplate transactionTemplate;
    private boolean joinTransaction = true;
    private boolean sharedEntityManager;

    public JpaMessageIdRepository(EntityManagerFactory entityManagerFactory, String processorName) {
        this(entityManagerFactory, createTransactionTemplate(entityManagerFactory), processorName);
    }

    public JpaMessageIdRepository(EntityManagerFactory entityManagerFactory, TransactionTemplate transactionTemplate, String processorName) {
        this.entityManagerFactory = entityManagerFactory;
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
    public boolean add(String messageId) {
        return add(null, messageId);
    }

    @Override
    public boolean add(final Exchange exchange, final String messageId) {
        final EntityManager entityManager = getTargetEntityManager(exchange, entityManagerFactory, true, sharedEntityManager, true);
        // Run this in single transaction.
        Boolean rc = transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus status) {
                if (isJoinTransaction()) {
                    entityManager.joinTransaction();
                }

                try {
                    List<?> list = query(entityManager, messageId);
                    if (list.isEmpty()) {
                        MessageProcessed processed = new MessageProcessed();
                        processed.setProcessorName(processorName);
                        processed.setMessageId(messageId);
                        processed.setCreatedAt(new Date());
                        entityManager.persist(processed);
                        entityManager.flush();
                        entityManager.close();
                        return Boolean.TRUE;
                    } else {
                        return Boolean.FALSE;
                    }
                } catch (Exception ex) {
                    LOG.error("Something went wrong trying to add message to repository {}", ex);
                    throw new PersistenceException(ex);
                } finally {
                    try {
                        if (entityManager.isOpen()) {
                            entityManager.close();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        });

        LOG.debug("add {} -> {}", messageId, rc);
        return rc;
    }

    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String messageId) {
        return contains(null, messageId);
    }

    @Override
    public boolean contains(final Exchange exchange, final String messageId) {
        final EntityManager entityManager = getTargetEntityManager(exchange, entityManagerFactory, true, sharedEntityManager, true);

        // Run this in single transaction.
        Boolean rc = transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus status) {
                if (isJoinTransaction()) {
                    entityManager.joinTransaction();
                }

                List<?> list = query(entityManager, messageId);
                if (list.isEmpty()) {
                    return Boolean.FALSE;
                } else {
                    return Boolean.TRUE;
                }
            }
        });

        LOG.debug("contains {} -> {}", messageId, rc);
        return rc;
    }

    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String messageId) {
        return remove(null, messageId);
    }

    @Override
    public boolean remove(final Exchange exchange, final String messageId) {
        final EntityManager entityManager = getTargetEntityManager(exchange, entityManagerFactory, true, sharedEntityManager, true);

        Boolean rc = transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus status) {
                if (isJoinTransaction()) {
                    entityManager.joinTransaction();
                }
                try {
                    List<?> list = query(entityManager, messageId);
                    if (list.isEmpty()) {
                        return Boolean.FALSE;
                    } else {
                        MessageProcessed processed = (MessageProcessed) list.get(0);
                        entityManager.remove(processed);
                        entityManager.flush();
                        entityManager.close();
                        return Boolean.TRUE;
                    }
                } catch (Exception ex) {
                    LOG.error("Something went wrong trying to remove message to repository {}", ex);
                    throw new PersistenceException(ex);
                } finally {
                    try {
                        if (entityManager.isOpen()) {
                            entityManager.close();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        });

        LOG.debug("remove {}", messageId);
        return rc;
    }

    @Override
    public boolean confirm(String messageId) {
        return confirm(null, messageId);
    }

    @Override
    public boolean confirm(final Exchange exchange, String messageId) {
        LOG.debug("confirm {} -> true", messageId);
        return true;
    }

    @ManagedOperation(description = "Clear the store")
    public void clear() {
        final EntityManager entityManager = getTargetEntityManager(null, entityManagerFactory, true, sharedEntityManager, true);

        Boolean rc = transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus status) {
                if (isJoinTransaction()) {
                    entityManager.joinTransaction();
                }
                try {
                    List<?> list = queryClear(entityManager);
                    if (!list.isEmpty()) {
                        Iterator it = list.iterator();
                        while (it.hasNext()) {
                            Object item = it.next();
                            entityManager.remove(item);
                        }
                        entityManager.flush();
                        entityManager.close();
                    }
                    return Boolean.TRUE;
                } catch (Exception ex) {
                    LOG.error("Something went wrong trying to clear the repository {}", ex);
                    throw new PersistenceException(ex);
                } finally {
                    try {
                        if (entityManager.isOpen()) {
                            entityManager.close();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        });

        LOG.debug("clear the store {}", MessageProcessed.class.getName());
    }

    private List<?> query(final EntityManager entityManager, final String messageId) {
        Query query = entityManager.createQuery(QUERY_STRING);
        query.setParameter(1, processorName);
        query.setParameter(2, messageId);
        return query.getResultList();
    }

    private List<?> queryClear(final EntityManager entityManager) {
        Query query = entityManager.createQuery(QUERY_CLEAR_STRING);
        query.setParameter(1, processorName);
        return query.getResultList();
    }

    @ManagedAttribute(description = "The processor name")
    public String getProcessorName() {
        return processorName;
    }

    @ManagedAttribute(description = "Whether to join existing transaction")
    public boolean isJoinTransaction() {
        return joinTransaction;
    }

    public void setJoinTransaction(boolean joinTransaction) {
        this.joinTransaction = joinTransaction;
    }

    @ManagedAttribute(description = "Whether to use shared EntityManager")
    public boolean isSharedEntityManager() {
        return sharedEntityManager;
    }

    public void setSharedEntityManager(boolean sharedEntityManager) {
        this.sharedEntityManager = sharedEntityManager;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}

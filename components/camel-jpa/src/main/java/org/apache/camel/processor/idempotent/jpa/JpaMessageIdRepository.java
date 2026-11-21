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
package org.apache.camel.processor.idempotent.jpa;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;

import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.jpa.JpaHelper;
import org.apache.camel.component.jpa.TransactionStrategy;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jpa.JpaHelper.getTargetEntityManager;
import static org.apache.camel.component.jpa.JpaHelper.isConstraintViolation;

@ManagedResource(description = "JPA based message id repository")
public class JpaMessageIdRepository extends ServiceSupport implements IdempotentRepository {
    protected static final String QUERY_STRING
            = "select x from " + MessageProcessed.class.getName() + " x where x.processorName = ?1 and x.messageId = ?2";
    protected static final String QUERY_CLEAR_STRING
            = "select x from " + MessageProcessed.class.getName() + " x where x.processorName = ?1";

    private static final String SOMETHING_WENT_WRONG
            = "Something went wrong trying to add message to repository %s";

    private static final Logger LOG = LoggerFactory.getLogger(JpaMessageIdRepository.class);

    private final String processorName;
    private final EntityManagerFactory entityManagerFactory;
    private final TransactionStrategy transactionStrategy;
    private boolean joinTransaction = true;
    private boolean sharedEntityManager;

    public JpaMessageIdRepository(EntityManagerFactory entityManagerFactory, String processorName) {
        this(entityManagerFactory, JpaHelper.resolveTransactionStrategy(null, entityManagerFactory, false), processorName);
    }

    public JpaMessageIdRepository(EntityManagerFactory entityManagerFactory, TransactionStrategy transactionStrategy,
                                  String processorName) {
        this.entityManagerFactory = entityManagerFactory;
        this.processorName = processorName;
        this.transactionStrategy = transactionStrategy;
    }

    public static JpaMessageIdRepository jpaMessageIdRepository(String persistenceUnit, String processorName) {
        return jpaMessageIdRepository(Persistence.createEntityManagerFactory(persistenceUnit), processorName);
    }

    public static JpaMessageIdRepository jpaMessageIdRepository(
            EntityManagerFactory entityManagerFactory, String processorName) {
        return new JpaMessageIdRepository(entityManagerFactory, processorName);
    }

    @Override
    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(String messageId) {
        return add(null, messageId);
    }

    /**
     * Adds the message id if it is not in the repository. When the insert fails with a constraint violation, which
     * happens when another caller (thread, node) inserts the same message id at the same time, the message id is read
     * again with a new EntityManager in a new transaction: add returns false if it is there, and throws otherwise (for
     * example after a primary key collision, when the id sequence is behind the table). In a transaction of the caller,
     * the message id is read again in that transaction (with the same EntityManager when it is shared), which the
     * failed insert has marked for rollback: add may then throw, and the caller's transaction fails.
     */
    @Override
    public boolean add(final Exchange exchange, final String messageId) {
        final EntityManager entityManager
                = getTargetEntityManager(exchange, entityManagerFactory, false, sharedEntityManager, true);
        final Boolean[] rc = new Boolean[1];
        final Exception[] violation = new Exception[1];
        // what add throws when the message id is not stored after a constraint violation
        RuntimeException failure = null;
        try {
            doAdd(entityManager, messageId, rc, violation);
        } catch (RuntimeException e) {
            // the transaction could not commit after the insert failed on a constraint (the failed flush marked it for
            // rollback, and the commit failure, such as an UnexpectedRollbackException, may not say why), or the
            // constraint violation came at commit
            if (violation[0] == null && !isConstraintViolation(e)) {
                throw e;
            }
            failure = e;
        }

        if (violation[0] != null) {
            String contextInfo = String.format(SOMETHING_WENT_WRONG, violation[0].getMessage());
            PersistenceException wrapped = new PersistenceException(contextInfo, violation[0]);
            if (failure != null) {
                wrapped.addSuppressed(failure);
            }
            failure = wrapped;
        }
        if (failure != null) {
            // the SQL state class 23 covers every integrity violation, not only the unique key on processorName and
            // messageId. When the database reports the violation of that key, the other caller's insert has committed,
            // so a new transaction sees its row
            boolean stored;
            try {
                stored = isStored(messageId);
            } catch (RuntimeException e) {
                e.addSuppressed(failure);
                throw e;
            }
            if (!stored) {
                throw failure;
            }
            LOG.debug("Duplicate message detected during concurrent insert: {}", messageId);
            rc[0] = Boolean.FALSE;
        }

        LOG.debug("add {} -> {}", messageId, rc[0]);
        return rc[0];
    }

    private void doAdd(EntityManager entityManager, String messageId, Boolean[] rc, Exception[] violation) {
        transactionStrategy.executeInTransaction(() -> {
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
                    rc[0] = Boolean.TRUE;
                } else {
                    rc[0] = Boolean.FALSE;
                }
            } catch (Exception ex) {
                if (isConstraintViolation(ex)) {
                    // possibly a concurrent insert of the same messageId: add checks it in a new transaction
                    violation[0] = ex;
                } else {
                    String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                    throw new PersistenceException(contextInfo, ex);
                }
            } finally {
                closeEntityManager(entityManager);
            }
        });
    }

    /**
     * Whether the message id is in the repository.
     * <p>
     * Without a caller's transaction, it is read in a new transaction, after the transaction of the insert ended.
     * <p>
     * In a caller's transaction, it is read in that transaction: with the EntityManager of that transaction when the
     * EntityManager is shared, otherwise with a new EntityManager.
     */
    private boolean isStored(String messageId) {
        final EntityManager entityManager
                = getTargetEntityManager(null, entityManagerFactory, false, sharedEntityManager, true);
        final boolean[] rc = new boolean[1];
        transactionStrategy.executeInTransaction(() -> {
            if (isJoinTransaction()) {
                entityManager.joinTransaction();
            }
            try {
                rc[0] = !query(entityManager, messageId).isEmpty();
            } catch (Exception ex) {
                String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                throw new PersistenceException(contextInfo, ex);
            } finally {
                closeEntityManager(entityManager);
            }
        });
        return rc[0];
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String messageId) {
        return contains(null, messageId);
    }

    @Override
    public boolean contains(final Exchange exchange, final String messageId) {
        final EntityManager entityManager
                = getTargetEntityManager(exchange, entityManagerFactory, false, sharedEntityManager, true);

        // Run this in single transaction.
        final Boolean[] rc = new Boolean[1];
        transactionStrategy.executeInTransaction(() -> {
            if (isJoinTransaction()) {
                entityManager.joinTransaction();
            }
            try {
                List<?> list = query(entityManager, messageId);
                if (list.isEmpty()) {
                    rc[0] = Boolean.FALSE;
                } else {
                    rc[0] = Boolean.TRUE;
                }
            } catch (Exception ex) {
                String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                throw new PersistenceException(contextInfo, ex);
            } finally {
                try {
                    if (entityManager.isOpen()) {
                        entityManager.close();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        });

        LOG.debug("contains {} -> {}", messageId, rc[0]);
        return rc[0];
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String messageId) {
        return remove(null, messageId);
    }

    @Override
    public boolean remove(final Exchange exchange, final String messageId) {
        final EntityManager entityManager
                = getTargetEntityManager(exchange, entityManagerFactory, false, sharedEntityManager, true);

        Boolean[] rc = new Boolean[1];
        transactionStrategy.executeInTransaction(() -> {
            if (isJoinTransaction()) {
                entityManager.joinTransaction();
            }
            try {
                List<?> list = query(entityManager, messageId);
                if (list.isEmpty()) {
                    rc[0] = Boolean.FALSE;
                } else {
                    MessageProcessed processed = (MessageProcessed) list.get(0);
                    entityManager.remove(processed);
                    entityManager.flush();
                    entityManager.close();
                    rc[0] = Boolean.TRUE;
                }
            } catch (Exception ex) {
                String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                throw new PersistenceException(contextInfo, ex);
            } finally {
                try {
                    if (entityManager.isOpen()) {
                        entityManager.close();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        });

        LOG.debug("remove {}", messageId);
        return rc[0];
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

    @Override
    @ManagedOperation(description = "Clear the store")
    public void clear() {
        final EntityManager entityManager
                = getTargetEntityManager(null, entityManagerFactory, false, sharedEntityManager, true);

        transactionStrategy.executeInTransaction(() -> {
            if (isJoinTransaction()) {
                entityManager.joinTransaction();
            }
            try {
                List<?> list = queryClear(entityManager);
                if (!list.isEmpty()) {
                    Iterator<?> it = list.iterator();
                    while (it.hasNext()) {
                        Object item = it.next();
                        entityManager.remove(item);
                    }
                    entityManager.flush();
                    entityManager.close();
                }
            } catch (Exception ex) {
                String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                throw new PersistenceException(contextInfo, ex);
            } finally {
                try {
                    if (entityManager.isOpen()) {
                        entityManager.close();
                    }
                } catch (Exception e) {
                    // ignore
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

    private static void closeEntityManager(EntityManager entityManager) {
        try {
            if (entityManager.isOpen()) {
                entityManager.close();
            }
        } catch (Exception e) {
            // ignore
        }
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

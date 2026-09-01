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
package org.apache.camel.processor.keyvalue.jpa;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.Query;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.jpa.DefaultTransactionStrategy;
import org.apache.camel.component.jpa.TransactionStrategy;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jpa.JpaHelper.getTargetEntityManager;

/**
 * A JPA-based {@link KeyValueRepository} that stores entries as {@link KeyValueEntry} entities.
 * <p/>
 * Values are serialized to byte arrays using Java Object Serialization and stored in a {@code CAMEL_KEYVALUE} table.
 * Expired entries are cleaned up lazily on access.
 * <p/>
 * This implementation follows the same transaction and entity-manager patterns used by
 * {@link org.apache.camel.processor.idempotent.jpa.JpaMessageIdRepository}.
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "A JPA-based KeyValueRepository that stores entries using JPA entities.",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "JPA based key-value repository")
public class JpaKeyValueRepository extends ServiceSupport implements KeyValueRepository {

    private static final String QUERY_BY_KEY
            = "select e from " + KeyValueEntry.class.getName() + " e where e.itemKey = ?1";
    private static final String QUERY_ALL_KEYS
            = "select e.itemKey from " + KeyValueEntry.class.getName() + " e where e.expiresAt = 0 or e.expiresAt > ?1";
    private static final String QUERY_ALL
            = "select e from " + KeyValueEntry.class.getName() + " e";
    private static final String QUERY_COUNT_VALID
            = "select count(e) from " + KeyValueEntry.class.getName() + " e where e.expiresAt = 0 or e.expiresAt > ?1";

    private static final String SOMETHING_WENT_WRONG
            = "Something went wrong in JpaKeyValueRepository: %s";

    private static final Logger LOG = LoggerFactory.getLogger(JpaKeyValueRepository.class);

    @Metadata(description = "The JPA EntityManagerFactory to use", required = true)
    private EntityManagerFactory entityManagerFactory;
    @Metadata(description = "The TransactionStrategy to use for transactional operations")
    private TransactionStrategy transactionStrategy;
    @Metadata(description = "Whether to join an existing transaction", defaultValue = "true")
    private boolean joinTransaction = true;
    @Metadata(description = "Whether to use a shared EntityManager", defaultValue = "false")
    private boolean sharedEntityManager;

    /**
     * Creates a new JPA key-value repository. The {@link #setEntityManagerFactory(EntityManagerFactory)} must be called
     * before starting.
     */
    public JpaKeyValueRepository() {
    }

    /**
     * Creates a new JPA key-value repository with the given entity manager factory.
     *
     * @param entityManagerFactory the JPA entity manager factory to use
     */
    public JpaKeyValueRepository(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    // ---- KeyValueRepository implementation ----

    @Override
    @ManagedOperation(description = "Get value by key")
    public Object get(String key) {
        final Object[] rc = new Object[1];
        final EntityManager entityManager
                = getTargetEntityManager(null, entityManagerFactory, false, sharedEntityManager, true);

        transactionStrategy.executeInTransaction(() -> {
            if (joinTransaction) {
                entityManager.joinTransaction();
            }
            try {
                KeyValueEntry entry = findByKey(entityManager, key);
                if (entry == null) {
                    rc[0] = null;
                } else if (entry.isExpired()) {
                    entityManager.remove(entry);
                    entityManager.flush();
                    rc[0] = null;
                } else {
                    rc[0] = deserialize(entry.getItemValue());
                }
            } catch (Exception ex) {
                String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                throw new PersistenceException(contextInfo, ex);
            } finally {
                closeEntityManager(entityManager);
            }
        });

        LOG.debug("get {} -> {}", key, rc[0] != null ? "found" : "null");
        return rc[0];
    }

    @Override
    @ManagedOperation(description = "Put a key-value pair with optional TTL")
    public Object put(String key, Object value, long ttlMillis) {
        final Object[] rc = new Object[1];
        final EntityManager entityManager
                = getTargetEntityManager(null, entityManagerFactory, false, sharedEntityManager, true);

        transactionStrategy.executeInTransaction(() -> {
            if (joinTransaction) {
                entityManager.joinTransaction();
            }
            try {
                long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : 0;
                byte[] serializedValue = serialize(value);

                KeyValueEntry entry = findByKey(entityManager, key);
                if (entry != null) {
                    if (!entry.isExpired()) {
                        rc[0] = deserialize(entry.getItemValue());
                    }
                    entry.setItemValue(serializedValue);
                    entry.setExpiresAt(expiresAt);
                    entityManager.merge(entry);
                } else {
                    entry = new KeyValueEntry(key, serializedValue, expiresAt);
                    entityManager.persist(entry);
                }
                entityManager.flush();
            } catch (Exception ex) {
                String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                throw new PersistenceException(contextInfo, ex);
            } finally {
                closeEntityManager(entityManager);
            }
        });

        LOG.debug("put {} -> previous={}", key, rc[0] != null ? "found" : "null");
        return rc[0];
    }

    @Override
    @ManagedOperation(description = "Delete a key")
    public Object delete(String key) {
        final Object[] rc = new Object[1];
        final EntityManager entityManager
                = getTargetEntityManager(null, entityManagerFactory, false, sharedEntityManager, true);

        transactionStrategy.executeInTransaction(() -> {
            if (joinTransaction) {
                entityManager.joinTransaction();
            }
            try {
                KeyValueEntry entry = findByKey(entityManager, key);
                if (entry == null) {
                    rc[0] = null;
                } else if (entry.isExpired()) {
                    entityManager.remove(entry);
                    entityManager.flush();
                    rc[0] = null;
                } else {
                    rc[0] = deserialize(entry.getItemValue());
                    entityManager.remove(entry);
                    entityManager.flush();
                }
            } catch (Exception ex) {
                String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                throw new PersistenceException(contextInfo, ex);
            } finally {
                closeEntityManager(entityManager);
            }
        });

        LOG.debug("delete {} -> {}", key, rc[0] != null ? "found" : "null");
        return rc[0];
    }

    @Override
    @ManagedOperation(description = "Check if key exists")
    public boolean contains(String key) {
        final Boolean[] rc = new Boolean[1];
        final EntityManager entityManager
                = getTargetEntityManager(null, entityManagerFactory, false, sharedEntityManager, true);

        transactionStrategy.executeInTransaction(() -> {
            if (joinTransaction) {
                entityManager.joinTransaction();
            }
            try {
                KeyValueEntry entry = findByKey(entityManager, key);
                if (entry == null) {
                    rc[0] = Boolean.FALSE;
                } else if (entry.isExpired()) {
                    entityManager.remove(entry);
                    entityManager.flush();
                    rc[0] = Boolean.FALSE;
                } else {
                    rc[0] = Boolean.TRUE;
                }
            } catch (Exception ex) {
                String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                throw new PersistenceException(contextInfo, ex);
            } finally {
                closeEntityManager(entityManager);
            }
        });

        LOG.debug("contains {} -> {}", key, rc[0]);
        return rc[0];
    }

    @Override
    public Set<String> keys() {
        final Set<?>[] rc = new Set<?>[1];
        final EntityManager entityManager
                = getTargetEntityManager(null, entityManagerFactory, false, sharedEntityManager, true);

        transactionStrategy.executeInTransaction(() -> {
            if (joinTransaction) {
                entityManager.joinTransaction();
            }
            try {
                Query query = entityManager.createQuery(QUERY_ALL_KEYS);
                query.setParameter(1, System.currentTimeMillis());
                @SuppressWarnings("unchecked")
                List<String> resultList = query.getResultList();
                rc[0] = Set.copyOf(resultList);
            } catch (Exception ex) {
                String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                throw new PersistenceException(contextInfo, ex);
            } finally {
                closeEntityManager(entityManager);
            }
        });

        LOG.debug("keys -> {} entries", rc[0] != null ? rc[0].size() : 0);
        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) rc[0];
        return result;
    }

    @Override
    @ManagedOperation(description = "Clear all entries")
    public void clear() {
        final EntityManager entityManager
                = getTargetEntityManager(null, entityManagerFactory, false, sharedEntityManager, true);

        transactionStrategy.executeInTransaction(() -> {
            if (joinTransaction) {
                entityManager.joinTransaction();
            }
            try {
                List<?> list = entityManager.createQuery(QUERY_ALL).getResultList();
                if (!list.isEmpty()) {
                    Iterator<?> it = list.iterator();
                    while (it.hasNext()) {
                        Object item = it.next();
                        entityManager.remove(item);
                    }
                    entityManager.flush();
                }
            } catch (Exception ex) {
                String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                throw new PersistenceException(contextInfo, ex);
            } finally {
                closeEntityManager(entityManager);
            }
        });

        LOG.debug("clear the store {}", KeyValueEntry.class.getName());
    }

    @Override
    public Object putIfAbsent(String key, Object value, long ttlMillis) {
        final Object[] rc = new Object[1];
        final EntityManager entityManager
                = getTargetEntityManager(null, entityManagerFactory, false, sharedEntityManager, true);

        transactionStrategy.executeInTransaction(() -> {
            if (joinTransaction) {
                entityManager.joinTransaction();
            }
            try {
                long expiresAt = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : 0;
                byte[] serializedValue = serialize(value);

                KeyValueEntry entry = findByKey(entityManager, key);
                if (entry != null && !entry.isExpired()) {
                    // key exists and is valid -- return existing value
                    rc[0] = deserialize(entry.getItemValue());
                } else if (entry != null) {
                    // key exists but expired -- update in place
                    entry.setItemValue(serializedValue);
                    entry.setExpiresAt(expiresAt);
                    entityManager.merge(entry);
                    entityManager.flush();
                    rc[0] = null;
                } else {
                    // no entry -- persist new one
                    entry = new KeyValueEntry(key, serializedValue, expiresAt);
                    entityManager.persist(entry);
                    entityManager.flush();
                    rc[0] = null;
                }
            } catch (Exception ex) {
                if (isConstraintViolation(ex)) {
                    // concurrent insert of the same key -- treat as "already present"
                    LOG.debug("Concurrent insert detected for key: {}", key);
                    // re-read to return the existing value
                    try {
                        KeyValueEntry existing = findByKey(entityManager, key);
                        rc[0] = existing != null ? deserialize(existing.getItemValue()) : null;
                    } catch (Exception inner) {
                        // fall through with null
                        rc[0] = null;
                    }
                } else {
                    String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                    throw new PersistenceException(contextInfo, ex);
                }
            } finally {
                closeEntityManager(entityManager);
            }
        });

        LOG.debug("putIfAbsent {} -> {}", key, rc[0] != null ? "existing" : "inserted");
        return rc[0];
    }

    @Override
    @ManagedAttribute(description = "The number of non-expired entries in the repository")
    public int size() {
        final int[] rc = new int[1];
        final EntityManager entityManager
                = getTargetEntityManager(null, entityManagerFactory, false, sharedEntityManager, true);

        transactionStrategy.executeInTransaction(() -> {
            if (joinTransaction) {
                entityManager.joinTransaction();
            }
            try {
                Query query = entityManager.createQuery(QUERY_COUNT_VALID);
                query.setParameter(1, System.currentTimeMillis());
                Long count = (Long) query.getSingleResult();
                rc[0] = count.intValue();
            } catch (Exception ex) {
                String contextInfo = String.format(SOMETHING_WENT_WRONG, ex.getMessage());
                throw new PersistenceException(contextInfo, ex);
            } finally {
                closeEntityManager(entityManager);
            }
        });

        LOG.debug("size -> {}", rc[0]);
        return rc[0];
    }

    // ---- Configuration properties ----

    public EntityManagerFactory getEntityManagerFactory() {
        return entityManagerFactory;
    }

    /**
     * Sets the JPA EntityManagerFactory to use.
     *
     * @param entityManagerFactory the entity manager factory
     */
    public void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public TransactionStrategy getTransactionStrategy() {
        return transactionStrategy;
    }

    /**
     * Sets the transaction strategy to use. If not set, a {@link DefaultTransactionStrategy} will be created
     * automatically.
     *
     * @param transactionStrategy the transaction strategy
     */
    public void setTransactionStrategy(TransactionStrategy transactionStrategy) {
        this.transactionStrategy = transactionStrategy;
    }

    @ManagedAttribute(description = "Whether to join existing transaction")
    public boolean isJoinTransaction() {
        return joinTransaction;
    }

    /**
     * Sets whether to join an existing transaction. Default is {@code true}.
     *
     * @param joinTransaction whether to join existing transactions
     */
    public void setJoinTransaction(boolean joinTransaction) {
        this.joinTransaction = joinTransaction;
    }

    @ManagedAttribute(description = "Whether to use shared EntityManager")
    public boolean isSharedEntityManager() {
        return sharedEntityManager;
    }

    /**
     * Sets whether to use a shared EntityManager. Default is {@code false}.
     *
     * @param sharedEntityManager whether to use a shared EntityManager
     */
    public void setSharedEntityManager(boolean sharedEntityManager) {
        this.sharedEntityManager = sharedEntityManager;
    }

    // ---- Lifecycle ----

    @Override
    protected void doInit() throws Exception {
        if (transactionStrategy == null) {
            transactionStrategy = new DefaultTransactionStrategy(null, entityManagerFactory);
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

    // ---- Private helpers ----

    private KeyValueEntry findByKey(EntityManager entityManager, String key) {
        Query query = entityManager.createQuery(QUERY_BY_KEY);
        query.setParameter(1, key);
        List<?> list = query.getResultList();
        if (list.isEmpty()) {
            return null;
        }
        return (KeyValueEntry) list.get(0);
    }

    private static byte[] serialize(Object value) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeCamelException("Failed to serialize value", e);
        }
    }

    private static Object deserialize(byte[] data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeCamelException("Failed to deserialize value", e);
        }
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

    private static boolean isConstraintViolation(Exception ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof java.sql.SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (cause instanceof jakarta.persistence.EntityExistsException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}

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
package org.apache.camel.processor.idempotent.jdbc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caching version of {@link JdbcMessageIdRepository}
 */
public class JdbcCachedMessageIdRepository extends JdbcMessageIdRepository {

    protected static final String DEFAULT_QUERY_ALL_STRING
            = "SELECT messageId, COUNT(*) FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? GROUP BY messageId";

    private volatile Map<String, Integer> cache = new ConcurrentHashMap<>();
    private final AtomicInteger hitCount = new AtomicInteger();
    private final AtomicInteger missCount = new AtomicInteger();
    private String queryAllString = DEFAULT_QUERY_ALL_STRING;

    public JdbcCachedMessageIdRepository() {
    }

    public JdbcCachedMessageIdRepository(DataSource dataSource, String processorName) {
        super(dataSource, processorName);
    }

    public JdbcCachedMessageIdRepository(DataSource dataSource, TransactionTemplate transactionTemplate, String processorName) {
        super(dataSource, transactionTemplate, processorName);
    }

    public JdbcCachedMessageIdRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        super(jdbcTemplate, transactionTemplate);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (getTableName() != null) {
            // update query string from default table name to the new table name
            queryAllString = DEFAULT_QUERY_ALL_STRING.replace(DEFAULT_TABLENAME, getTableName());
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        reload();
    }

    @Override
    public boolean add(final String key) {
        Integer previousValue = cache.getOrDefault(key, 0);
        if (previousValue != 0) {
            cache.merge(key, 1, Integer::sum);
            hitCount.incrementAndGet();
            return false;
        }
        missCount.incrementAndGet();
        boolean added = super.add(key);
        // only remember the key after the database insert succeeded, otherwise a failed insert
        // would leave the key cached and every redelivery would be rejected as a duplicate
        cacheAfterCommit(key);
        return added;
    }

    @Override
    public boolean contains(final String key) {
        Integer previousValue = cache.getOrDefault(key, 0);
        if (previousValue != 0) {
            hitCount.incrementAndGet();
            return true;
        }
        missCount.incrementAndGet();
        return super.contains(key);
    }

    @Override
    public boolean remove(String key) {
        cache.remove(key);
        cancelCacheAfterCommit(key);
        boolean removed = super.remove(key);
        // and again after the delete: an add of the key on another thread whose transaction committed while this delete
        // waited for its row lock may have cached it meanwhile. This narrows the window in which the cache keeps a
        // deleted key; it does not close it, as that add's afterCommit can still run after this line.
        cache.remove(key);
        // in a transaction of the caller the row is deleted when it commits: until then another thread still sees the
        // row and caches the key (its add is a duplicate), so the key is removed from the cache again after the commit
        if (removed) {
            evictAfterCommit(key);
        }
        return removed;
    }

    @Override
    public void clear() {
        cache.clear();
        cancelCacheAfterCommit(null);
        hitCount.set(0);
        missCount.set(0);
        super.clear();
        // and again after the delete, and after the commit of a transaction of the caller, as in remove
        cache.clear();
        evictAfterCommit(null);
    }

    /**
     * Caches the key once the transaction that inserted its row commits. In a transaction of the caller that rolls back
     * (for example a transacted route), the row is gone, and a cached key would make the redelivery of the message a
     * duplicate. Without a transaction with synchronization the key is cached at once.
     */
    private void cacheAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new CacheAfterCommit(this, key));
        } else {
            cache.merge(key, 1, Integer::sum);
        }
    }

    /**
     * A remove of the key (or a clear when the key is null) later in the same transaction cancels its caching after the
     * commit.
     */
    private void cancelCacheAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                if (synchronization instanceof CacheAfterCommit pending && pending.repository == this
                        && (key == null || pending.key.equals(key))) {
                    pending.cancelled = true;
                }
            }
        }
    }

    /**
     * Removes the key (or every key when the key is null) from the cache again once the transaction of the caller that
     * deleted its row commits. Without a transaction with synchronization the delete has committed already. When the
     * transaction of the caller is of another transaction manager (another DataSource, JMS), the delete committed in a
     * transaction of its own already, and this only costs a cache miss.
     */
    private void evictAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new EvictAfterCommit(this, key));
        }
    }

    private static final class EvictAfterCommit implements TransactionSynchronization {
        private final JdbcCachedMessageIdRepository repository;
        private final String key;

        private EvictAfterCommit(JdbcCachedMessageIdRepository repository, String key) {
            this.repository = repository;
            this.key = key;
        }

        @Override
        public void afterCommit() {
            if (key == null) {
                repository.cache.clear();
            } else {
                repository.cache.remove(key);
            }
        }
    }

    private static final class CacheAfterCommit implements TransactionSynchronization {
        private final JdbcCachedMessageIdRepository repository;
        private final String key;
        // set and read by the thread of the transaction
        private boolean cancelled;

        private CacheAfterCommit(JdbcCachedMessageIdRepository repository, String key) {
            this.repository = repository;
            this.key = key;
        }

        @Override
        public void afterCommit() {
            if (!cancelled) {
                repository.cache.merge(key, 1, Integer::sum);
            }
        }
    }

    public String getQueryAllString() {
        return queryAllString;
    }

    public void setQueryAllString(String queryAllString) {
        this.queryAllString = queryAllString;
    }

    public int getHitCount() {
        return hitCount.get();
    }

    public int getMissCount() {
        return missCount.get();
    }

    public void reload() {
        transactionTemplate.execute(status -> {
            try {
                cache = jdbcTemplate.query(getQueryAllString(), resultSet -> {
                    Map<String, Integer> messageIdCount = new ConcurrentHashMap<>();
                    while (resultSet.next()) {
                        messageIdCount.put(resultSet.getString(1), resultSet.getInt(2));
                    }
                    return messageIdCount;
                }, getProcessorName());
                log.info("JdbcCachedMessageIdRepository cache loaded with {} entries", cache.size());
            } catch (DataAccessException dae) {
                log.error(
                        "Unable to populate JdbcCachedMessageIdRepository cache because of: {}.",
                        dae.getMessage());
                throw dae;
            }
            return Boolean.TRUE;

        });
    }

}

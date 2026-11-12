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
import java.util.concurrent.atomic.AtomicLong;

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
    // bumped by every eviction after a delete, before the key leaves the cache: an add that read the row before the
    // delete committed sees the change after caching the key, and removes it again
    private final AtomicLong evictions = new AtomicLong();
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
        // one atomic step: a key evicted meanwhile by a remove on another thread is not put back
        if (cache.computeIfPresent(key, (k, count) -> count + 1) != null) {
            hitCount.incrementAndGet();
            return false;
        }
        missCount.incrementAndGet();
        // read before the row is: a remove or clear that deletes it after this read changes the counter
        long seen = evictions.get();
        boolean added = super.add(key);
        // only remember the key after the database insert succeeded, otherwise a failed insert
        // would leave the key cached and every redelivery would be rejected as a duplicate
        cacheAfterCommit(key, seen);
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
        if (removed) {
            // and again after the delete: an add of the key on another thread that read the row before the delete
            // committed may have cached it meanwhile, or caches it later and then sees the changed counter (see cacheKey)
            evict(key);
            // in a transaction of the caller the row is deleted when it commits: until then another thread still sees
            // the row and caches the key (its add is a duplicate), so the key is removed from the cache again after the
            // commit
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
        evict(null);
        evictAfterCommit(null);
    }

    /**
     * Caches the key once the transaction that inserted its row commits. In a transaction of the caller that rolls back
     * (for example a transacted route), the row is gone, and a cached key would make the redelivery of the message a
     * duplicate. Without a transaction with synchronization the key is cached at once.
     */
    private void cacheAfterCommit(String key, long seen) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // a remove of the key earlier in the same transaction does not evict it at the commit: the row is there again
            cancelEvictAfterCommit(key);
            TransactionSynchronizationManager.registerSynchronization(new CacheAfterCommit(this, key, seen));
        } else {
            cacheKey(key, seen);
        }
    }

    /**
     * Caches the key, unless a remove or clear evicted keys since the add read the row: that delete may have removed
     * the row, and its eviction may have run before this merge. Either the eviction runs after the merge and removes
     * the key, or it has changed the counter before the check below, so a read of the row before a delete committed
     * does not leave its key in the cache (with read committed: a snapshot of a transaction of the caller older than
     * the counter is not covered). An add that runs alongside a remove or clear of any key is therefore not cached,
     * which costs one SELECT later.
     */
    private void cacheKey(String key, long seen) {
        cache.merge(key, 1, Integer::sum);
        if (evictions.get() != seen) {
            cache.remove(key);
        }
    }

    /**
     * Removes the key (or every key when the key is null) from the cache after its row was deleted. The counter changes
     * first, so an add that cached the key meanwhile either sees the change or is removed here.
     */
    private void evict(String key) {
        evictions.incrementAndGet();
        if (key == null) {
            cache.clear();
        } else {
            cache.remove(key);
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

    /**
     * An add of the key later in the same transaction cancels its eviction after the commit, as the row is there again
     * when the transaction commits. The eviction of a clear is kept: the add is then not cached, which costs one
     * SELECT.
     */
    private void cancelEvictAfterCommit(String key) {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof EvictAfterCommit pending && pending.repository == this
                    && key.equals(pending.key)) {
                pending.cancelled = true;
            }
        }
    }

    private static final class EvictAfterCommit implements TransactionSynchronization {
        private final JdbcCachedMessageIdRepository repository;
        private final String key;
        // set and read by the thread of the transaction
        private boolean cancelled;

        private EvictAfterCommit(JdbcCachedMessageIdRepository repository, String key) {
            this.repository = repository;
            this.key = key;
        }

        @Override
        public void afterCommit() {
            if (!cancelled) {
                repository.evict(key);
            }
        }
    }

    private static final class CacheAfterCommit implements TransactionSynchronization {
        private final JdbcCachedMessageIdRepository repository;
        private final String key;
        private final long seen;
        // set and read by the thread of the transaction
        private boolean cancelled;

        private CacheAfterCommit(JdbcCachedMessageIdRepository repository, String key, long seen) {
            this.repository = repository;
            this.key = key;
            this.seen = seen;
        }

        @Override
        public void afterCommit() {
            if (!cancelled) {
                repository.cacheKey(key, seen);
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
        // a remove or clear while the keys are read may delete a row the query saw (as for an add, see cacheKey)
        long seen = evictions.get();
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
        if (evictions.get() != seen) {
            // the loaded keys may include a deleted row: start empty instead (only misses until they are cached again)
            cache.clear();
        }
    }

}

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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caching version of {@link JdbcMessageIdRepository}
 */
public class JdbcCachedMessageIdRepository extends JdbcMessageIdRepository {
    private volatile Map<String, Integer> cache = new ConcurrentHashMap<>();
    private final AtomicInteger hitCount = new AtomicInteger();
    private final AtomicInteger missCount = new AtomicInteger();
    private String queryAllString
            = "SELECT messageId, COUNT(*) FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? GROUP BY messageId";

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
            queryAllString = queryAllString.replaceFirst(DEFAULT_TABLENAME, getTableName());
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
        cache.merge(key, 1, Integer::sum);
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
        return super.remove(key);
    }

    @Override
    public void clear() {
        cache.clear();
        hitCount.set(0);
        missCount.set(0);
        super.clear();
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

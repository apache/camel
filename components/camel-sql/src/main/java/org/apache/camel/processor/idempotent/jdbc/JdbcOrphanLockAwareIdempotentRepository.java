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

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.ShutdownableService;
import org.apache.camel.spi.ExecutorServiceManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implementation of {@link AbstractJdbcMessageIdRepository} which handles orphan locks resulting from jvm crash.
 *
 * When an instance of the application acquires a lock on the idempotent repository, the lock attributes are added to a
 * HashSet. While the lock is help by the instance, the instance keeps updating the createdAt column with the current
 * timestamp indicating the instance holding the lock is active.
 *
 * A lock is granted to an instance if either the entry for the lock attributes do not exists in the
 * CAMEL_MESSAGEPROCESSED table or if in case the instance holding the lock has crashed. This is determined if the
 * timestamp on the createdAt column is more than the lockMaxAge.
 */
public class JdbcOrphanLockAwareIdempotentRepository extends JdbcMessageIdRepository implements ShutdownableService {

    private final StampedLock sl = new StampedLock();

    private final Set<ProcessorNameAndMessageId> processorNameMessageIdSet = new HashSet<>();

    private ExecutorServiceManager executorServiceManager;

    private ScheduledExecutorService executorService;

    private CamelContext context;

    /** Max age of read lock in milliseconds **/
    private long lockMaxAgeMillis;

    /** intervals after which keep alive is sent for the locks held by an instance **/
    private long lockKeepAliveIntervalMillis;

    private String updateTimestampQuery
            = "UPDATE CAMEL_MESSAGEPROCESSED SET createdAt =? WHERE processorName =? AND messageId = ?";

    public JdbcOrphanLockAwareIdempotentRepository(CamelContext camelContext) {
        super();
        this.context = camelContext;
    }

    public JdbcOrphanLockAwareIdempotentRepository(DataSource dataSource, String processorName, CamelContext camelContext) {
        super(dataSource, processorName);
        this.context = camelContext;
    }

    public JdbcOrphanLockAwareIdempotentRepository(DataSource dataSource, TransactionTemplate transactionTemplate,
                                                   String processorName, CamelContext camelContext) {
        super(dataSource, transactionTemplate, processorName);
        this.context = camelContext;
    }

    public JdbcOrphanLockAwareIdempotentRepository(JdbcTemplate jdbcTemplate,
                                                   TransactionTemplate transactionTemplate, CamelContext camelContext) {
        super(jdbcTemplate, transactionTemplate);
        this.context = camelContext;
    }

    @Override
    protected int queryForInt(String key) {
        /**
         * If the update timestamp time is more than lockMaxAge then assume that the lock is orphan and the process
         * which had acquired the lock has died
         */
        String orphanLockRecoverQueryString = getQueryString() + " AND createdAt >= ?";
        Timestamp xMillisAgo = new Timestamp(System.currentTimeMillis() - lockMaxAgeMillis);
        return jdbcTemplate.queryForObject(orphanLockRecoverQueryString, Integer.class, processorName, key,
                xMillisAgo);
    }

    @Override
    protected int delete(String key) {
        long stamp = sl.writeLock();
        try {
            int result = super.delete(key);
            processorNameMessageIdSet.remove(new ProcessorNameAndMessageId(processorName, key));
            return result;
        } finally {
            sl.unlockWrite(stamp);
        }

    }

    @Override
    protected int insert(String key) {
        Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
        long stamp = sl.writeLock();
        try {
            if (jdbcTemplate.queryForObject(getQueryString(), Integer.class, processorName, key) == 0) {
                int result = jdbcTemplate.update(getInsertString(), processorName, key, currentTimestamp);
                processorNameMessageIdSet.add(new ProcessorNameAndMessageId(processorName, key));
                return result;
            } else {
                //Update in case of orphan lock where a process dies without releasing exist lock
                return jdbcTemplate.update(getUpdateTimestampQuery(), currentTimestamp,
                        processorName, key);
            }
        } finally {
            sl.unlockWrite(stamp);
        }
    }

    @Override
    protected void doInit() throws Exception {
        if (lockMaxAgeMillis <= lockKeepAliveIntervalMillis) {
            throw new IllegalStateException("value of lockMaxAgeMillis cannot be <= lockKeepAliveIntervalMillis");
        }
        Objects.requireNonNull(this.context, () -> "context cannot be null");

        super.doInit();
        if (getTableName() != null) {
            updateTimestampQuery = updateTimestampQuery.replaceFirst(DEFAULT_TABLENAME, getTableName());
        }
        executorServiceManager = context.getExecutorServiceManager();
        executorService = executorServiceManager.newSingleThreadScheduledExecutor(this, this.getClass().getSimpleName());

        // Schedule a task which will keep updating the timestamp on the acquired locks at lockKeepAliveInterval so that
        // the timestamp does not reaches lockMaxAge
        executorService.scheduleWithFixedDelay(new LockKeepAliveTask(), lockKeepAliveIntervalMillis,
                lockKeepAliveIntervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doShutdown() throws Exception {
        if (executorServiceManager != null && executorService != null) {
            executorServiceManager.shutdownGraceful(executorService);
        }
    }

    @Override
    protected int delete() {
        long stamp = sl.writeLock();
        try {
            int result = super.delete();
            processorNameMessageIdSet.clear();
            return result;
        } finally {
            sl.unlockWrite(stamp);
        }

    }

    void keepAlive() {
        Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
        long stamp = sl.readLock();
        try {
            List<Object[]> args = processorNameMessageIdSet.stream()
                    .map(processorNameMessageId -> new Object[] {
                            currentTimestamp, processorNameMessageId.processorName, processorNameMessageId.messageId })
                    .collect(Collectors.toList());
            transactionTemplate.execute(status -> jdbcTemplate.batchUpdate(getUpdateTimestampQuery(), args));
        } catch (Exception e) {
            log.error("failed updating createdAt in keepAlive due to ", e);
        } finally {
            sl.unlockRead(stamp);
        }
    }

    public Set<ProcessorNameAndMessageId> getProcessorNameMessageIdSet() {
        return processorNameMessageIdSet;
    }

    public String getUpdateTimestampQuery() {
        return updateTimestampQuery;
    }

    public void setUpdateTimestampQuery(String updateTimestampQuery) {
        this.updateTimestampQuery = updateTimestampQuery;
    }

    public long getLockMaxAgeMillis() {
        return lockMaxAgeMillis;
    }

    public void setLockMaxAgeMillis(long lockMaxAgeMillis) {
        this.lockMaxAgeMillis = lockMaxAgeMillis;
    }

    public long getLockKeepAliveIntervalMillis() {
        return lockKeepAliveIntervalMillis;
    }

    public void setLockKeepAliveIntervalMillis(long lockKeepAliveIntervalMillis) {
        this.lockKeepAliveIntervalMillis = lockKeepAliveIntervalMillis;
    }

    class LockKeepAliveTask implements Runnable {

        @Override
        public void run() {
            keepAlive();
        }
    }

    static class ProcessorNameAndMessageId {
        private final String processorName;
        private final String messageId;

        public ProcessorNameAndMessageId(String processorName, String messageId) {
            this.processorName = processorName;
            this.messageId = messageId;
        }

        public String getProcessorName() {
            return processorName;
        }

        public String getMessageId() {
            return messageId;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((messageId == null) ? 0 : messageId.hashCode());
            result = prime * result + ((processorName == null) ? 0 : processorName.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;

            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ProcessorNameAndMessageId other = (ProcessorNameAndMessageId) obj;
            if (messageId == null) {
                if (other.messageId != null) {
                    return false;
                }
            } else if (!messageId.equals(other.messageId)) {
                return false;
            }
            if (processorName == null) {
                if (other.processorName != null) {
                    return false;
                }
            } else if (!processorName.equals(other.processorName)) {
                return false;
            }
            return true;
        }
    }

}

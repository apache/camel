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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.util.ObjectHelper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implementation of {@link AbstractJdbcMessageIdRepository} which handles orphan locks resulting from jvm crash.
 *
 * When an instance of the application acquires a lock on the idempotent repository, the lock attributes are added to a
 * set. While the lock is held by the instance, the instance keeps updating the createdAt column with the current
 * timestamp indicating the instance holding the lock is active.
 *
 * A lock is granted to an instance if either the entry for the lock attributes do not exists in the
 * CAMEL_MESSAGEPROCESSED table or if in case the instance holding the lock has crashed. This is determined if the
 * timestamp on the createdAt column is more than the lockMaxAge.
 *
 * A key that this instance holds (it was added and is not confirmed or removed yet) is never an orphan for this
 * instance, whatever the age of its createdAt: when the keep-alive cannot refresh it for longer than the lockMaxAge
 * (the database cannot be reached), the message may still be processed by this instance, which then still treats the
 * key as a duplicate. Another instance cannot tell this instance from one that crashed, so it does take the key over
 * then: the lockMaxAge must be longer than the longest database outage that must not lead to a message processed twice.
 *
 * A key that is confirmed (the message was processed) is no longer a lock: its createdAt is set to null, which the
 * orphan check never treats as old, and it is no longer kept alive. So it stays a duplicate after the instance that
 * added it stops, as in {@link JdbcMessageIdRepository}. A confirmed key has no age then, so a purge by createdAt does
 * not delete it.
 * <p/>
 * The createdAt column must store null. The repository checks this once when it starts, in a transaction that it rolls
 * back (it inserts a row, confirms it with the confirm query, and checks that createdAt is null). When the check fails
 * (a NOT NULL column, or a MySQL or MariaDB TIMESTAMP column with explicit_defaults_for_timestamp=OFF), it logs a WARN
 * and does not confirm keys at all: they stay locks, as before, and a confirm never sends a statement that could fail
 * inside a transaction of the caller. A confirm that fails later (for example the connection is lost) logs a WARN, and
 * when the repository joins a transaction of the caller (same DataSource), it marks that transaction as rollback only,
 * as a failed add or remove does.
 * <p/>
 * When another instance took a key over (this instance could not refresh it for longer than the lockMaxAge), this
 * instance no longer removes the key nor keeps it alive: the lease is then the one of the other instance, which still
 * processes the message. The remove and the keep-alive of a key that this instance holds only match the lease that this
 * instance wrote last (see the Lease class). A confirm still confirms the key, as the message was processed.
 */
public class JdbcOrphanLockAwareIdempotentRepository extends JdbcMessageIdRepository {

    protected static final String DEFAULT_UPDATE_TIMESTAMP_QUERY
            = "UPDATE CAMEL_MESSAGEPROCESSED SET createdAt =? WHERE processorName =? AND messageId = ?";
    protected static final String DEFAULT_CONFIRM_QUERY
            = "UPDATE CAMEL_MESSAGEPROCESSED SET createdAt = NULL WHERE processorName = ? AND messageId = ?";

    // the keys this instance holds and keeps alive, each with the lease this instance last wrote for it. A concurrent
    // map, so no lock is held while SQL runs: add and remove hold a connection of the pool while they run, and the
    // keep-alive needs one too, so a lock held across SQL could deadlock with a small pool
    private final Map<ProcessorNameAndMessageId, Lease> leases = new ConcurrentHashMap<>();
    private final Set<ProcessorNameAndMessageId> processorNameMessageIdSet = leases.keySet();

    // the keys whose confirm runs: the confirm takes them out of processorNameMessageIdSet before its UPDATE, so that the
    // keep-alive does not wait for the row lock of the confirm, but this instance still holds them until the confirm ends
    private final Set<ProcessorNameAndMessageId> confirming = ConcurrentHashMap.newKeySet();

    private ExecutorServiceManager executorServiceManager;

    private ScheduledExecutorService executorService;

    private CamelContext context;

    /** Max age of read lock in milliseconds **/
    private long lockMaxAgeMillis;

    /** intervals after which keep alive is sent for the locks held by an instance **/
    private long lockKeepAliveIntervalMillis;

    private String updateTimestampQuery = DEFAULT_UPDATE_TIMESTAMP_QUERY;

    private String confirmQuery = DEFAULT_CONFIRM_QUERY;

    // set when the repository starts: whether the confirm query stores null in createdAt
    private volatile boolean confirmChecked;

    // when the keep-alive last refreshed the leases of this instance (or the repository started), and whether it logged
    // that they may have expired since then
    private volatile long leasesRefreshedAt = System.currentTimeMillis();
    private volatile boolean leasesMayHaveExpired;

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
        // a key this instance holds is not an orphan, whatever the age of its lease: when the keep-alive could not refresh
        // it for longer than lockMaxAge (the database could not be reached), this instance may still process the message,
        // and taking the key over would process it a second time
        if (holds(new ProcessorNameAndMessageId(processorName, key))) {
            return 1;
        }
        /**
         * If the update timestamp time is more than lockMaxAge then assume that the lock is orphan and the process
         * which had acquired the lock has died
         */
        // NOTE: the querystring is passed by the user, so, we are safe as he decide what query wants to perform.
        // a confirmed key (createdAt is null) is never an orphan lock
        String orphanLockRecoverQueryString = getQueryString() + " AND (createdAt >= ? OR createdAt IS NULL)";
        Timestamp xMillisAgo = new Timestamp(System.currentTimeMillis() - lockMaxAgeMillis);
        return jdbcTemplate.queryForObject(orphanLockRecoverQueryString, Integer.class, processorName, key, // NOSONAR
                xMillisAgo);
    }

    // whether this instance holds the key: it is kept alive, or its confirm runs. The confirm adds the key to confirming
    // before it takes it out of processorNameMessageIdSet, so the first two reads find a key whose confirm starts
    private boolean holds(ProcessorNameAndMessageId id) {
        if (processorNameMessageIdSet.contains(id) || confirming.contains(id)) {
            return true;
        }
        // a confirm that failed puts the key back in processorNameMessageIdSet before it takes it out of confirming,
        // possibly between the two reads above
        return processorNameMessageIdSet.contains(id);
    }

    @Override
    protected int delete(String key) {
        Lease lease = leases.get(new ProcessorNameAndMessageId(processorName, key));
        int result;
        if (lease == null) {
            // not a key that this instance keeps alive (such as a key removed by hand, or one added before a restart of
            // the JVM, or earlier in the current transaction): deleted by its key
            result = super.delete(key);
        } else if (lease.lost) {
            // the keep-alive found that another instance took the key over: its row is not the lease of this instance
            result = 0;
        } else {
            result = jdbcTemplate.update(getDeleteString() + " AND createdAt < ?", processorName, key, ownedBefore(lease));
            if (result == 0) {
                log.warn("Not removing the key {} of processor {} from the idempotent repository: another instance took it"
                         + " over, as this instance could not refresh its lease for longer than lockMaxAgeMillis, or confirmed"
                         + " or removed it. The other instance may still process its message.",
                        key, processorName);
            }
        }
        stopKeepAlive(key);
        return result;
    }

    // the createdAt of a row that is the lease of this instance is earlier than this: see Lease
    private Timestamp ownedBefore(Lease lease) {
        return ownedBefore(lease.writtenAt, (lockMaxAgeMillis + lockKeepAliveIntervalMillis) / 2, ZoneId.systemDefault());
    }

    /**
     * The lease written at writtenAt plus the margin. A createdAt column without time zone (the default table) holds
     * the local time of the JVM, which goes back an hour at the end of daylight saving time: a lease written shortly
     * before would then look later than the instant plus the margin. So the margin is added to the local time too, and
     * the later of the two is used. For a column with time zone, this matches up to an hour more for a lease written
     * shortly before that change, as the remove and the keep-alive matched any createdAt before.
     */
    static Timestamp ownedBefore(long writtenAt, long margin, ZoneId zone) {
        long local = LocalDateTime.ofInstant(Instant.ofEpochMilli(writtenAt), zone).plus(margin, ChronoUnit.MILLIS)
                .atZone(zone).toInstant().toEpochMilli();
        return new Timestamp(Math.max(writtenAt + margin, local));
    }

    /**
     * Confirms the key: the message was processed, so the key is no longer a lock that is kept alive and that another
     * instance can take over when this one stops. Its createdAt is set to null, which the orphan check never treats as
     * old, so the key stays a duplicate for good.
     * <p/>
     * When the key cannot be confirmed (a failure of the database), it stays a lock that is kept alive while this
     * instance runs, as before, and false is returned; also when there is no such key. With no confirm query (null or
     * empty), or when the check at start found that the confirm query cannot set createdAt to null, a key is not
     * confirmed either, no statement is sent, and true is returned. The same holds before the repository is started,
     * and after the confirm query is changed, until the next start.
     *
     * @return {@code true} if the key was confirmed, {@code false} otherwise
     */
    @Override
    public boolean confirm(final String key) {
        String query = getConfirmQuery();
        if (ObjectHelper.isEmpty(query) || !confirmChecked) {
            // confirmed keys are kept as locks
            return true;
        }
        ProcessorNameAndMessageId id = new ProcessorNameAndMessageId(processorName, key);
        // the key leaves the keep-alive right away, as in delete: inside a transaction of the caller, the confirm locks
        // the row until that transaction ends, and a later keep-alive that updated the row would wait for it (and could
        // deadlock with a later confirm or remove in the same transaction). It is kept alive again when the confirm fails
        // or its transaction does not commit; not when no row matched, as then there is no lease of this instance.
        // Until the confirm ends, this instance still holds the key (see queryForInt), but does not keep it alive.
        // The confirm matches the row by its key also when another instance took the key over meanwhile: the message
        // was processed, so the key is a duplicate from now on (the other instance does not remove it then).
        confirming.add(id);
        Lease lease = leases.remove(id);
        boolean keptAlive = lease != null;
        boolean completed = false;
        try {
            // in a transaction of its own (or the one of the caller), as add and remove
            Boolean rc = transactionTemplate.execute(status -> {
                if (jdbcTemplate.update(query, processorName, key) == 0) {
                    return false;
                }
                // an insert of the key earlier in this transaction does not keep it alive after the commit
                cancelKeepAliveAfterCommit(id);
                if (keptAlive && TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new KeepAliveUnlessCommitted(this, id, lease));
                }
                return true;
            });
            completed = true;
            return Boolean.TRUE.equals(rc);
        } catch (DataAccessException | TransactionException e) {
            log.warn("Cannot confirm the key {} of processor {} in the idempotent repository: it stays a lock that is kept"
                     + " alive while this instance runs and that another instance takes over when this one stops"
                     + " (inside a transaction of the caller, that transaction is marked as rollback only). Caused by: {}",
                    key, processorName, e.getMessage());
            log.debug("Cannot confirm the key {} of processor {} in the idempotent repository", key, processorName, e);
            return false;
        } finally {
            if (keptAlive && !completed) {
                leases.put(id, lease);
            }
            confirming.remove(id);
        }
    }

    /**
     * Checks that the confirm query stores null in createdAt, in a new transaction that is always rolled back: inserts
     * a row with a key of its own, confirms it, and reads it back. When the check fails, keys are not confirmed (they
     * stay locks, as before), so that a confirm never sends a statement that fails inside a transaction of the caller.
     */
    private boolean checkConfirmQuery(String query) {
        TransactionTemplate check = new TransactionTemplate(transactionTemplate.getTransactionManager(), transactionTemplate);
        check.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String key = UUID.randomUUID().toString();
        Boolean storesNull;
        try {
            storesNull = check.execute(status -> {
                status.setRollbackOnly();
                jdbcTemplate.update(getInsertString(), processorName, key, new Timestamp(System.currentTimeMillis()));
                int updated;
                try {
                    updated = jdbcTemplate.update(query, processorName, key);
                } catch (DataIntegrityViolationException e) {
                    // a NOT NULL column
                    log.debug("The confirm query cannot set createdAt to null", e);
                    return false;
                }
                if (updated == 0) {
                    throw new IllegalStateException("the confirm query updated no row");
                }
                // such as a MySQL or MariaDB TIMESTAMP column with explicit_defaults_for_timestamp=OFF, which stores the
                // current time for null
                Integer stored = jdbcTemplate.queryForObject(getQueryString() + " AND createdAt IS NULL", Integer.class,
                        processorName, key);
                return stored != null && stored > 0;
            });
        } catch (RuntimeException e) {
            log.warn("Cannot check the confirm query of processor {} in the idempotent repository ({}): keys are not"
                     + " confirmed until the repository is started again. They stay locks that another instance takes over"
                     + " when this one stops.",
                    processorName, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            log.debug("Cannot check the confirm query of processor {} in the idempotent repository", processorName, e);
            return false;
        } finally {
            // when the transaction manager does not manage the connection of the JdbcTemplate (another DataSource), the
            // rollback does not remove the row of the check
            try {
                jdbcTemplate.update(getDeleteString(), processorName, key);
            } catch (RuntimeException e) {
                log.debug("Cannot delete the row of the check of the confirm query", e);
            }
        }
        if (!Boolean.TRUE.equals(storesNull)) {
            log.warn("Confirmed keys of processor {} stay locks that another instance takes over when this one stops, as the"
                     + " createdAt column does not store null. Make the createdAt column nullable (on MySQL or MariaDB with"
                     + " explicit_defaults_for_timestamp=OFF declare it TIMESTAMP NULL), or set confirmQuery to null or empty"
                     + " to keep confirmed keys as locks.",
                    processorName);
            return false;
        }
        return true;
    }

    // the key is no longer kept alive, also when an insert of it earlier in the current transaction would keep it alive
    // after the commit
    private void stopKeepAlive(String key) {
        ProcessorNameAndMessageId id = new ProcessorNameAndMessageId(processorName, key);
        processorNameMessageIdSet.remove(id);
        cancelKeepAliveAfterCommit(id);
    }

    // an insert of the key earlier in the current transaction keeps it alive only after the commit: cancel that
    private void cancelKeepAliveAfterCommit(ProcessorNameAndMessageId id) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                if (synchronization instanceof KeepAliveAfterCommit pending && pending.repository == this
                        && pending.id.equals(id)) {
                    pending.cancelled = true;
                }
            }
        }
    }

    @Override
    protected int insert(String key) {
        Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
        if (jdbcTemplate.queryForObject(getQueryString(), Integer.class, processorName, key) == 0) {
            int result = jdbcTemplate.update(getInsertString(), processorName, key, currentTimestamp);
            keepAliveAfterCommit(key, currentTimestamp);
            return result;
        } else {
            // Row exists — try to claim orphan lock with conditional UPDATE.
            // Only succeeds if createdAt is older than lockMaxAge (lock is truly orphaned).
            // Returns 0 if the lock is held by an active instance, preventing the race in CAMEL-23294.
            String orphanLockRecoverQueryString = getUpdateTimestampQuery() + " AND createdAt < ?";
            Timestamp xMillisAgo = new Timestamp(System.currentTimeMillis() - lockMaxAgeMillis);
            int result = jdbcTemplate.update(orphanLockRecoverQueryString, currentTimestamp, processorName, key, xMillisAgo);
            if (result > 0) {
                log.debug("Orphan lock seized for key: {}", key);
                keepAliveAfterCommit(key, currentTimestamp);
            }
            return result;
        }
    }

    /**
     * Keeps the lease of the key alive once the transaction that inserted or seized its row commits. When that
     * transaction rolls back (for example in a transacted route with removeOnFailure=false), the row is not there, so
     * the key must not be kept alive: another node may insert it later, and this node would keep its lease alive.
     */
    private void keepAliveAfterCommit(String key, Timestamp createdAt) {
        ProcessorNameAndMessageId id = new ProcessorNameAndMessageId(processorName, key);
        Lease lease = new Lease(createdAt.getTime(), false);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new KeepAliveAfterCommit(this, id, lease));
        } else {
            leases.put(id, lease);
        }
    }

    /**
     * The lease that this instance last wrote for a key it holds: the createdAt of its insert or takeover, or of the
     * last keep-alive that refreshed it. Another instance takes the key over only when its createdAt is older than the
     * lockMaxAge, and then writes a createdAt of its own, later than this one by more than the lockMaxAge. A keep-alive
     * of this instance whose outcome is not known yet (it runs while the key is confirmed or removed, or it failed
     * after the database committed it) writes about one keep-alive interval later than this one. So the remove and the
     * keep-alive of the key only match a createdAt earlier than halfway between the two, which leaves room for a late
     * keep-alive and for the rounding of the createdAt column. When the keep-alive does not match the row, the lease is
     * lost: the key is no longer kept alive, nor removed, but this instance still holds it (see queryForInt) until it
     * is removed or confirmed. The clocks of the instances do not need to agree: the other instance compares the stored
     * createdAt with its own clock, and writes a createdAt from the same clock. A keep-alive of this instance that
     * committed although it failed, after the keep-alive could not refresh the key for longer than half of lockMaxAge
     * minus the keep-alive interval, is not told apart from a takeover: the lease is then marked lost.
     */
    private static final class Lease {
        private final long writtenAt;
        private final boolean lost;

        private Lease(long writtenAt, boolean lost) {
            this.writtenAt = writtenAt;
            this.lost = lost;
        }
    }

    /**
     * Adds a key to the keys kept alive after the commit of the transaction that inserted or seized its row, unless the
     * key was deleted later in that transaction.
     */
    private static final class KeepAliveAfterCommit implements TransactionSynchronization {
        private final JdbcOrphanLockAwareIdempotentRepository repository;
        private final ProcessorNameAndMessageId id;
        private final Lease lease;
        // set and read by the thread of the transaction
        private boolean cancelled;

        private KeepAliveAfterCommit(JdbcOrphanLockAwareIdempotentRepository repository, ProcessorNameAndMessageId id,
                                     Lease lease) {
            this.repository = repository;
            this.id = id;
            this.lease = lease;
        }

        @Override
        public void afterCommit() {
            if (!cancelled) {
                repository.leases.put(id, lease);
            }
        }
    }

    /**
     * Keeps a confirmed key alive again when the transaction of its confirm does not commit: the row is still a lock
     * then. When the outcome is unknown, the key is kept alive too: the keep-alive does not update a row that was
     * confirmed.
     */
    private static final class KeepAliveUnlessCommitted implements TransactionSynchronization {
        private final JdbcOrphanLockAwareIdempotentRepository repository;
        private final ProcessorNameAndMessageId id;
        private final Lease lease;

        private KeepAliveUnlessCommitted(JdbcOrphanLockAwareIdempotentRepository repository, ProcessorNameAndMessageId id,
                                         Lease lease) {
            this.repository = repository;
            this.id = id;
            this.lease = lease;
        }

        @Override
        public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
                repository.leases.put(id, lease);
            }
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
            // update query string from default table name to the new table name
            updateTimestampQuery = DEFAULT_UPDATE_TIMESTAMP_QUERY.replace(DEFAULT_TABLENAME, getTableName());
            if (DEFAULT_CONFIRM_QUERY.equals(confirmQuery)) {
                confirmQuery = DEFAULT_CONFIRM_QUERY.replace(DEFAULT_TABLENAME, getTableName());
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        String query = getConfirmQuery();
        confirmChecked = ObjectHelper.isNotEmpty(query) && checkConfirmQuery(query);

        leasesRefreshedAt = System.currentTimeMillis();
        leasesMayHaveExpired = false;
        executorServiceManager = context.getExecutorServiceManager();
        executorService = executorServiceManager.newSingleThreadScheduledExecutor(this, this.getClass().getSimpleName());

        // Schedule a task which will keep updating the timestamp on the acquired locks at lockKeepAliveInterval so that
        // the timestamp does not reaches lockMaxAge
        executorService.scheduleWithFixedDelay(new LockKeepAliveTask(), lockKeepAliveIntervalMillis,
                lockKeepAliveIntervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doStop() throws Exception {
        if (executorServiceManager != null && executorService != null) {
            executorServiceManager.shutdownGraceful(executorService);
            executorService = null;
        }
        super.doStop();
    }

    @Override
    protected int delete() {
        int result = super.delete();
        processorNameMessageIdSet.clear();
        // and the inserts earlier in this transaction are not kept alive after the commit either
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                if (synchronization instanceof KeepAliveAfterCommit pending && pending.repository == this) {
                    pending.cancelled = true;
                }
            }
        }
        return result;
    }

    void keepAlive() {
        // the leases get a later timestamp, taken once the connection was obtained
        long started = System.currentTimeMillis();
        List<Refresh> refreshes;
        try {
            refreshes = transactionTemplate.execute(status -> {
                // take the timestamp and the keys inside the transaction, after the connection was obtained (with the
                // DataSourceTransactionManager), as waiting for it can take a while
                Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
                // only the lease of this instance (see Lease), and not a key that was confirmed meanwhile (its createdAt
                // is null). One statement per key, as a batch may not tell which keys it updated
                String query = getUpdateTimestampQuery() + " AND createdAt < ?";
                List<Refresh> result = new ArrayList<>();
                for (Map.Entry<ProcessorNameAndMessageId, Lease> entry : leases.entrySet()) {
                    ProcessorNameAndMessageId id = entry.getKey();
                    Lease lease = entry.getValue();
                    if (!lease.lost) {
                        int updated = jdbcTemplate.update(query, currentTimestamp, id.processorName, id.messageId,
                                ownedBefore(lease));
                        result.add(new Refresh(
                                id, lease,
                                updated > 0 ? new Lease(currentTimestamp.getTime(), false) : new Lease(lease.writtenAt, true)));
                    }
                }
                return result;
            });
        } catch (Exception e) {
            log.error("failed updating createdAt in keepAlive due to ", e);
            warnOnceWhenLeasesMayHaveExpired();
            return;
        }
        // once committed; not for a key that was removed or confirmed meanwhile
        for (Refresh refresh : refreshes) {
            if (leases.replace(refresh.id, refresh.lease, refresh.next) && refresh.next.lost) {
                log.warn("The keep-alive of processor {} no longer keeps the key {} alive: another instance took it over, as"
                         + " this instance could not refresh its lease for longer than lockMaxAgeMillis, or confirmed or"
                         + " removed it. This instance does not remove the key, and still treats it as a duplicate.",
                        processorName, refresh.id.messageId);
            }
        }
        if (leasesMayHaveExpired) {
            leasesMayHaveExpired = false;
            log.info("The keep-alive of processor {} refreshes the leases of this instance again, {} ms after it last did:"
                     + " another instance may have taken over some of its keys meanwhile",
                    processorName, started - leasesRefreshedAt);
        }
        leasesRefreshedAt = started;
    }

    // once per outage: the keep-alive has not refreshed the leases for longer than lockMaxAge, so other instances may take
    // over the keys of this instance, as if it had crashed (this instance does not, see queryForInt)
    private void warnOnceWhenLeasesMayHaveExpired() {
        // now, as a round can fail only after a while (such as the connection timeout of the pool)
        long notRefreshedFor = System.currentTimeMillis() - leasesRefreshedAt;
        int held = processorNameMessageIdSet.size();
        if (!leasesMayHaveExpired && held > 0 && notRefreshedFor >= lockMaxAgeMillis) {
            leasesMayHaveExpired = true;
            log.warn("The keep-alive of processor {} has not refreshed the leases of this instance for {} ms, longer than"
                     + " lockMaxAgeMillis ({} ms): another instance may take over the {} keys this instance holds, as if it"
                     + " had crashed, and process their messages a second time. This instance still treats them as"
                     + " duplicates. Set lockMaxAgeMillis longer than the longest database outage to survive.",
                    processorName, notRefreshedFor, lockMaxAgeMillis, held);
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

    public String getConfirmQuery() {
        return confirmQuery;
    }

    /**
     * The query that confirms a key: it sets its createdAt to null. It may set other columns of a custom table too, for
     * example the time of the confirm, to purge confirmed keys by age. Set it to null or empty to keep confirmed keys
     * as locks that another instance takes over when this one stops (the behaviour before Camel 4.24). It is checked
     * when the repository starts: a query set later is not used until the next start.
     */
    public void setConfirmQuery(String confirmQuery) {
        this.confirmQuery = confirmQuery;
        // checked again when the repository starts
        this.confirmChecked = false;
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

    // a lease that a keep-alive refreshed, or found lost
    private record Refresh(ProcessorNameAndMessageId id, Lease lease, Lease next) {
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

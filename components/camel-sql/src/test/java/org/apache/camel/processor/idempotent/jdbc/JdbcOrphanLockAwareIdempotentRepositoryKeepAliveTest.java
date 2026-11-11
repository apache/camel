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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The keep-alive must not wait for the add or remove of a key, nor make them wait, while either holds a connection of
 * the pool. With a lock held across the SQL, a keep-alive that waits for the only connection while add or remove,
 * holding that connection, waits for the lock deadlocks the node; and a keep-alive that waits while add runs a slow
 * statement (such as one waiting for a row lock of another node) lets the leases of the node expire.
 */
class JdbcOrphanLockAwareIdempotentRepositoryKeepAliveTest {

    private static final String PROCESSOR_NAME = "APP_1";
    private static final String HELD = "HELD";
    private static final String KEY = "KEY";
    private static final long LOCK_MAX_AGE_MILLIS = 300_000L;
    private static final long WAIT_SECONDS = 10;

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private JdbcOrphanLockAwareIdempotentRepository repository;
    private Pool pool;
    private Timestamp staleCreatedAt;

    @BeforeEach
    void setup() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcTemplate = new JdbcTemplate(database);
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            // a thread held by the pool is let go, also when the test failed before it did so
            pool.release.countDown();
        }
        if (repository != null) {
            repository.shutdown();
        }
        database.shutdown();
    }

    private void start(int connections) {
        pool = new Pool(database, connections);
        repository = new JdbcOrphanLockAwareIdempotentRepository(pool, PROCESSOR_NAME, new DefaultCamelContext());
        repository.setLockMaxAgeMillis(LOCK_MAX_AGE_MILLIS);
        // the test calls keepAlive() itself
        repository.setLockKeepAliveIntervalMillis(LOCK_MAX_AGE_MILLIS / 2);
        repository.start();
        assertTrue(repository.add(HELD));
        // the lease of the held key is old, so a keep-alive that runs is seen
        staleCreatedAt = new Timestamp(System.currentTimeMillis() - LOCK_MAX_AGE_MILLIS / 2);
        jdbcTemplate.update("UPDATE CAMEL_MESSAGEPROCESSED SET createdAt = ? WHERE messageId = ?", staleCreatedAt, HELD);
    }

    @Test
    void testKeepAliveWaitingForTheOnlyConnectionDoesNotBlockAdd() throws Exception {
        start(1);
        AtomicReference<Object> added = new AtomicReference<>();
        Thread adder = worker("adder", added, () -> repository.add(KEY));

        keepAliveWhileHoldingTheOnlyConnection(adder);

        assertEquals(Boolean.TRUE, added.get());
        assertEquals(2, repository.getProcessorNameMessageIdSet().size());
    }

    @Test
    void testKeepAliveWaitingForTheOnlyConnectionDoesNotBlockRemove() throws Exception {
        start(1);
        assertTrue(repository.add(KEY));
        AtomicReference<Object> removed = new AtomicReference<>();
        Thread remover = worker("remover", removed, () -> repository.remove(KEY));

        keepAliveWhileHoldingTheOnlyConnection(remover);

        assertEquals(Boolean.TRUE, removed.get());
        assertEquals(1, repository.getProcessorNameMessageIdSet().size());
    }

    /**
     * The worker borrows the only connection and is held there; the keep-alive then waits for the connection, and the
     * worker goes on. Both must finish, and the keep-alive must refresh the held key.
     */
    private void keepAliveWhileHoldingTheOnlyConnection(Thread worker) throws Exception {
        CountDownLatch keepAliveWaits = new CountDownLatch(1);
        pool.holdAfterBorrow(worker);
        pool.onWait = keepAliveWaits::countDown;
        worker.start();
        assertTrue(pool.reached.await(WAIT_SECONDS, TimeUnit.SECONDS));

        Thread keeper = new Thread(repository::keepAlive, "keep-alive");
        keeper.start();
        assertTrue(keepAliveWaits.await(WAIT_SECONDS, TimeUnit.SECONDS));
        pool.release.countDown();

        try {
            worker.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
            keeper.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
            assertFalse(worker.isAlive(), () -> worker.getName() + " is blocked: " + stackOf(worker));
            assertFalse(keeper.isAlive(), () -> "the keep-alive is blocked: " + stackOf(keeper));
        } finally {
            // on a deadlock, end the wait of the keep-alive for the connection, so both threads end
            keeper.interrupt();
            keeper.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
            worker.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        }
        assertTrue(createdAt(HELD).after(staleCreatedAt), "the keep-alive did not refresh the held key");
    }

    @Test
    void testKeepAliveDoesNotWaitForASlowInsert() throws Exception {
        start(2);
        AtomicReference<Object> added = new AtomicReference<>();
        Thread adder = worker("adder", added, () -> repository.add(KEY));
        // the INSERT of the add is held, as a statement that waits for a row lock of another node would be
        pool.holdAtStatement(adder, "INSERT");
        adder.start();
        assertTrue(pool.reached.await(WAIT_SECONDS, TimeUnit.SECONDS));

        Thread keeper = new Thread(repository::keepAlive, "keep-alive");
        try {
            keeper.start();
            keeper.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
            assertFalse(keeper.isAlive(), () -> "the keep-alive waits for the INSERT: " + stackOf(keeper));
            assertTrue(createdAt(HELD).after(staleCreatedAt), "the keep-alive did not refresh the held key");
        } finally {
            pool.release.countDown();
            adder.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
            keeper.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        }
        assertFalse(adder.isAlive());
        assertEquals(Boolean.TRUE, added.get());
    }

    /**
     * A thread that calls the repository and keeps what it returned, or what it threw.
     */
    private static Thread worker(String name, AtomicReference<Object> result, Supplier<Boolean> call) {
        return new Thread(() -> {
            try {
                result.set(call.get());
            } catch (Throwable e) {
                result.set(e);
            }
        }, name);
    }

    private Timestamp createdAt(String key) {
        return jdbcTemplate.queryForObject("SELECT createdAt FROM CAMEL_MESSAGEPROCESSED WHERE messageId = ?",
                Timestamp.class, key);
    }

    private static String stackOf(Thread thread) {
        StringBuilder sb = new StringBuilder(thread.getState().toString());
        for (StackTraceElement element : thread.getStackTrace()) {
            sb.append("\n\tat ").append(element);
        }
        return sb.toString();
    }

    /**
     * A pool of a fixed number of connections to the embedded database, which waits for a connection as long as it
     * takes, with a point where it holds one thread: after it borrowed a connection, or before it runs a statement.
     */
    private static final class Pool extends DelegatingDataSource {

        private final Semaphore free;
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<Thread> held = new AtomicReference<>();
        private volatile String heldAtStatement;
        private volatile Runnable onWait = () -> {
        };

        Pool(EmbeddedDatabase database, int connections) {
            super(database);
            this.free = new Semaphore(connections);
        }

        void holdAfterBorrow(Thread thread) {
            held.set(thread);
        }

        void holdAtStatement(Thread thread, String sqlPrefix) {
            heldAtStatement = sqlPrefix;
            held.set(thread);
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (!free.tryAcquire()) {
                onWait.run();
                try {
                    free.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("Interrupted while waiting for a connection", e);
                }
            }
            Connection connection;
            try {
                connection = super.getConnection();
            } catch (SQLException | RuntimeException e) {
                free.release();
                throw e;
            }
            if (heldAtStatement == null) {
                hold();
            }
            return pooled(connection);
        }

        private void hold() {
            if (held.compareAndSet(Thread.currentThread(), null)) {
                reached.countDown();
                try {
                    // bounded, so a failed test cannot leave the thread waiting forever
                    release.await(WAIT_SECONDS * 3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        /**
         * The connection, which goes back to the pool when it is closed.
         */
        private Connection pooled(Connection connection) {
            AtomicBoolean closed = new AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
                    (proxy, method, args) -> {
                        if ("close".equals(method.getName())) {
                            if (closed.compareAndSet(false, true)) {
                                try {
                                    connection.close();
                                } finally {
                                    free.release();
                                }
                            }
                            return null;
                        }
                        if ("equals".equals(method.getName())) {
                            return proxy == args[0];
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("prepareStatement".equals(method.getName()) && heldAtStatement != null
                                && ((String) args[0]).startsWith(heldAtStatement)) {
                            hold();
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }
}

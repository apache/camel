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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces CAMEL-23294: JDBC-based Idempotent repository race condition in orphan lock recovery.
 * <p>
 * When multiple instances (separate JVMs) concurrently detect an orphan lock and try to claim it, both succeed because:
 * <ol>
 * <li>{@code add()} ignores the return value of {@code insert()} — it always returns {@code true} when
 * {@code queryForInt()} returned 0</li>
 * <li>{@code insert()} does an unconditional UPDATE on orphan recovery with no guard to prevent two instances from both
 * succeeding</li>
 * </ol>
 */
public class JdbcOrphanLockAwareIdempotentRepositoryConcurrencyTest {

    private static final String PROCESSOR_NAME = "TEST_PROCESSOR";
    private static final long LOCK_MAX_AGE_MILLIS = 300_000L;
    private static final long LOCK_KEEP_ALIVE_MILLIS = 3_000L;

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.HSQL)
                .addScript("classpath:sql/idempotentWithOrphanLockRemoval.sql")
                .generateUniqueName(true)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.shutdown();
        }
    }

    private JdbcOrphanLockAwareIdempotentRepository createRepository() throws Exception {
        JdbcOrphanLockAwareIdempotentRepository repo
                = new JdbcOrphanLockAwareIdempotentRepository(dataSource, PROCESSOR_NAME, new DefaultCamelContext());
        repo.setLockMaxAgeMillis(LOCK_MAX_AGE_MILLIS);
        repo.setLockKeepAliveIntervalMillis(LOCK_KEEP_ALIVE_MILLIS);
        repo.doInit();
        return repo;
    }

    /**
     * CAMEL-23294: Two instances concurrently try to recover the same orphan lock. Only one should succeed.
     * <p>
     * Each repository instance has its own {@code StampedLock}, so the JVM-local lock provides no cross-instance
     * protection — this accurately simulates two separate JVM instances sharing the same database.
     */
    @Test
    void testConcurrentOrphanLockRecoveryShouldAllowOnlyOneInstance() throws Exception {
        String key = "ORPHAN_RACE_KEY";

        // Insert an orphan lock (timestamp well past lockMaxAge)
        Timestamp orphanTimestamp = new Timestamp(System.currentTimeMillis() - LOCK_MAX_AGE_MILLIS - 60_000L);
        jdbcTemplate.update(
                "INSERT INTO CAMEL_MESSAGEPROCESSED (processorName, messageId, createdAt) VALUES (?, ?, ?)",
                PROCESSOR_NAME, key, orphanTimestamp);

        // Two separate repository instances simulate two JVM instances
        JdbcOrphanLockAwareIdempotentRepository instance1 = createRepository();
        JdbcOrphanLockAwareIdempotentRepository instance2 = createRepository();

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicBoolean result1 = new AtomicBoolean(false);
        AtomicBoolean result2 = new AtomicBoolean(false);
        AtomicReference<Exception> error1 = new AtomicReference<>();
        AtomicReference<Exception> error2 = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                result1.set(instance1.add(key));
            } catch (Exception e) {
                error1.set(e);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                result2.set(instance2.add(key));
            } catch (Exception e) {
                error2.set(e);
            }
        });

        t1.start();
        t2.start();
        t1.join(10_000);
        t2.join(10_000);

        assertNull(error1.get(), () -> "Instance 1 threw: " + error1.get());
        assertNull(error2.get(), () -> "Instance 2 threw: " + error2.get());

        // At least one instance must acquire the lock
        assertTrue(result1.get() || result2.get(),
                "At least one instance should acquire the orphan lock");

        // CAMEL-23294: Both add() calls return true because:
        // 1. Both queryForInt() return 0 (orphan lock: row exists but createdAt is too old)
        // 2. Both insert() update the timestamp unconditionally (no guard on createdAt)
        // 3. add() ignores insert()'s return value — always returns true when queryForInt() was 0
        assertFalse(result1.get() && result2.get(),
                "CAMEL-23294: Both instances acquired the same orphan lock — only one should succeed");
    }
}

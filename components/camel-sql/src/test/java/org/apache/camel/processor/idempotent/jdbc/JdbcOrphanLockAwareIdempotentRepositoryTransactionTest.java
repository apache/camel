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

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.idempotent.jdbc.JdbcOrphanLockAwareIdempotentRepository.ProcessorNameAndMessageId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A key is kept alive only once the transaction that inserted or seized its row commits. In a transaction of the caller
 * that rolls back (for example a transacted route with removeOnFailure=false), the row is not there, and the node must
 * not keep the key alive: another node may insert it later, and this node would keep that node's lease alive.
 */
class JdbcOrphanLockAwareIdempotentRepositoryTransactionTest {

    private static final String PROCESSOR_NAME = "APP_1";
    private static final String KEY = "KEY";
    private static final long LOCK_MAX_AGE_MILLIS = 300_000L;
    private static final ProcessorNameAndMessageId ID = new ProcessorNameAndMessageId(PROCESSOR_NAME, KEY);

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private JdbcOrphanLockAwareIdempotentRepository repository;

    @BeforeEach
    void setup() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        repository = new JdbcOrphanLockAwareIdempotentRepository(database, PROCESSOR_NAME, new DefaultCamelContext());
        repository.setLockMaxAgeMillis(LOCK_MAX_AGE_MILLIS);
        // the keep-alive task does not run during the test
        repository.setLockKeepAliveIntervalMillis(LOCK_MAX_AGE_MILLIS / 2);
        repository.start();
    }

    @AfterEach
    void tearDown() {
        repository.shutdown();
        database.shutdown();
    }

    @Test
    void testAddInACallerTransactionThatRollsBackIsNotKeptAlive() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            status.setRollbackOnly();
        });

        assertEquals(0, rows());
        assertFalse(repository.getProcessorNameMessageIdSet().contains(ID));
    }

    @Test
    void testAddInACallerTransactionIsKeptAliveAfterTheCommit() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            assertFalse(repository.getProcessorNameMessageIdSet().contains(ID));
        });

        assertEquals(1, rows());
        assertTrue(repository.getProcessorNameMessageIdSet().contains(ID));
    }

    @Test
    void testAddIsKeptAlive() {
        assertTrue(repository.add(KEY));

        assertTrue(repository.getProcessorNameMessageIdSet().contains(ID));
    }

    @Test
    void testAddAndRemoveInOneCallerTransactionIsNotKeptAlive() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            assertTrue(repository.remove(KEY));
        });

        assertEquals(0, rows());
        assertFalse(repository.getProcessorNameMessageIdSet().contains(ID));
    }

    @Test
    void testAddAndClearInOneCallerTransactionIsNotKeptAlive() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            repository.clear();
        });

        assertEquals(0, rows());
        assertFalse(repository.getProcessorNameMessageIdSet().contains(ID));
    }

    @Test
    void testOrphanLockSeizedInACallerTransactionThatRollsBackIsNotKeptAlive() {
        Timestamp orphaned = new Timestamp(System.currentTimeMillis() - LOCK_MAX_AGE_MILLIS * 2);
        jdbcTemplate.update("INSERT INTO CAMEL_MESSAGEPROCESSED (processorName, messageId, createdAt) VALUES (?, ?, ?)",
                PROCESSOR_NAME, KEY, orphaned);

        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            status.setRollbackOnly();
        });

        assertEquals(orphaned, jdbcTemplate.queryForObject(
                "SELECT createdAt FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?",
                Timestamp.class, PROCESSOR_NAME, KEY));
        assertFalse(repository.getProcessorNameMessageIdSet().contains(ID));
    }

    @Test
    void testOrphanLockSeizedInACallerTransactionIsKeptAliveAfterTheCommit() {
        Timestamp orphaned = new Timestamp(System.currentTimeMillis() - LOCK_MAX_AGE_MILLIS * 2);
        jdbcTemplate.update("INSERT INTO CAMEL_MESSAGEPROCESSED (processorName, messageId, createdAt) VALUES (?, ?, ?)",
                PROCESSOR_NAME, KEY, orphaned);

        repository.transactionTemplate.executeWithoutResult(status -> assertTrue(repository.add(KEY)));

        assertTrue(repository.getProcessorNameMessageIdSet().contains(ID));
    }

    private int rows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ?",
                Integer.class, PROCESSOR_NAME);
    }
}

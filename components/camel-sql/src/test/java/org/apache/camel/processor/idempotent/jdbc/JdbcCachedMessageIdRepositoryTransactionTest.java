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
 * A key is cached only once the transaction that inserted its row commits. In a transaction of the caller that rolls
 * back (for example a transacted route), the row is gone, so the redelivery of the message must not be rejected as a
 * duplicate from the cache.
 */
public class JdbcCachedMessageIdRepositoryTransactionTest {

    private static final String PROCESSOR_NAME = "myProcessorName";
    private static final String KEY = "KEY";

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private JdbcCachedMessageIdRepository repository;

    @BeforeEach
    public void setUp() throws Exception {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcCachedMessageIdRepository(dataSource, PROCESSOR_NAME);
        repository.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (repository != null) {
            repository.stop();
        }
        if (dataSource != null) {
            dataSource.shutdown();
        }
    }

    @Test
    public void addInACallerTransactionThatRollsBackIsNotCached() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            status.setRollbackOnly();
        });

        assertEquals(0, rows());
        assertFalse(repository.contains(KEY), "the key of a rolled back add must not be a duplicate");
        // the redelivery of the message is processed
        assertTrue(repository.add(KEY));
        assertEquals(1, rows());
    }

    @Test
    public void addInACallerTransactionIsCachedAfterTheCommit() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            // not cached before the commit: contains reads the row
            int misses = repository.getMissCount();
            assertTrue(repository.contains(KEY));
            assertEquals(misses + 1, repository.getMissCount());
        });
        int hits = repository.getHitCount();

        assertEquals(1, rows());
        assertTrue(repository.contains(KEY));
        assertEquals(hits + 1, repository.getHitCount(), "the committed key must be answered from the cache");
    }

    @Test
    public void addIsCached() {
        assertTrue(repository.add(KEY));
        int hits = repository.getHitCount();

        assertFalse(repository.add(KEY));
        assertEquals(hits + 1, repository.getHitCount());
    }

    @Test
    public void addAndRemoveInOneCallerTransactionIsNotCached() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            assertTrue(repository.remove(KEY));
        });

        assertEquals(0, rows());
        assertFalse(repository.contains(KEY));
        assertTrue(repository.add(KEY));
    }

    @Test
    public void addAndClearInOneCallerTransactionIsNotCached() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            repository.clear();
        });

        assertEquals(0, rows());
        assertFalse(repository.contains(KEY));
        assertTrue(repository.add(KEY));
    }

    private int rows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ?",
                Integer.class, PROCESSOR_NAME);
    }
}

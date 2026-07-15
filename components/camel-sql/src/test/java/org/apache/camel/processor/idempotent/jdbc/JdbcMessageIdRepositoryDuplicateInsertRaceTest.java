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

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a concurrent duplicate insert in add() is treated as "already exists" (returns false) instead of
 * propagating a DuplicateKeyException.
 * <p>
 * The race is simulated by overriding queryForInt() to return 0 even when the key is already present, forcing the
 * INSERT to hit the primary-key constraint.
 */
public class JdbcMessageIdRepositoryDuplicateInsertRaceTest {

    private static final String PROCESSOR_NAME = "testProcessor";

    private EmbeddedDatabase dataSource;
    private JdbcMessageIdRepository repo;

    @BeforeEach
    void setUp() {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("idempotent-race-" + System.identityHashCode(this))
                .build();

        repo = new RaceSimulatingRepository(dataSource, PROCESSOR_NAME);
        repo.start();
    }

    @AfterEach
    void tearDown() {
        repo.stop();
        dataSource.shutdown();
    }

    @Test
    void testDuplicateInsertReturnsFalseInsteadOfThrowing() {
        // first add succeeds normally
        assertTrue(repo.add("key1"));

        // second add hits the PK constraint because queryForInt() is overridden to return 0;
        // before the fix this throws DuplicateKeyException; after the fix it returns false
        assertDoesNotThrow(() -> {
            boolean result = repo.add("key1");
            assertFalse(result, "add() should return false when a concurrent insert already inserted the key");
        });
    }

    /**
     * Subclass that overrides queryForInt() to always return 0 after the first successful add, simulating a concurrent
     * node that inserted the same key between the SELECT COUNT(*) and INSERT statements.
     */
    static class RaceSimulatingRepository extends JdbcMessageIdRepository {

        private volatile boolean simulateRace;

        RaceSimulatingRepository(DataSource dataSource, String processorName) {
            super(dataSource, processorName);
        }

        @Override
        protected int queryForInt(String key) {
            if (simulateRace) {
                return 0;
            }
            return super.queryForInt(key);
        }

        @Override
        protected int insert(String key) {
            // after the first successful insert, enable race simulation
            int result = super.insert(key);
            simulateRace = true;
            return result;
        }
    }
}

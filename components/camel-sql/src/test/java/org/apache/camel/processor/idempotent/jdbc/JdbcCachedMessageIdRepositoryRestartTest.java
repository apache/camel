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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stopping and starting the repository again keeps the query that reloads the cache for a custom table name.
 */
public class JdbcCachedMessageIdRepositoryRestartTest {

    private static final String TABLE_NAME = "CAMEL_MESSAGEPROCESSED_ORDERS";
    private static final String PROCESSOR_NAME = "APP_1";
    private static final int RESTARTS = 3;

    private EmbeddedDatabase dataSource;
    private JdbcCachedMessageIdRepository repository;

    @BeforeEach
    public void setup() {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        repository = new JdbcCachedMessageIdRepository(dataSource, PROCESSOR_NAME);
        repository.setTableName(TABLE_NAME);
    }

    @AfterEach
    public void tearDown() {
        repository.shutdown();
        dataSource.shutdown();
    }

    @Test
    public void testRestartKeepsQueryAllString() {
        String expected = "SELECT messageId, COUNT(*) FROM " + TABLE_NAME + " WHERE processorName = ? GROUP BY messageId";

        repository.start();
        assertEquals(expected, repository.getQueryAllString());

        for (int i = 0; i < RESTARTS; i++) {
            repository.stop();
            repository.start();
        }
        assertEquals(expected, repository.getQueryAllString());

        // a key added by another node while this one is stopped is in the cache after the next start
        repository.stop();
        new JdbcTemplate(dataSource).update(
                "INSERT INTO " + TABLE_NAME + " (processorName, messageId, createdAt) VALUES (?, ?, CURRENT_TIMESTAMP)",
                PROCESSOR_NAME, "FILE_1");
        repository.start();

        int hits = repository.getHitCount();
        assertTrue(repository.contains("FILE_1"));
        assertEquals(hits + 1, repository.getHitCount());
    }
}

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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultExecutorServiceManager;
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
 * Stopping and starting the repository again keeps the SQL for a custom table name and runs one keep-alive task.
 */
public class JdbcOrphanLockAwareIdempotentRepositoryRestartTest {

    private static final String TABLE_NAME = "CAMEL_MESSAGEPROCESSED_ORDERS";
    private static final String PROCESSOR_NAME = "APP_1";
    private static final long LOCK_MAX_AGE_MILLIS = 300_000L;
    private static final int RESTARTS = 3;

    private final List<ScheduledExecutorService> keepAliveExecutors = new CopyOnWriteArrayList<>();
    private EmbeddedDatabase dataSource;
    private DefaultCamelContext context;
    private JdbcOrphanLockAwareIdempotentRepository repository;

    @BeforeEach
    public void setup() {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();

        context = new DefaultCamelContext();
        context.setExecutorServiceManager(new DefaultExecutorServiceManager(context) {
            @Override
            public ScheduledExecutorService newSingleThreadScheduledExecutor(Object source, String name) {
                ScheduledExecutorService answer = super.newSingleThreadScheduledExecutor(source, name);
                keepAliveExecutors.add(answer);
                return answer;
            }
        });

        repository = new JdbcOrphanLockAwareIdempotentRepository(dataSource, PROCESSOR_NAME, context);
        repository.setTableName(TABLE_NAME);
        repository.setLockMaxAgeMillis(LOCK_MAX_AGE_MILLIS);
        // the test calls keepAlive() itself
        repository.setLockKeepAliveIntervalMillis(LOCK_MAX_AGE_MILLIS / 2);
    }

    @AfterEach
    public void tearDown() {
        repository.shutdown();
        context.stop();
        dataSource.shutdown();
    }

    @Test
    public void testRestartKeepsUpdateTimestampQuery() {
        String expected = "UPDATE " + TABLE_NAME + " SET createdAt =? WHERE processorName =? AND messageId = ?";

        repository.start();
        assertEquals(expected, repository.getUpdateTimestampQuery());

        for (int i = 0; i < RESTARTS; i++) {
            repository.stop();
            repository.start();
        }
        assertEquals(expected, repository.getUpdateTimestampQuery());

        // the keep-alive still refreshes a lease after the restarts
        assertTrue(repository.add("FILE_1"));
        Timestamp expired = new Timestamp(System.currentTimeMillis() - LOCK_MAX_AGE_MILLIS - 60_000L);
        new JdbcTemplate(dataSource).update("UPDATE " + TABLE_NAME + " SET createdAt = ? WHERE messageId = ?", expired,
                "FILE_1");
        assertFalse(repository.contains("FILE_1"));

        repository.keepAlive();
        assertTrue(repository.contains("FILE_1"));
    }

    @Test
    public void testRestartUsesOneKeepAliveExecutor() {
        repository.start();
        assertEquals(1, keepAliveExecutors.size());

        for (int i = 0; i < RESTARTS; i++) {
            repository.stop();
            assertTrue(keepAliveExecutors.stream().allMatch(ExecutorService::isShutdown),
                    "The keep-alive executor should be shut down when the repository is stopped");
            repository.start();
        }

        assertEquals(RESTARTS + 1, keepAliveExecutors.size());
        assertEquals(1, keepAliveExecutors.stream().filter(e -> !e.isShutdown()).count());

        repository.shutdown();
        assertTrue(keepAliveExecutors.stream().allMatch(ExecutorService::isShutdown));
    }
}

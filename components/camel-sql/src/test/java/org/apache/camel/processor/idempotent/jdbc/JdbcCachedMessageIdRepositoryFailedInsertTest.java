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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the database insert fails, {@link JdbcCachedMessageIdRepository#add(String)} propagates the exception and the
 * idempotent consumer EIP never processes the message. The failed key must therefore not be remembered as processed: a
 * redelivery of the same message must be processable once the database is available again.
 */
public class JdbcCachedMessageIdRepositoryFailedInsertTest {

    private static final String PROCESSOR_NAME = "myProcessorName";

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
    public void failedInsertMustNotMarkMessageAsProcessed() {
        // sanity: normal add works and duplicates are detected
        assertTrue(repository.add("first"));
        assertFalse(repository.add("first"));

        // simulate a database outage while adding a new key
        jdbcTemplate.execute("DROP TABLE CAMEL_MESSAGEPROCESSED");
        assertThrows(Exception.class, () -> repository.add("second"));

        // database is back
        jdbcTemplate.execute(repository.getCreateString());

        // the failed add must not have marked "second" as processed: the message was
        // never processed and nothing was stored in the database
        assertFalse(repository.contains("second"),
                "a key whose insert failed must not be reported as already processed");
        assertTrue(repository.add("second"),
                "redelivery of a message whose insert failed must be processable");
    }
}

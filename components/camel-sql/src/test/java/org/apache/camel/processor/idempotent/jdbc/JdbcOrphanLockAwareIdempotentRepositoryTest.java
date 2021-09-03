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
import java.util.concurrent.TimeUnit;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.idempotent.jdbc.JdbcOrphanLockAwareIdempotentRepository.ProcessorNameAndMessageId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
public class JdbcOrphanLockAwareIdempotentRepositoryTest {

    private static final String APP_NAME = "APP_1";

    private EmbeddedDatabase dataSource;

    private JdbcOrphanLockAwareIdempotentRepository jdbcMessageIdRepository;

    @BeforeAll
    public void setup() throws Exception {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.HSQL)
                .addScript("classpath:sql/idempotentWithOrphanLockRemoval.sql")
                .generateUniqueName(true)
                .build();
        jdbcMessageIdRepository = new JdbcOrphanLockAwareIdempotentRepository(dataSource, APP_NAME, new DefaultCamelContext());
        jdbcMessageIdRepository.setLockMaxAgeMillis(3000_00L);
        jdbcMessageIdRepository.setLockKeepAliveIntervalMillis(3000L);
        jdbcMessageIdRepository.doInit();
    }

    @Test
    public void testLockNotGrantedForCurrentTimeStamp() {
        assertTrue(jdbcMessageIdRepository.contains("FILE_1"));
    }

    @Test
    public void testLockNotGrantedForCurrentTimeStampPlus2Min() {
        assertTrue(jdbcMessageIdRepository.contains("FILE_2"));
    }

    @Test
    public void testLockGrantedForCurrentTimeStampPlus5Min() {
        assertFalse(jdbcMessageIdRepository.contains("FILE_3"));
    }

    @Test
    public void testLockKeepAliveWorks() {
        assertFalse(jdbcMessageIdRepository.contains("FILE_4"));
        jdbcMessageIdRepository.insert("FILE_4");
        assertTrue(jdbcMessageIdRepository.contains("FILE_4"));
        JdbcTemplate template = new JdbcTemplate(dataSource);
        Timestamp timestamp = new Timestamp(System.currentTimeMillis() - 5 * 60 * 1000L);
        template.update("UPDATE CAMEL_MESSAGEPROCESSED SET createdAT = ? WHERE processorName = ? AND messageId = ?", timestamp,
                APP_NAME, "FILE_4");

        await().atMost(5, TimeUnit.SECONDS).until(() -> !jdbcMessageIdRepository.contains("FILE_4"));
        jdbcMessageIdRepository.keepAlive();
        assertTrue(jdbcMessageIdRepository.contains("FILE_4"));
    }

    @Test
    public void testInsertQueryDelete() {
        assertFalse(jdbcMessageIdRepository.contains("FILE_5"));
        assertFalse(jdbcMessageIdRepository.getProcessorNameMessageIdSet()
                .contains(new ProcessorNameAndMessageId(APP_NAME, "FILE_5")));

        jdbcMessageIdRepository.add("FILE_5");

        assertTrue(jdbcMessageIdRepository.getProcessorNameMessageIdSet()
                .contains(new ProcessorNameAndMessageId(APP_NAME, "FILE_5")));
        assertTrue(jdbcMessageIdRepository.contains("FILE_5"));
        jdbcMessageIdRepository.remove("FILE_5");
        assertFalse(jdbcMessageIdRepository.contains("FILE_5"));
        assertFalse(jdbcMessageIdRepository.getProcessorNameMessageIdSet()
                .contains(new ProcessorNameAndMessageId(APP_NAME, "FILE_5")));
    }

}

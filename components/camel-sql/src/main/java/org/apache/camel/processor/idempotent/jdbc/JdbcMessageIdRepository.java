/**
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
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default implementation of {@link AbstractJdbcMessageIdRepository}
 */
public class JdbcMessageIdRepository extends AbstractJdbcMessageIdRepository<String> {

    public static final String QUERY_STRING = "SELECT COUNT(*) FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?";
    public static final String INSERT_STRING = "INSERT INTO CAMEL_MESSAGEPROCESSED (processorName, messageId, createdAt) VALUES (?, ?, ?)";
    public static final String DELETE_STRING = "DELETE FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?";

    public JdbcMessageIdRepository() {
        super();
    }

    public JdbcMessageIdRepository(DataSource dataSource, String processorName) {
        super(dataSource, processorName);
    }

    public JdbcMessageIdRepository(DataSource dataSource, TransactionTemplate transactionTemplate, String processorName) {
        super(dataSource, transactionTemplate, processorName);
    }

    public JdbcMessageIdRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        super(jdbcTemplate, transactionTemplate);
    }

    @Override
    protected int queryForInt(String key) {
        return jdbcTemplate.queryForInt(QUERY_STRING, processorName, key);
    }

    @Override
    protected int insert(String key) {
        return jdbcTemplate.update(INSERT_STRING, processorName, key, new Timestamp(System.currentTimeMillis()));
    }

    @Override
    protected int delete(String key) {
        return jdbcTemplate.update(DELETE_STRING, processorName, key);
    }

}
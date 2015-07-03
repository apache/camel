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

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default implementation of {@link AbstractJdbcMessageIdRepository}
 */
public class JdbcMessageIdRepository extends AbstractJdbcMessageIdRepository<String> {

    private boolean createTableIfNotExists = true;
    private String tableExistsString = "SELECT 1 FROM CAMEL_MESSAGEPROCESSED WHERE 1 = 0";
    private String createString = "CREATE TABLE CAMEL_MESSAGEPROCESSED (processorName VARCHAR(255), messageId VARCHAR(100), createdAt TIMESTAMP)";
    private String queryString = "SELECT COUNT(*) FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?";
    private String insertString = "INSERT INTO CAMEL_MESSAGEPROCESSED (processorName, messageId, createdAt) VALUES (?, ?, ?)";
    private String deleteString = "DELETE FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?";
    private String clearString = "DELETE FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ?";

    public JdbcMessageIdRepository() {
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
    protected void doStart() throws Exception {
        super.doStart();
        
        transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    // we will receive an exception if the table doesn't exists or we cannot access it
                    jdbcTemplate.execute(tableExistsString);
                    log.debug("Expected table for JdbcMessageIdRepository exist");
                } catch (DataAccessException e) {
                    if (createTableIfNotExists) {
                        try {
                            log.debug("creating table for JdbcMessageIdRepository because it doesn't exist...");
                            jdbcTemplate.execute(createString);
                            log.info("table created with query '{}'", createString);
                        } catch (DataAccessException dae) {
                            // we will fail if we cannot create it
                            log.error("Can't create table for JdbcMessageIdRepository with query '{}' because of: {}. This may be a permissions problem. Please create this table and try again.",
                                    createString, e.getMessage());
                            throw dae;
                        }
                    } else {
                        throw e;
                    }

                }
                return Boolean.TRUE;
            }
        });   
    }

    @Override
    protected int queryForInt(String key) {
        return jdbcTemplate.queryForObject(queryString, Integer.class, processorName, key);
    }

    @Override
    protected int insert(String key) {
        return jdbcTemplate.update(insertString, processorName, key, new Timestamp(System.currentTimeMillis()));
    }

    @Override
    protected int delete(String key) {
        return jdbcTemplate.update(deleteString, processorName, key);
    }
    
    @Override
    protected int delete() {
        return jdbcTemplate.update(clearString, processorName);
    }

    public boolean isCreateTableIfNotExists() {
        return createTableIfNotExists;
    }

    public void setCreateTableIfNotExists(boolean createTableIfNotExists) {
        this.createTableIfNotExists = createTableIfNotExists;
    }

    public String getTableExistsString() {
        return tableExistsString;
    }

    public void setTableExistsString(String tableExistsString) {
        this.tableExistsString = tableExistsString;
    }
    
    public String getCreateString() {
        return createString;
    }

    public void setCreateString(String createString) {
        this.createString = createString;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public String getInsertString() {
        return insertString;
    }

    public void setInsertString(String insertString) {
        this.insertString = insertString;
    }

    public String getDeleteString() {
        return deleteString;
    }

    public void setDeleteString(String deleteString) {
        this.deleteString = deleteString;
    }
}
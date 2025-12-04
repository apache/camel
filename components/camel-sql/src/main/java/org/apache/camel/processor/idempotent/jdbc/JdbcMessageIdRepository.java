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

import javax.sql.DataSource;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default implementation of {@link AbstractJdbcMessageIdRepository}
 */
@Metadata(
        label = "bean",
        description = "Idempotent repository that uses a SQL database to store message ids.",
        annotations = {"interfaceName=org.apache.camel.spi.IdempotentRepository"})
@Configurer(metadataOnly = true)
public class JdbcMessageIdRepository extends AbstractJdbcMessageIdRepository {

    protected static final String DEFAULT_TABLENAME = "CAMEL_MESSAGEPROCESSED";
    protected static final String DEFAULT_TABLE_EXISTS_STRING = "SELECT 1 FROM CAMEL_MESSAGEPROCESSED WHERE 1 = 0";
    protected static final String DEFAULT_CREATE_STRING =
            "CREATE TABLE CAMEL_MESSAGEPROCESSED (processorName VARCHAR(255), messageId VARCHAR(100), "
                    + "createdAt TIMESTAMP, PRIMARY KEY (processorName, messageId))";
    protected static final String DEFAULT_QUERY_STRING =
            "SELECT COUNT(*) FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?";
    protected static final String DEFAULT_INSERT_STRING =
            "INSERT INTO CAMEL_MESSAGEPROCESSED (processorName, messageId, createdAt) VALUES (?, ?, ?)";
    protected static final String DEFAULT_DELETE_STRING =
            "DELETE FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?";
    protected static final String DEFAULT_CLEAR_STRING = "DELETE FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ?";

    @Metadata(description = "The name of the table to use in the database", defaultValue = "CAMEL_MESSAGEPROCESSED")
    private String tableName;

    @Metadata(
            description = "Whether to create the table in the database if none exists on startup",
            defaultValue = "true")
    private boolean createTableIfNotExists = true;

    @Metadata(label = "advanced", description = "SQL query to use for checking if table exists")
    private String tableExistsString = DEFAULT_TABLE_EXISTS_STRING;

    @Metadata(label = "advanced", description = "SQL query to use for creating table")
    private String createString = DEFAULT_CREATE_STRING;

    @Metadata(label = "advanced", description = "SQL query to use for check if message id already exists")
    private String queryString = DEFAULT_QUERY_STRING;

    @Metadata(label = "advanced", description = "SQL query to use for inserting a new message id in the table")
    private String insertString = DEFAULT_INSERT_STRING;

    @Metadata(label = "advanced", description = "SQL query to use for deleting message id from the table")
    private String deleteString = DEFAULT_DELETE_STRING;

    @Metadata(label = "advanced", description = "SQL query to delete all message ids from the table")
    private String clearString = DEFAULT_CLEAR_STRING;

    public JdbcMessageIdRepository() {}

    public JdbcMessageIdRepository(DataSource dataSource, String processorName) {
        super(dataSource, processorName);
    }

    public JdbcMessageIdRepository(
            DataSource dataSource, TransactionTemplate transactionTemplate, String processorName) {
        super(dataSource, transactionTemplate, processorName);
    }

    public JdbcMessageIdRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        super(jdbcTemplate, transactionTemplate);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (tableName != null) {
            // update query strings from default table name to the new table name
            tableExistsString = DEFAULT_TABLE_EXISTS_STRING.replace(DEFAULT_TABLENAME, tableName);
            createString = DEFAULT_CREATE_STRING.replace(DEFAULT_TABLENAME, tableName);
            queryString = DEFAULT_QUERY_STRING.replace(DEFAULT_TABLENAME, tableName);
            insertString = DEFAULT_INSERT_STRING.replace(DEFAULT_TABLENAME, tableName);
            deleteString = DEFAULT_DELETE_STRING.replace(DEFAULT_TABLENAME, tableName);
            clearString = DEFAULT_CLEAR_STRING.replace(DEFAULT_TABLENAME, tableName);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        boolean tableExists = transactionTemplate.execute(status -> {
            try {
                // we will receive an exception if the table doesn't exists or we cannot access it
                jdbcTemplate.execute(getTableExistsString());
                log.debug("Expected table for JdbcMessageIdRepository exist");

                return true;
            } catch (DataAccessException e) {
                log.debug("Expected table for JdbcMessageIdRepository does not exist");
                return false;
            }
        });

        if (!tableExists && createTableIfNotExists) {
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    log.debug("creating table for JdbcMessageIdRepository because it doesn't exist...");
                    jdbcTemplate.execute(getCreateString());
                    log.info("table created with query '{}'", getCreateString());
                } catch (DataAccessException dae) {
                    // we will fail if we cannot create it
                    log.error(
                            "Can't create table for JdbcMessageIdRepository with query '{}' because of: {}. This may be a permissions problem. Please create this table and try again.",
                            getCreateString(),
                            dae.getMessage());
                    throw dae;
                }
            });
        }
    }

    @Override
    protected int queryForInt(String key) {
        return jdbcTemplate.queryForObject(getQueryString(), Integer.class, processorName, key);
    }

    @Override
    protected int insert(String key) {
        return jdbcTemplate.update(getInsertString(), processorName, key, new Timestamp(System.currentTimeMillis()));
    }

    @Override
    protected int delete(String key) {
        return jdbcTemplate.update(getDeleteString(), processorName, key);
    }

    @Override
    protected int delete() {
        return jdbcTemplate.update(getClearString(), processorName);
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

    public String getTableName() {
        return tableName;
    }

    /**
     * To use a custom table name instead of the default name: CAMEL_MESSAGEPROCESSED
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
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

    public String getClearString() {
        return clearString;
    }

    public void setClearString(String clearString) {
        this.clearString = clearString;
    }
}

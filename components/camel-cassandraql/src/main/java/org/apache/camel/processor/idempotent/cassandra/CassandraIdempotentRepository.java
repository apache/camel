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
package org.apache.camel.processor.idempotent.cassandra;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.api.querybuilder.truncate.Truncate;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.utils.cassandra.CassandraSessionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.utils.cassandra.CassandraUtils.append;
import static org.apache.camel.utils.cassandra.CassandraUtils.applyConsistencyLevel;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateDelete;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateInsert;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateSelect;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateTruncate;

/**
 * Implementation of {@link IdempotentRepository} using Cassandra table to store message ids. Advice: use
 * LeveledCompaction for this table and tune read/write consistency levels. Warning: Cassandra is not the best tool for
 * queuing use cases See http://www.datastax.com/dev/blog/cassandra-anti-patterns-queues-and-queue-like-datasets
 */
public class CassandraIdempotentRepository extends ServiceSupport implements IdempotentRepository {
    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraIdempotentRepository.class);
    /**
     * Session holder
     */
    private CassandraSessionHolder sessionHolder;
    /**
     * Table name
     */
    private String table = "CAMEL_IDEMPOTENT";
    /**
     * Values used as primary key prefix
     */
    private String[] prefixPKValues = new String[0];
    /**
     * Primary key columns
     */
    private String[] pkColumns = { "KEY" };
    /**
     * Time to live in seconds used for inserts
     */
    private Integer ttl;
    /**
     * Write consistency level
     */
    private ConsistencyLevel writeConsistencyLevel;
    /**
     * Read consistency level
     */
    private ConsistencyLevel readConsistencyLevel;
    private PreparedStatement insertStatement;
    private PreparedStatement selectStatement;
    private PreparedStatement deleteStatement;
    private PreparedStatement truncateStatement;

    public CassandraIdempotentRepository() {
    }

    public CassandraIdempotentRepository(CqlSession session) {
        this.sessionHolder = new CassandraSessionHolder(session);
    }

    private boolean isKey(ResultSet resultSet) {
        Row row = resultSet.one();
        if (row == null) {
            LOGGER.debug("No row to check key");
            return false;
        } else {
            LOGGER.debug("Row with {} columns to check key", row.getColumnDefinitions());
            return row.getColumnDefinitions().size() >= pkColumns.length;
        }
    }

    protected final boolean isApplied(ResultSet resultSet) {
        Row row = resultSet.one();
        return row == null || row.getBoolean("[applied]");
    }

    protected Object[] getPKValues(String key) {
        return append(prefixPKValues, key);
    }
    // -------------------------------------------------------------------------
    // Lifecycle methods

    @Override
    protected void doStart() throws Exception {
        sessionHolder.start();
        initInsertStatement();
        initSelectStatement();
        initDeleteStatement();
        initClearStatement();
    }

    @Override
    protected void doStop() throws Exception {
        sessionHolder.stop();
    }
    // -------------------------------------------------------------------------
    // Add key to repository

    protected void initInsertStatement() {
        Insert insert = generateInsert(table, pkColumns, true, ttl);
        SimpleStatement statement = applyConsistencyLevel(insert.build(), writeConsistencyLevel);
        LOGGER.debug("Generated Insert {}", statement);
        insertStatement = getSession().prepare(statement);
    }

    @Override
    public boolean add(String key) {
        Object[] idValues = getPKValues(key);
        LOGGER.debug("Inserting key {}", (Object) idValues);
        return isApplied(getSession().execute(insertStatement.bind(idValues)));
    }

    // -------------------------------------------------------------------------
    // Check if key is in repository

    protected void initSelectStatement() {
        Select select = generateSelect(table, pkColumns, pkColumns);
        SimpleStatement statement = applyConsistencyLevel(select.build(), readConsistencyLevel);
        LOGGER.debug("Generated Select {}", statement);
        selectStatement = getSession().prepare(statement);
    }

    @Override
    public boolean contains(String key) {
        Object[] idValues = getPKValues(key);
        LOGGER.debug("Checking key {}", (Object) idValues);
        return isKey(getSession().execute(selectStatement.bind(idValues)));
    }

    @Override
    public boolean confirm(String key) {
        return true;
    }

    // -------------------------------------------------------------------------
    // Remove key from repository

    protected void initDeleteStatement() {
        Delete delete = generateDelete(table, pkColumns, true);
        SimpleStatement statement = applyConsistencyLevel(delete.build(), writeConsistencyLevel);
        LOGGER.debug("Generated Delete {}", statement);
        deleteStatement = getSession().prepare(statement);
    }

    @Override
    public boolean remove(String key) {
        Object[] idValues = getPKValues(key);
        LOGGER.debug("Deleting key {}", (Object) idValues);
        return isApplied(getSession().execute(deleteStatement.bind(idValues)));
    }

    // -------------------------------------------------------------------------
    // Clear the repository

    protected void initClearStatement() {
        Truncate truncate = generateTruncate(table);
        SimpleStatement statement = applyConsistencyLevel(truncate.build(), writeConsistencyLevel);
        LOGGER.debug("Generated truncate for clear operation {}", statement);
        truncateStatement = getSession().prepare(statement);
    }

    @Override
    public void clear() {
        LOGGER.debug("Clear table {}", table);
        getSession().execute(truncateStatement.bind());
    }

    // -------------------------------------------------------------------------
    // Getters & Setters

    public CqlSession getSession() {
        return sessionHolder.getSession();
    }

    public void setSession(CqlSession session) {
        this.sessionHolder = new CassandraSessionHolder(session);
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String[] getPKColumns() {
        return pkColumns;
    }

    public void setPKColumns(String... pkColumns) {
        this.pkColumns = pkColumns;
    }

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public ConsistencyLevel getWriteConsistencyLevel() {
        return writeConsistencyLevel;
    }

    public void setWriteConsistencyLevel(ConsistencyLevel writeConsistencyLevel) {
        this.writeConsistencyLevel = writeConsistencyLevel;
    }

    public ConsistencyLevel getReadConsistencyLevel() {
        return readConsistencyLevel;
    }

    public void setReadConsistencyLevel(ConsistencyLevel readConsistencyLevel) {
        this.readConsistencyLevel = readConsistencyLevel;
    }

    public String[] getPrefixPKValues() {
        return prefixPKValues;
    }

    public void setPrefixPKValues(String[] prefixPKValues) {
        this.prefixPKValues = prefixPKValues;
    }

}

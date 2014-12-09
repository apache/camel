/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor.idempotent.cassandraql;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link IdempotentRepository} using Cassandra table to store
 * message ids.
 * Warning: Cassandra is not the best tool for queuing use cases
 * @see http://www.datastax.com/dev/blog/cassandra-anti-patterns-queues-and-queue-like-datasets
 * @param <T> Repository Id
 * @param <K> Message Id
 */
public class CassandraQlIdempotentRepository<T,K> extends ServiceSupport implements IdempotentRepository<K> {
    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraQlIdempotentRepository.class);
    /**
     * Session with keyspace
     */
    private Session session;
    /**
     * Table name
     */
    private String tableName = "CAMEL_IDEMPOTENT";
    /**
     * Partition + cluster key column names
     */
    private String[] idColumnNames = new String[]{"ID", "KEY"};
    /**
     * Partition key
     */
    private T id;
    /**
     * Time to live in seconds used for inserts
     */
    private Integer ttl;
    
    private PreparedStatement insertStatement;
    private PreparedStatement selectStatement;
    private PreparedStatement deleteStatement;

    public CassandraQlIdempotentRepository() {
    }

    public CassandraQlIdempotentRepository(Session session, T id) {
        this.session = session;
        this.id = id;
    }

    private boolean isKey(ResultSet resultSet) {
        Row row = resultSet.one(); 
        if (row==null) {
            LOGGER.debug("No row to check key");
            return false;
        } else {
            LOGGER.debug("Row with {} columns to check key", row.getColumnDefinitions());            
            return row.getColumnDefinitions().size()>1;
        }
    }
    /**
     * Generate partition+cluster key.
     * Override this method to customize primary key generation.
     * @param key Message key
     * @return Partition+cluster key
     */
    protected Object[] getIdValues(T id, K key) {
        return new Object[]{id, key};
    }

    protected final void appendIdColumnNames(StringBuilder cqlBuilder, String sep) {
        for (int i = 0; i < idColumnNames.length; i++) {
            if (i > 0) {
                cqlBuilder.append(sep);
            }
            cqlBuilder.append(idColumnNames[i]);
        }
    }

    protected final void appendWhereId(StringBuilder cqlBuilder) {
        cqlBuilder.append(" where ");
        appendIdColumnNames(cqlBuilder, "=? and ");
        cqlBuilder.append("=?");
    }

    // -------------------------------------------------------------------------
    // Lifecycle methods

    @Override
    protected void doStart() throws Exception {
        initInsertStatement();
        initSelectStatement();
        initDeleteStatement();
    }

    @Override
    protected void doStop() throws Exception {
    }
    // -------------------------------------------------------------------------
    // Add key to repository

    protected String createInsertCql() {
        StringBuilder cqlBuilder = new StringBuilder("insert into ")
                .append(tableName).append("(");
        appendIdColumnNames(cqlBuilder, ",");
        cqlBuilder.append(") values (");
        for (int i = 0; i < idColumnNames.length; i++) {
            if (i > 0) {
                cqlBuilder.append(",");
            }
            cqlBuilder.append("?");
        }
        cqlBuilder.append(") if not exists");
        if (ttl!=null) {
            cqlBuilder.append(" using ttl=").append(ttl);
        }
        final String cql = cqlBuilder.toString();
        LOGGER.debug("Generated Insert {}", cql);
        return cql;
    }

    protected void initInsertStatement() {
        insertStatement = session
                .prepare(createInsertCql())
                .setConsistencyLevel(ConsistencyLevel.LOCAL_ONE);
    }

    @Override
    public boolean add(K key) {
        final Object[] idValues = getIdValues(this.id, key);
        LOGGER.debug("Inserting key {}", (Object) idValues);
        return !isKey(session.execute(insertStatement.bind(idValues)));
    }

    // -------------------------------------------------------------------------
    // Check if key is in repository
    protected String createSelectCql() {
        StringBuilder cqlBuilder = new StringBuilder("select ");
        appendIdColumnNames(cqlBuilder, ",");
        cqlBuilder.append(" from ").append(tableName);
        appendWhereId(cqlBuilder);
        final String cql = cqlBuilder.toString();
        LOGGER.debug("Generated Select {}", cql);
        return cql;
    }

    protected void initSelectStatement() {
        selectStatement = session
                .prepare(createSelectCql())
                .setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
    }

    @Override
    public boolean contains(K key) {
        final Object[] idValues = getIdValues(this.id, key);
        LOGGER.debug("Checking key {}", (Object) idValues);
        return isKey(session.execute(selectStatement.bind(idValues)));
    }

    @Override
    public boolean confirm(K key) {
        return contains(key);
    }

    // -------------------------------------------------------------------------
    // Remove key from repository
    protected String createDeleteCql() {
        StringBuilder cqlBuilder = new StringBuilder("delete from ").append(tableName);
        appendWhereId(cqlBuilder);
        cqlBuilder.append(" if exists");
        final String cql = cqlBuilder.toString();
        LOGGER.debug("Generated Delete {}", cql);
        return cql;
    }

    protected void initDeleteStatement() {
        deleteStatement = session
                .prepare(createDeleteCql())
                .setConsistencyLevel(ConsistencyLevel.LOCAL_ONE);
    }

    @Override
    public boolean remove(K key) {
        final Object[] idValues = getIdValues(this.id, key);
        LOGGER.debug("Deleting key {}", (Object) idValues);
        session.execute(deleteStatement.bind(idValues));
        return true;
    }
    // -------------------------------------------------------------------------
    // Getters & Setters

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String[] getIdColumnNames() {
        return idColumnNames;
    }

    public void setIdColumnNames(String... idColumnNames) {
        this.idColumnNames = idColumnNames;
    }

    public T getId() {
        return id;
    }

    public void setId(T id) {
        this.id = id;
    }

}

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
package org.apache.camel.processor.keyvalue.cassandra;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.api.querybuilder.truncate.Truncate;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.KeyValueRepositoryHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.utils.cassandra.CassandraSessionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static org.apache.camel.utils.cassandra.CassandraUtils.applyConsistencyLevel;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateDelete;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateSelect;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateTruncate;

/**
 * A Cassandra-based implementation of {@link KeyValueRepository} that stores key-value entries in a Cassandra table.
 * <p/>
 * Values are serialized to bytes using Java {@link ObjectOutputStream} and stored in a {@code BLOB} column. Keys are
 * stored as {@code TEXT}. Time-to-live is handled natively by Cassandra's {@code USING TTL} clause on {@code INSERT}
 * statements, so expired entries are removed automatically by Cassandra without any client-side eviction logic.
 * <p/>
 * The {@link #putIfAbsent(String, Object, long)} method is implemented atomically using Cassandra's lightweight
 * transactions ({@code INSERT ... IF NOT EXISTS}).
 * <p/>
 * Advice: use LeveledCompaction for the backing table and tune read/write consistency levels for your use case.
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "A Cassandra-based KeyValueRepository that uses a Cassandra table to store key-value entries."
                        + " Advice: use LeveledCompaction for this table and tune read/write consistency levels.",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
@ManagedResource(description = "Cassandra based key-value repository")
public class CassandraKeyValueRepository extends ServiceSupport implements KeyValueRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraKeyValueRepository.class);

    private static final String KEY_COLUMN = "KEY";
    private static final String VALUE_COLUMN = "VALUE";

    @Metadata(description = "Cassandra session", required = true)
    private CassandraSessionHolder session;
    @Metadata(description = "The table name for storing the data", defaultValue = "CAMEL_KEYVALUE")
    private String table = "CAMEL_KEYVALUE";
    @Metadata(description = "Write consistency level",
              enums = "ANY,ONE,TWO,THREE,QUORUM,ALL,LOCAL_ONE,LOCAL_QUORUM,EACH_QUORUM,SERIAL,LOCAL_SERIAL")
    private ConsistencyLevel writeConsistencyLevel;
    @Metadata(description = "Read consistency level",
              enums = "ANY,ONE,TWO,THREE,QUORUM,ALL,LOCAL_ONE,LOCAL_QUORUM,EACH_QUORUM,SERIAL,LOCAL_SERIAL")
    private ConsistencyLevel readConsistencyLevel;

    private PreparedStatement insertStatement;
    private PreparedStatement insertWithTtlStatement;
    private PreparedStatement selectStatement;
    private PreparedStatement deleteStatement;
    private PreparedStatement selectAllKeysStatement;
    private PreparedStatement truncateStatement;
    private PreparedStatement insertIfNotExistsStatement;
    private PreparedStatement insertIfNotExistsWithTtlStatement;

    public CassandraKeyValueRepository() {
    }

    public CassandraKeyValueRepository(CqlSession session) {
        this.session = new CassandraSessionHolder(session);
    }

    // -------------------------------------------------------------------------
    // Helper methods

    /**
     * Checks whether a lightweight transaction was applied.
     *
     * @param  resultSet the result set from a conditional statement
     * @return           {@code true} if the statement was applied or the result is empty
     */
    protected final boolean isApplied(ResultSet resultSet) {
        Row row = resultSet.one();
        return row == null || row.getBoolean("[applied]");
    }

    // -------------------------------------------------------------------------
    // Lifecycle methods

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(session, "session", this);
        session.start();
        initInsertStatement();
        initInsertWithTtlStatement();
        initSelectStatement();
        initDeleteStatement();
        initSelectAllKeysStatement();
        initClearStatement();
        initInsertIfNotExistsStatement();
        initInsertIfNotExistsWithTtlStatement();
    }

    @Override
    protected void doStop() throws Exception {
        if (session != null) {
            session.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Prepared statement initialization

    protected void initInsertStatement() {
        SimpleStatement statement = applyConsistencyLevel(
                insertInto(table)
                        .value(KEY_COLUMN, bindMarker())
                        .value(VALUE_COLUMN, bindMarker())
                        .build(),
                writeConsistencyLevel);
        LOGGER.debug("Generated Insert {}", statement);
        insertStatement = getSession().prepare(statement);
    }

    protected void initInsertWithTtlStatement() {
        SimpleStatement statement = applyConsistencyLevel(
                insertInto(table)
                        .value(KEY_COLUMN, bindMarker())
                        .value(VALUE_COLUMN, bindMarker())
                        .usingTtl(bindMarker())
                        .build(),
                writeConsistencyLevel);
        LOGGER.debug("Generated Insert with TTL {}", statement);
        insertWithTtlStatement = getSession().prepare(statement);
    }

    protected void initSelectStatement() {
        Select select = generateSelect(table, new String[] { VALUE_COLUMN }, new String[] { KEY_COLUMN });
        SimpleStatement statement = applyConsistencyLevel(select.build(), readConsistencyLevel);
        LOGGER.debug("Generated Select {}", statement);
        selectStatement = getSession().prepare(statement);
    }

    protected void initDeleteStatement() {
        Delete delete = generateDelete(table, new String[] { KEY_COLUMN }, true);
        SimpleStatement statement = applyConsistencyLevel(delete.build(), writeConsistencyLevel);
        LOGGER.debug("Generated Delete {}", statement);
        deleteStatement = getSession().prepare(statement);
    }

    protected void initSelectAllKeysStatement() {
        Select select = generateSelect(table, new String[] { KEY_COLUMN }, null);
        SimpleStatement statement = applyConsistencyLevel(select.build(), readConsistencyLevel);
        LOGGER.debug("Generated Select all keys {}", statement);
        selectAllKeysStatement = getSession().prepare(statement);
    }

    protected void initClearStatement() {
        Truncate truncate = generateTruncate(table);
        SimpleStatement statement = applyConsistencyLevel(truncate.build(), writeConsistencyLevel);
        LOGGER.debug("Generated truncate for clear operation {}", statement);
        truncateStatement = getSession().prepare(statement);
    }

    protected void initInsertIfNotExistsStatement() {
        SimpleStatement statement = applyConsistencyLevel(
                insertInto(table)
                        .value(KEY_COLUMN, bindMarker())
                        .value(VALUE_COLUMN, bindMarker())
                        .ifNotExists()
                        .build(),
                writeConsistencyLevel);
        LOGGER.debug("Generated Insert if not exists {}", statement);
        insertIfNotExistsStatement = getSession().prepare(statement);
    }

    protected void initInsertIfNotExistsWithTtlStatement() {
        SimpleStatement statement = applyConsistencyLevel(
                insertInto(table)
                        .value(KEY_COLUMN, bindMarker())
                        .value(VALUE_COLUMN, bindMarker())
                        .ifNotExists()
                        .usingTtl(bindMarker())
                        .build(),
                writeConsistencyLevel);
        LOGGER.debug("Generated Insert if not exists with TTL {}", statement);
        insertIfNotExistsWithTtlStatement = getSession().prepare(statement);
    }

    // -------------------------------------------------------------------------
    // KeyValueRepository operations

    @Override
    @ManagedOperation(description = "Get value by key")
    public Object get(String key) {
        LOGGER.debug("Getting key {}", key);
        ResultSet rs = getSession().execute(selectStatement.bind(key));
        Row row = rs.one();
        if (row == null) {
            return null;
        }
        ByteBuffer buffer = row.getByteBuffer(VALUE_COLUMN);
        return buffer != null ? KeyValueRepositoryHelper.deserialize(buffer) : null;
    }

    @Override
    @ManagedOperation(description = "Put a key-value pair with optional TTL")
    public Object put(String key, Object value, Duration ttl) {
        LOGGER.debug("Putting key {} with TTL {}", key, ttl);
        // Read the previous value before upserting
        Object oldValue = get(key);
        ByteBuffer serializedValue = KeyValueRepositoryHelper.serializeToByteBuffer(value);
        int ttlSeconds = toTtlSeconds(ttl);
        if (ttlSeconds > 0) {
            getSession().execute(insertWithTtlStatement.bind(key, serializedValue, ttlSeconds));
        } else {
            getSession().execute(insertStatement.bind(key, serializedValue));
        }
        return oldValue;
    }

    @Override
    @ManagedOperation(description = "Delete a key")
    public Object delete(String key) {
        LOGGER.debug("Deleting key {}", key);
        // Read the previous value before deleting
        Object oldValue = get(key);
        getSession().execute(deleteStatement.bind(key));
        return oldValue;
    }

    @Override
    @ManagedOperation(description = "Check if key exists")
    public boolean contains(String key) {
        LOGGER.debug("Checking key {}", key);
        ResultSet rs = getSession().execute(selectStatement.bind(key));
        return rs.one() != null;
    }

    @Override
    public Set<String> keys() {
        LOGGER.debug("Getting all keys from table {}", table);
        ResultSet rs = getSession().execute(selectAllKeysStatement.bind());
        Set<String> result = new LinkedHashSet<>();
        for (Row row : rs) {
            result.add(row.getString(KEY_COLUMN));
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    @ManagedOperation(description = "Clear all entries")
    public void clear() {
        LOGGER.debug("Clear table {}", table);
        getSession().execute(truncateStatement.bind());
    }

    /**
     * Atomically stores the value under the given key only if no mapping already exists, using Cassandra's lightweight
     * transaction ({@code INSERT ... IF NOT EXISTS}).
     *
     * @param  key   the key
     * @param  value the value to store
     * @param  ttl   the time-to-live; {@code null}, zero, or negative means no expiration
     * @return       the existing value if the key was already present, or {@code null} if the put succeeded
     */
    @Override
    public Object putIfAbsent(String key, Object value, Duration ttl) {
        LOGGER.debug("Putting key {} if absent with TTL {}", key, ttl);
        ByteBuffer serializedValue = KeyValueRepositoryHelper.serializeToByteBuffer(value);
        ResultSet rs;
        int ttlSeconds = toTtlSeconds(ttl);
        if (ttlSeconds > 0) {
            rs = getSession().execute(insertIfNotExistsWithTtlStatement.bind(key, serializedValue, ttlSeconds));
        } else {
            rs = getSession().execute(insertIfNotExistsStatement.bind(key, serializedValue));
        }
        Row row = rs.one();
        if (row == null || row.getBoolean("[applied]")) {
            return null;
        }
        // Insert was not applied; return the existing value from the result row
        ByteBuffer existingBuffer = row.getByteBuffer(VALUE_COLUMN);
        return existingBuffer != null ? KeyValueRepositoryHelper.deserialize(existingBuffer) : null;
    }

    @Override
    @ManagedAttribute(description = "The number of entries in the repository")
    public int size() {
        return keys().size();
    }

    private static int toTtlSeconds(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return 0;
        }
        return (int) ttl.toSeconds();
    }

    // -------------------------------------------------------------------------
    // Getters & Setters

    public CqlSession getSession() {
        return session.getSession();
    }

    public void setSession(CqlSession session) {
        this.session = new CassandraSessionHolder(session);
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
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
}

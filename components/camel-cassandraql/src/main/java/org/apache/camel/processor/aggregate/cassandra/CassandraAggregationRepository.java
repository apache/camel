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

package org.apache.camel.processor.aggregate.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static org.apache.camel.utils.cassandra.CassandraUtils.append;
import static org.apache.camel.utils.cassandra.CassandraUtils.applyConsistencyLevel;
import static org.apache.camel.utils.cassandra.CassandraUtils.concat;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateDelete;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateInsert;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateSelect;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.delete.Delete;
import com.datastax.oss.driver.api.querybuilder.insert.Insert;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.utils.cassandra.CassandraSessionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link AggregationRepository} using Cassandra table to store exchanges. Advice: use
 * LeveledCompaction for this table and tune read/write consistency levels. Warning: Cassandra is not the best tool for
 * queuing use cases See: http://www.datastax.com/dev/blog/cassandra-anti-patterns-queues-and-queue-like-datasets
 */
@Metadata(
        label = "bean",
        description = "Aggregation repository that uses Cassandra table to store exchanges."
                + " Advice: use LeveledCompaction for this table and tune read/write consistency levels.",
        annotations = {"interfaceName=org.apache.camel.spi.AggregationRepository"})
@Configurer(metadataOnly = true)
public class CassandraAggregationRepository extends ServiceSupport implements RecoverableAggregationRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAggregationRepository.class);

    private final CassandraCamelCodec exchangeCodec = new CassandraCamelCodec();

    @Metadata(description = "Cassandra session", required = true)
    private CassandraSessionHolder sessionHolder;

    @Metadata(description = "The table name for storing the data", defaultValue = "CAMEL_AGGREGATION")
    private String table = "CAMEL_AGGREGATION";

    @Metadata(description = "Column name for Exchange ID", defaultValue = "EXCHANGE_ID")
    private String exchangeIdColumn = "EXCHANGE_ID";

    @Metadata(description = "Column name for Exchange", defaultValue = "EXCHANGE")
    private String exchangeColumn = "EXCHANGE";

    @Metadata(
            description = "Values used as primary key prefix. Multiple values can be separated by comma.",
            displayName = "Prefix Primary Key Values",
            javaType = "java.lang.String")
    private Object[] prefixPKValues = new Object[0];

    @Metadata(
            description = "Primary key columns. Multiple values can be separated by comma.",
            displayName = "Primary Key Columns",
            javaType = "java.lang.String",
            defaultValue = "KEY")
    private String[] pkColumns = {"KEY"};

    @Metadata(description = "Time to live in seconds used for inserts", displayName = "Time to Live")
    private Integer ttl;

    @Metadata(
            description = "Write consistency level",
            enums = "ANY,ONE,TWO,THREE,QUORUM,ALL,LOCAL_ONE,LOCAL_QUORUM,EACH_QUORUM,SERIAL,LOCAL_SERIAL")
    private ConsistencyLevel writeConsistencyLevel;

    @Metadata(
            description = "Read consistency level",
            enums = "ANY,ONE,TWO,THREE,QUORUM,ALL,LOCAL_ONE,LOCAL_QUORUM,EACH_QUORUM,SERIAL,LOCAL_SERIAL")
    private ConsistencyLevel readConsistencyLevel;

    private PreparedStatement insertStatement;
    private PreparedStatement selectStatement;
    private PreparedStatement deleteStatement;
    /**
     * Prepared statement used to get exchangeIds and exchange ids
     */
    private PreparedStatement selectKeyIdStatement;
    /**
     * Prepared statement used to delete with key and exchange id
     */
    private PreparedStatement deleteIfIdStatement;

    @Metadata(description = "Sets the interval between recovery scans", defaultValue = "5000")
    private long recoveryInterval = 5000;

    @Metadata(description = "Whether or not recovery is enabled", defaultValue = "true")
    private boolean useRecovery = true;

    @Metadata(
            description = "Sets an optional dead letter channel which exhausted recovered Exchange should be send to.")
    private String deadLetterUri;

    @Metadata(
            description =
                    "Sets an optional limit of the number of redelivery attempt of recovered Exchange should be attempted, before its exhausted."
                            + " When this limit is hit, then the Exchange is moved to the dead letter channel.")
    private int maximumRedeliveries;

    @Metadata(
            label = "advanced",
            description =
                    "Whether headers on the Exchange that are Java objects and Serializable should be included and saved to the repository")
    private boolean allowSerializedHeaders;

    /**
     * Sets a deserialization filter while reading Object from Aggregation Repository. By default the filter will allow
     * all java packages and subpackages and all org.apache.camel packages and subpackages, while the remaining will be
     * blacklisted and not deserialized. This parameter should be customized if you're using classes you trust to be
     * deserialized.
     */
    private String deserializationFilter = "java.**;org.apache.camel.**;!*";

    public CassandraAggregationRepository() {}

    public CassandraAggregationRepository(CqlSession session) {
        this.sessionHolder = new CassandraSessionHolder(session);
    }

    /**
     * Generate primary key values from aggregation key.
     */
    protected Object[] getPKValues(String key) {
        return append(prefixPKValues, key);
    }

    /**
     * Get aggregation key colum name.
     */
    private String getKeyColumn() {
        return pkColumns[pkColumns.length - 1];
    }

    private String[] getAllColumns() {
        return append(pkColumns, exchangeIdColumn, exchangeColumn);
    }
    // --------------------------------------------------------------------------
    // Service support

    @Override
    protected void doStart() throws Exception {
        sessionHolder.start();
        initInsertStatement();
        initSelectStatement();
        initDeleteStatement();
        initSelectKeyIdStatement();
        initDeleteIfIdStatement();
    }

    @Override
    protected void doStop() throws Exception {
        sessionHolder.stop();
    }

    // -------------------------------------------------------------------------
    // Add exchange to repository

    private void initInsertStatement() {
        Insert insert = generateInsert(table, getAllColumns(), false, ttl);
        SimpleStatement statement = applyConsistencyLevel(insert.build(), writeConsistencyLevel);
        LOGGER.debug("Generated Insert {}", statement);
        insertStatement = getSession().prepare(statement);
    }

    /**
     * Insert or update exchange in aggregation table.
     */
    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
        final Object[] idValues = getPKValues(key);
        LOGGER.debug("Inserting key {} exchange {}", idValues, exchange);
        try {
            ByteBuffer marshalledExchange = exchangeCodec.marshallExchange(exchange, allowSerializedHeaders);
            Object[] cqlParams = concat(idValues, new Object[] {exchange.getExchangeId(), marshalledExchange});
            getSession().execute(insertStatement.bind(cqlParams));
            return exchange;
        } catch (IOException iOException) {
            throw new CassandraAggregationException("Failed to write exchange", exchange, iOException);
        }
    }

    // -------------------------------------------------------------------------
    // Get exchange from repository

    protected void initSelectStatement() {
        Select select = generateSelect(table, getAllColumns(), pkColumns);
        SimpleStatement statement = applyConsistencyLevel(select.build(), readConsistencyLevel);
        LOGGER.debug("Generated Select {}", statement);
        selectStatement = getSession().prepare(statement);
    }

    /**
     * Get exchange from aggregation table by aggregation key.
     */
    @Override
    public Exchange get(CamelContext camelContext, String key) {
        Object[] pkValues = getPKValues(key);
        LOGGER.debug("Selecting key {}", pkValues);
        Row row = getSession().execute(selectStatement.bind(pkValues)).one();
        Exchange exchange = null;
        if (row != null) {
            try {
                exchange = exchangeCodec.unmarshallExchange(
                        camelContext, row.getByteBuffer(exchangeColumn), deserializationFilter);
            } catch (IOException iOException) {
                throw new CassandraAggregationException("Failed to read exchange", exchange, iOException);
            } catch (ClassNotFoundException classNotFoundException) {
                throw new CassandraAggregationException("Failed to read exchange", exchange, classNotFoundException);
            }
        }
        return exchange;
    }

    // -------------------------------------------------------------------------
    // Confirm exchange in repository
    private void initDeleteIfIdStatement() {
        Delete delete = generateDelete(table, pkColumns, false);
        Delete deleteIf = delete.ifColumn(exchangeIdColumn).isEqualTo(bindMarker());
        SimpleStatement statement = applyConsistencyLevel(deleteIf.build(), writeConsistencyLevel);
        LOGGER.debug("Generated Delete If Id {}", statement);
        deleteIfIdStatement = getSession().prepare(statement);
    }

    /**
     * Remove exchange by Id from aggregation table.
     */
    @Override
    public void confirm(CamelContext camelContext, String exchangeId) {
        String keyColumn = getKeyColumn();
        LOGGER.debug("Selecting Ids");
        List<Row> rows = selectKeyIds();
        for (Row row : rows) {
            if (row.getString(exchangeIdColumn).equals(exchangeId)) {
                String key = row.getString(keyColumn);
                Object[] cqlParams = append(getPKValues(key), exchangeId);
                LOGGER.debug("Deleting If Id {}", cqlParams);
                getSession().execute(deleteIfIdStatement.bind(cqlParams));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Remove exchange from repository

    private void initDeleteStatement() {
        Delete delete = generateDelete(table, pkColumns, false);
        SimpleStatement statement = applyConsistencyLevel(delete.build(), writeConsistencyLevel);
        LOGGER.debug("Generated Delete {}", statement);
        deleteStatement = getSession().prepare(statement);
    }

    /**
     * Remove exchange by aggregation key from aggregation table.
     */
    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        Object[] idValues = getPKValues(key);
        LOGGER.debug("Deleting key {}", (Object) idValues);
        getSession().execute(deleteStatement.bind(idValues));
    }

    // -------------------------------------------------------------------------
    private void initSelectKeyIdStatement() {
        Select select = generateSelect(
                table,
                new String[] {getKeyColumn(), exchangeIdColumn}, // Key
                // +
                // Exchange
                // Id
                // columns
                pkColumns,
                pkColumns.length - 1); // Where
        // fixed
        // PK
        // columns
        SimpleStatement statement = applyConsistencyLevel(select.build(), readConsistencyLevel);
        LOGGER.debug("Generated Select keys {}", statement);
        selectKeyIdStatement = getSession().prepare(statement);
    }

    protected List<Row> selectKeyIds() {
        LOGGER.debug("Selecting keys {}", getPrefixPKValues());
        return getSession()
                .execute(selectKeyIdStatement.bind(getPrefixPKValues()))
                .all();
    }

    /**
     * Get aggregation exchangeIds from aggregation table.
     */
    @Override
    public Set<String> getKeys() {
        List<Row> rows = selectKeyIds();
        Set<String> keys = new HashSet<>(rows.size());
        String keyColumnName = getKeyColumn();
        for (Row row : rows) {
            keys.add(row.getString(keyColumnName));
        }
        return keys;
    }

    /**
     * Get exchange IDs to be recovered
     *
     * @return Exchange IDs
     */
    @Override
    public Set<String> scan(CamelContext camelContext) {
        List<Row> rows = selectKeyIds();
        Set<String> exchangeIds = new HashSet<>(rows.size());
        for (Row row : rows) {
            exchangeIds.add(row.getString(exchangeIdColumn));
        }
        return exchangeIds;
    }

    /**
     * Get exchange by exchange ID. This is far from optimal.
     */
    @Override
    public Exchange recover(CamelContext camelContext, String exchangeId) {
        List<Row> rows = selectKeyIds();
        String keyColumnName = getKeyColumn();
        String lKey = null;
        for (Row row : rows) {
            String lExchangeId = row.getString(exchangeIdColumn);
            if (lExchangeId.equals(exchangeId)) {
                lKey = row.getString(keyColumnName);
                break;
            }
        }
        return lKey == null ? null : get(camelContext, lKey);
    }

    // -------------------------------------------------------------------------
    // Getters and Setters

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

    public Object[] getPrefixPKValues() {
        return prefixPKValues;
    }

    public void setPrefixPKValues(Object... prefixPKValues) {
        this.prefixPKValues = prefixPKValues;
    }

    public String[] getPKColumns() {
        return pkColumns;
    }

    public void setPKColumns(String... pkColumns) {
        this.pkColumns = pkColumns;
    }

    public String getExchangeIdColumn() {
        return exchangeIdColumn;
    }

    public void setExchangeIdColumn(String exchangeIdColumn) {
        this.exchangeIdColumn = exchangeIdColumn;
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

    public String getExchangeColumn() {
        return exchangeColumn;
    }

    public void setExchangeColumn(String exchangeColumnName) {
        this.exchangeColumn = exchangeColumnName;
    }

    public Integer getTtl() {
        return ttl;
    }

    public void setTtl(Integer ttl) {
        this.ttl = ttl;
    }

    public long getRecoveryInterval() {
        return recoveryInterval;
    }

    @Override
    public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        this.recoveryInterval = timeUnit.toMillis(interval);
    }

    @Override
    public void setRecoveryInterval(long recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    @Override
    public boolean isUseRecovery() {
        return useRecovery;
    }

    @Override
    public void setUseRecovery(boolean useRecovery) {
        this.useRecovery = useRecovery;
    }

    @Override
    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    @Override
    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    @Override
    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    @Override
    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    public String getDeserializationFilter() {
        return deserializationFilter;
    }

    public void setDeserializationFilter(String deserializationFilter) {
        this.deserializationFilter = deserializationFilter;
    }
}

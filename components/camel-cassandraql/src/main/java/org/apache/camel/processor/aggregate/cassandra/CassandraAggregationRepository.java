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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Delete;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.Select;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.utils.cassandra.CassandraSessionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static org.apache.camel.utils.cassandra.CassandraUtils.append;
import static org.apache.camel.utils.cassandra.CassandraUtils.applyConsistencyLevel;
import static org.apache.camel.utils.cassandra.CassandraUtils.concat;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateDelete;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateInsert;
import static org.apache.camel.utils.cassandra.CassandraUtils.generateSelect;

/**
 * Implementation of {@link AggregationRepository} using Cassandra table to
 * store exchanges. Advice: use LeveledCompaction for this table and tune
 * read/write consistency levels. Warning: Cassandra is not the best tool for
 * queuing use cases See:
 * http://www.datastax.com/dev/blog/cassandra-anti-patterns-queues-and-queue-like-datasets
 */
public class CassandraAggregationRepository extends ServiceSupport implements RecoverableAggregationRepository {
    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraAggregationRepository.class);
    /**
     * Session holder
     */
    private CassandraSessionHolder sessionHolder;
    /**
     * Table name
     */
    private String table = "CAMEL_AGGREGATION";
    /**
     * Exchange Id column name
     */
    private String exchangeIdColumn = "EXCHANGE_ID";
    /**
     * Exchange column name
     */
    private String exchangeColumn = "EXCHANGE";
    /**
     * Values used as primary key prefix
     */
    private Object[] prefixPKValues = new Object[0];
    /**
     * Primary key columns
     */
    private String[] pkColumns = {"KEY"};
    /**
     * Exchange marshaller/unmarshaller
     */
    private final CassandraCamelCodec exchangeCodec = new CassandraCamelCodec();
    /**
     * Time to live in seconds used for inserts
     */
    private Integer ttl;
    /**
     * Writeconsistency level
     */
    private ConsistencyLevel writeConsistencyLevel;
    /**
     * Read consistency level
     */
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

    private long recoveryIntervalInMillis = 5000;

    private boolean useRecovery = true;

    private String deadLetterUri;

    private int maximumRedeliveries;

    private boolean allowSerializedHeaders;

    public CassandraAggregationRepository() {
    }

    public CassandraAggregationRepository(Session session) {
        this.sessionHolder = new CassandraSessionHolder(session);
    }

    public CassandraAggregationRepository(Cluster cluster, String keyspace) {
        this.sessionHolder = new CassandraSessionHolder(cluster, keyspace);
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
        insert = applyConsistencyLevel(insert, writeConsistencyLevel);
        LOGGER.debug("Generated Insert {}", insert);
        insertStatement = getSession().prepare(insert);
    }

    /**
     * Insert or update exchange in aggregation table.
     */
    @Override
    public Exchange add(CamelContext camelContext, String key, Exchange exchange) {
        final Object[] idValues = getPKValues(key);
        LOGGER.debug("Inserting key {} exchange {}", idValues, exchange);
        try {
            ByteBuffer marshalledExchange = exchangeCodec.marshallExchange(camelContext, exchange, allowSerializedHeaders);
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
        select = applyConsistencyLevel(select, readConsistencyLevel);
        LOGGER.debug("Generated Select {}", select);
        selectStatement = getSession().prepare(select);
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
                exchange = exchangeCodec.unmarshallExchange(camelContext, row.getBytes(exchangeColumn));
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
        Delete.Conditions deleteIf = delete.onlyIf(eq(exchangeIdColumn, bindMarker()));
        deleteIf = applyConsistencyLevel(deleteIf, writeConsistencyLevel);
        LOGGER.debug("Generated Delete If Id {}", deleteIf);
        deleteIfIdStatement = getSession().prepare(deleteIf);
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
        delete = applyConsistencyLevel(delete, writeConsistencyLevel);
        LOGGER.debug("Generated Delete {}", delete);
        deleteStatement = getSession().prepare(delete);
    }

    /**
     * Remove exchange by aggregation key from aggregation table.
     */
    @Override
    public void remove(CamelContext camelContext, String key, Exchange exchange) {
        Object[] idValues = getPKValues(key);
        LOGGER.debug("Deleting key {}", (Object)idValues);
        getSession().execute(deleteStatement.bind(idValues));
    }

    // -------------------------------------------------------------------------
    private void initSelectKeyIdStatement() {
        Select select = generateSelect(table, new String[] {getKeyColumn(), exchangeIdColumn}, // Key
                                                                                               // +
                                                                                               // Exchange
                                                                                               // Id
                                                                                               // columns
                                       pkColumns, pkColumns.length - 1); // Where
                                                                         // fixed
                                                                         // PK
                                                                         // columns
        select = applyConsistencyLevel(select, readConsistencyLevel);
        LOGGER.debug("Generated Select keys {}", select);
        selectKeyIdStatement = getSession().prepare(select);
    }

    protected List<Row> selectKeyIds() {
        LOGGER.debug("Selecting keys {}", getPrefixPKValues());
        return getSession().execute(selectKeyIdStatement.bind(getPrefixPKValues())).all();
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

    public Session getSession() {
        return sessionHolder.getSession();
    }

    public void setSession(Session session) {
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

    @Override
    public long getRecoveryIntervalInMillis() {
        return recoveryIntervalInMillis;
    }

    public void setRecoveryIntervalInMillis(long recoveryIntervalInMillis) {
        this.recoveryIntervalInMillis = recoveryIntervalInMillis;
    }

    @Override
    public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        this.recoveryIntervalInMillis = timeUnit.toMillis(interval);
    }

    @Override
    public void setRecoveryInterval(long recoveryIntervalInMillis) {
        this.recoveryIntervalInMillis = recoveryIntervalInMillis;
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
}

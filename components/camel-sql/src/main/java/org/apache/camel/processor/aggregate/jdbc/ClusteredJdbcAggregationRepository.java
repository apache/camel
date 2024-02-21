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
package org.apache.camel.processor.aggregate.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.AbstractLobCreatingPreparedStatementCallback;
import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

/**
 * JDBC based {@link org.apache.camel.spi.AggregationRepository} JdbcAggregationRepository will only preserve any
 * Serializable compatible data types. If a data type is not such a type its dropped and a WARN is logged. And it only
 * persists the Message body and the Message headers. The Exchange properties are not persisted.
 */
public class ClusteredJdbcAggregationRepository extends JdbcAggregationRepository {

    private static final String INSTANCE_ID = "instance_id";
    private static final Logger LOG = LoggerFactory.getLogger(ClusteredJdbcAggregationRepository.class);

    private String instanceId = "DEFAULT";
    private boolean recoveryByInstance;

    /**
     * Creates an aggregation repository
     */
    public ClusteredJdbcAggregationRepository() {
    }

    /**
     * Creates an aggregation repository with the three mandatory parameters
     */
    public ClusteredJdbcAggregationRepository(PlatformTransactionManager transactionManager, String repositoryName,
                                              DataSource dataSource) {
        this.setRepositoryName(repositoryName);
        this.setTransactionManager(transactionManager);
        this.setDataSource(dataSource);
    }

    @Override
    public void remove(final CamelContext camelContext, final String correlationId, final Exchange exchange) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                final String confirmKey = exchange.getExchangeId();
                final long version = exchange.getProperty(VERSION_PROPERTY, Long.class);
                try {
                    LOG.debug("Removing key {}", correlationId);

                    jdbcTemplate.update("DELETE FROM " + getRepositoryName() + " WHERE " + ID + " = ? AND " + VERSION + " = ?",
                            correlationId, version);

                    insert(camelContext, confirmKey, exchange, getRepositoryNameCompleted(), version, true);

                } catch (Exception e) {
                    throw new RuntimeException(
                            "Error removing key " + correlationId + " from repository " + getRepositoryName(), e);
                }
            }
        });
    }

    /**
     * Inserts a new record into the given repository table. Note: the exchange properties are NOT persisted.
     *
     * @param camelContext   Current CamelContext
     * @param correlationId  Correlation key
     * @param exchange       Aggregated exchange to insert
     * @param repositoryName Table's name
     * @param version        Version identifier
     */
    protected void insert(
            final CamelContext camelContext, final String correlationId, final Exchange exchange,
            final String repositoryName, final Long version, final boolean completed)
            throws Exception {
        // The default totalParameterIndex is 3 for ID, Exchange and version. Depending
        // on logic this will be increased.
        int totalParameterIndex = 3;
        StringBuilder queryBuilder = new StringBuilder().append("INSERT INTO ").append(repositoryName).append('(')
                .append(EXCHANGE).append(", ").append(ID).append(", ").append(VERSION);

        if (isStoreBodyAsText()) {
            queryBuilder.append(", ").append(BODY);
            totalParameterIndex++;
        }

        if (hasHeadersToStoreAsText()) {
            for (String headerName : getHeadersToStoreAsText()) {
                queryBuilder.append(", ").append(headerName);
                totalParameterIndex++;
            }
        }
        if (completed && isRecoveryByInstance()) {
            queryBuilder.append(", ").append(INSTANCE_ID);
            totalParameterIndex++;
        }
        queryBuilder.append(") VALUES (");

        queryBuilder.append("?, ".repeat(totalParameterIndex - 1));
        queryBuilder.append("?)");

        String sql = queryBuilder.toString();

        insertHelper(camelContext, correlationId, exchange, sql, version, completed);
    }

    protected int insertHelper(
            final CamelContext camelContext, final String key, final Exchange exchange,
            final String sql, final Long version, final boolean completed)
            throws Exception {
        final byte[] data = codec.marshallExchange(exchange, allowSerializedHeaders);
        Integer insertCount = super.jdbcTemplate.execute(sql,
                new AbstractLobCreatingPreparedStatementCallback(getLobHandler()) {
                    @Override
                    protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                        int totalParameterIndex = 0;
                        lobCreator.setBlobAsBytes(ps, ++totalParameterIndex, data);
                        ps.setString(++totalParameterIndex, key);
                        ps.setLong(++totalParameterIndex, version);
                        if (isStoreBodyAsText()) {
                            ps.setString(++totalParameterIndex, exchange.getIn().getBody(String.class));
                        }
                        if (hasHeadersToStoreAsText()) {
                            for (String headerName : getHeadersToStoreAsText()) {
                                String headerValue = exchange.getIn().getHeader(headerName, String.class);
                                ps.setString(++totalParameterIndex, headerValue);
                            }
                        }
                        if (completed && isRecoveryByInstance()) {
                            ps.setString(++totalParameterIndex, instanceId);
                        }
                    }
                });
        return insertCount == null ? 0 : insertCount;
    }

    @Override
    public Set<String> scan(final CamelContext camelContext) {
        return transactionTemplateReadOnly.execute(new TransactionCallback<LinkedHashSet<String>>() {
            public LinkedHashSet<String> doInTransaction(final TransactionStatus status) {
                final List<String> keys = jdbcTemplate.query(
                        "SELECT " + ID + " FROM " + getRepositoryNameCompleted()
                                                             + (isRecoveryByInstance()
                                                                     ? " WHERE INSTANCE_ID='" + instanceId + "'" : ""),
                        new RowMapper<String>() {
                            public String mapRow(final ResultSet rs, final int rowNum) throws SQLException {
                                final String id = rs.getString(ID);
                                LOG.trace("getKey {}", id);
                                return id;
                            }
                        });
                return new LinkedHashSet<>(keys);
            }
        });
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(final String instanceId) {
        this.instanceId = instanceId;
    }

    public boolean isRecoveryByInstance() {
        return recoveryByInstance;
    }

    public void setRecoveryByInstance(final boolean recoveryByInstance) {
        this.recoveryByInstance = recoveryByInstance;
    }

}

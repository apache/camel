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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Constants;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.AbstractLobCreatingPreparedStatementCallback;
import org.springframework.jdbc.core.support.AbstractLobStreamingResultSetExtractor;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileCopyUtils;

/**
 * JDBC based {@link org.apache.camel.spi.AggregationRepository}
 * JdbcAggregationRepository will only preserve any Serializable compatible
 * data types. If a data type is not such a type its dropped and a WARN is
 * logged. And it only persists the Message body and the Message headers.
 * The Exchange properties are not persisted.
 */
public class JdbcAggregationRepository extends ServiceSupport implements RecoverableAggregationRepository, OptimisticLockingAggregationRepository {

    protected static final String EXCHANGE = "exchange";
    protected static final String ID = "id";
    protected static final String BODY = "body";

    private static final Logger LOG = LoggerFactory.getLogger(JdbcAggregationRepository.class);
    private static final Constants PROPAGATION_CONSTANTS = new Constants(TransactionDefinition.class);

    private JdbcOptimisticLockingExceptionMapper jdbcOptimisticLockingExceptionMapper = new DefaultJdbcOptimisticLockingExceptionMapper();
    private PlatformTransactionManager transactionManager;
    private DataSource dataSource;
    private TransactionTemplate transactionTemplate;
    private TransactionTemplate transactionTemplateReadOnly;
    private int propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED;
    private JdbcTemplate jdbcTemplate;
    private LobHandler lobHandler = new DefaultLobHandler();
    private String repositoryName;
    private boolean returnOldExchange;
    private JdbcCamelCodec codec = new JdbcCamelCodec();
    private long recoveryInterval = 5000;
    private boolean useRecovery = true;
    private int maximumRedeliveries;
    private String deadLetterUri;
    private List<String> headersToStoreAsText;
    private boolean storeBodyAsText;
    private boolean allowSerializedHeaders;

    /**
     * Creates an aggregation repository
     */
    public JdbcAggregationRepository() {
    }

    /**
     * Creates an aggregation repository with the three mandatory parameters
     */
    public JdbcAggregationRepository(PlatformTransactionManager transactionManager, String repositoryName, DataSource dataSource) {
        this.setRepositoryName(repositoryName);
        this.setTransactionManager(transactionManager);
        this.setDataSource(dataSource);
    }

    /**
     * Sets the name of the repository
     */
    public final void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public final void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    /**
     * Sets the DataSource to use for accessing the database
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public Exchange add(final CamelContext camelContext, final String correlationId,
                        final Exchange oldExchange, final Exchange newExchange) throws OptimisticLockingException {

        try {
            return add(camelContext, correlationId, newExchange);
        } catch (Exception e) {
            if (jdbcOptimisticLockingExceptionMapper != null && jdbcOptimisticLockingExceptionMapper.isOptimisticLocking(e)) {
                throw new OptimisticLockingException();
            } else {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    public Exchange add(final CamelContext camelContext, final String correlationId, final Exchange exchange) {
        return transactionTemplate.execute(new TransactionCallback<Exchange>() {

            public Exchange doInTransaction(TransactionStatus status) {
                Exchange result = null;
                final String key = correlationId;

                try {
                    LOG.debug("Adding exchange with key: [{}]", key);

                    boolean present = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM " + getRepositoryName() + " WHERE " + ID + " = ?", Integer.class, key) != 0;

                    // Recover existing exchange with that ID
                    if (isReturnOldExchange() && present) {
                        result = get(key, getRepositoryName(), camelContext);
                    }

                    if (present) {
                        update(camelContext, correlationId, exchange, getRepositoryName());
                    } else {
                        insert(camelContext, correlationId, exchange, getRepositoryName());
                    }

                } catch (Exception e) {
                    throw new RuntimeException("Error adding to repository " + repositoryName + " with key " + key, e);
                }

                return result;
            }
        });
    }

    /**
     * Updates the current exchange details in the given repository table
     *
     * @param camelContext   the current CamelContext
     * @param key            the correlation key
     * @param exchange       the aggregated exchange
     * @param repositoryName The name of the table
     */
    protected void update(final CamelContext camelContext, final String key, final Exchange exchange, String repositoryName) throws Exception {
        StringBuilder queryBuilder = new StringBuilder()
                .append("UPDATE ").append(repositoryName)
                .append(" SET ")
                .append(EXCHANGE).append(" = ?");
        if (storeBodyAsText) {
            queryBuilder.append(", ").append(BODY).append(" = ?");
        }

        if (hasHeadersToStoreAsText()) {
            for (String headerName : headersToStoreAsText) {
                queryBuilder.append(", ").append(headerName).append(" = ?");
            }
        }

        queryBuilder.append(" WHERE ").append(ID).append(" = ?");

        String sql = queryBuilder.toString();
        insertAndUpdateHelper(camelContext, key, exchange, sql, false);
    }

    /**
     * Inserts a new record into the given repository table.
     * note : the exchange properties are NOT persisted.
     *
     * @param camelContext   the current CamelContext
     * @param correlationId  the correlation key
     * @param exchange       the aggregated exchange to insert. The headers will be persisted but not the properties.
     * @param repositoryName The name of the table
     */
    protected void insert(final CamelContext camelContext, final String correlationId, final Exchange exchange, String repositoryName) throws Exception {
        // The default totalParameterIndex is 2 for ID and Exchange. Depending on logic this will be increased
        int totalParameterIndex = 2;
        StringBuilder queryBuilder = new StringBuilder()
                .append("INSERT INTO ").append(repositoryName)
                .append('(')
                .append(EXCHANGE).append(", ")
                .append(ID);

        if (storeBodyAsText) {
            queryBuilder.append(", ").append(BODY);
            totalParameterIndex++;
        }

        if (hasHeadersToStoreAsText()) {
            for (String headerName : headersToStoreAsText) {
                queryBuilder.append(", ").append(headerName);
                totalParameterIndex++;
            }
        }

        queryBuilder.append(") VALUES (");

        for (int i = 0; i < totalParameterIndex - 1; i++) {
            queryBuilder.append("?, ");
        }
        queryBuilder.append("?)");

        String sql = queryBuilder.toString();

        insertAndUpdateHelper(camelContext, correlationId, exchange, sql, true);
    }

    protected int insertAndUpdateHelper(final CamelContext camelContext, final String key, final Exchange exchange, String sql, final boolean idComesFirst) throws Exception {
        final byte[] data = codec.marshallExchange(camelContext, exchange, allowSerializedHeaders);
        Integer updateCount = jdbcTemplate.execute(sql,
                new AbstractLobCreatingPreparedStatementCallback(getLobHandler()) {
                    @Override
                    protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                        int totalParameterIndex = 0;
                        lobCreator.setBlobAsBytes(ps, ++totalParameterIndex, data);
                        if (idComesFirst) {
                            ps.setString(++totalParameterIndex, key);
                        }
                        if (storeBodyAsText) {
                            ps.setString(++totalParameterIndex, exchange.getIn().getBody(String.class));
                        }
                        if (hasHeadersToStoreAsText()) {
                            for (String headerName : headersToStoreAsText) {
                                String headerValue = exchange.getIn().getHeader(headerName, String.class);
                                ps.setString(++totalParameterIndex, headerValue);
                            }
                        }
                        if (!idComesFirst) {
                            ps.setString(++totalParameterIndex, key);
                        }
                    }
                });
        return updateCount == null ? 0 : updateCount;
    }

    @Override
    public Exchange get(final CamelContext camelContext, final String correlationId) {
        final String key = correlationId;
        Exchange result = get(key, getRepositoryName(), camelContext);

        LOG.debug("Getting key  [{}] -> {}", key, result);

        return result;
    }

    private Exchange get(final String key, final String repositoryName, final CamelContext camelContext) {
        return transactionTemplateReadOnly.execute(new TransactionCallback<Exchange>() {
            public Exchange doInTransaction(TransactionStatus status) {
                try {
                    String sql = "SELECT " + EXCHANGE + " FROM " + repositoryName + " WHERE " + ID + " = ?";
                    ByteArrayOutputStream bis = new ByteArrayOutputStream();
                    jdbcTemplate.query(sql, new Object[] {key}, new AbstractLobStreamingResultSetExtractor<Object>() {
                        @Override
                        protected void streamData(ResultSet rs) throws SQLException, IOException, DataAccessException {
                            FileCopyUtils.copy(getLobHandler().getBlobAsBinaryStream(rs, EXCHANGE), bis);
                        }
                    });
                    return codec.unmarshallExchange(camelContext, bis.toByteArray());
                } catch (EmptyResultDataAccessException ex) {
                    return null;
                } catch (IOException ex) {
                    // Rollback the transaction
                    throw new RuntimeException("Error getting key " + key + " from repository " + repositoryName, ex);
                } catch (ClassNotFoundException ex) {
                    // Rollback the transaction
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    @Override
    public void remove(final CamelContext camelContext, final String correlationId, final Exchange exchange) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                final String key = correlationId;
                final String confirmKey = exchange.getExchangeId();
                try {
                    LOG.debug("Removing key [{}]", key);

                    jdbcTemplate.update("DELETE FROM " + getRepositoryName() + " WHERE " + ID + " = ?", key);

                    insert(camelContext, confirmKey, exchange, getRepositoryNameCompleted());

                } catch (Exception e) {
                    throw new RuntimeException("Error removing key " + key + " from repository " + repositoryName, e);
                }
            }
        });
    }

    @Override
    public void confirm(final CamelContext camelContext, final String exchangeId) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                LOG.debug("Confirming exchangeId [{}]", exchangeId);
                final String confirmKey = exchangeId;

                jdbcTemplate.update("DELETE FROM " + getRepositoryNameCompleted() + " WHERE " + ID + " = ?",
                        new Object[]{confirmKey});

            }
        });
    }

    @Override
    public Set<String> getKeys() {
        return getKeys(getRepositoryName());
    }

    @Override
    public Set<String> scan(CamelContext camelContext) {
        return getKeys(getRepositoryNameCompleted());
    }

    /**
     * Returns the keys in the given repository
     *
     * @param repositoryName The name of the table
     * @return Set of keys in the given repository name
     */
    protected Set<String> getKeys(final String repositoryName) {
        return transactionTemplateReadOnly.execute(new TransactionCallback<LinkedHashSet<String>>() {
            public LinkedHashSet<String> doInTransaction(TransactionStatus status) {
                List<String> keys = jdbcTemplate.query("SELECT " + ID + " FROM " + repositoryName,
                        new RowMapper<String>() {
                            public String mapRow(ResultSet rs, int rowNum) throws SQLException {
                                String id = rs.getString(ID);
                                LOG.trace("getKey [{}]", id);
                                return id;
                            }
                        });
                return new LinkedHashSet<>(keys);
            }
        });
    }

    @Override
    public Exchange recover(CamelContext camelContext, String exchangeId) {
        final String key = exchangeId;
        Exchange answer = get(key, getRepositoryNameCompleted(), camelContext);

        LOG.debug("Recovering exchangeId [{}] -> {}", key, answer);

        return answer;
    }

    /**
     *  If recovery is enabled then a background task is run every x'th time to scan for failed exchanges to recover
     *  and resubmit. By default this interval is 5000 millis.
     */
    @Override
    public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        this.recoveryInterval = timeUnit.toMillis(interval);
    }

    @Override
    public void setRecoveryInterval(long interval) {
        this.recoveryInterval = interval;
    }

    @Override
    public long getRecoveryIntervalInMillis() {
        return recoveryInterval;
    }

    @Override
    public boolean isUseRecovery() {
        return useRecovery;
    }

    /**
     * Whether or not recovery is enabled. This option is by default true. When enabled the Camel
     * Aggregator automatic recover failed aggregated exchange and have them resubmitted.
     */
    @Override
    public void setUseRecovery(boolean useRecovery) {
        this.useRecovery = useRecovery;
    }

    @Override
    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    @Override
    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    @Override
    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    /**
     * An endpoint uri for a Dead Letter Channel where exhausted recovered Exchanges will be
     * moved. If this option is used then the maximumRedeliveries option must also be provided.
     * Important note : if the deadletter route throws an exception, it will be send again to DLQ until it succeed !
     */
    @Override
    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    public boolean isReturnOldExchange() {
        return returnOldExchange;
    }

    /**
     * Whether the get operation should return the old existing Exchange if any existed.
     * By default this option is false to optimize as we do not need the old exchange when aggregating.
     */
    public void setReturnOldExchange(boolean returnOldExchange) {
        this.returnOldExchange = returnOldExchange;
    }

    public void setJdbcCamelCodec(JdbcCamelCodec codec) {
        this.codec = codec;
    }

    public boolean hasHeadersToStoreAsText() {
        return this.headersToStoreAsText != null && !this.headersToStoreAsText.isEmpty();
    }

    public List<String> getHeadersToStoreAsText() {
        return headersToStoreAsText;
    }

    /**
     * Allows to store headers as String which is human readable. By default this option is disabled,
     * storing the headers in binary format.
     *
     * @param headersToStoreAsText the list of headers to store as String
     */
    public void setHeadersToStoreAsText(List<String> headersToStoreAsText) {
        this.headersToStoreAsText = headersToStoreAsText;
    }

    public boolean isStoreBodyAsText() {
        return storeBodyAsText;
    }

    /**
     * Whether to store the message body as String which is human readable.
     * By default this option is false storing the body in binary format.
     */
    public void setStoreBodyAsText(boolean storeBodyAsText) {
        this.storeBodyAsText = storeBodyAsText;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    public int getPropagationBehavior() {
        return propagationBehavior;
    }

    /**
     * Sets propagation behavior to use with spring transaction templates which are used for database access.
     * The default is TransactionDefinition.PROPAGATION_REQUIRED.
     */
    public void setPropagationBehavior(int propagationBehavior) {
        this.propagationBehavior = propagationBehavior;
    }

    /**
     * Sets propagation behavior to use with spring transaction templates which are used for database access.
     * The default is TransactionDefinition.PROPAGATION_REQUIRED. This setter accepts names of the constants, like
     * "PROPAGATION_REQUIRED".
     * @param propagationBehaviorName
     */
    public void setPropagationBehaviorName(String propagationBehaviorName) {
        if (!propagationBehaviorName.startsWith(DefaultTransactionDefinition.PREFIX_PROPAGATION)) {
            throw new IllegalArgumentException("Only propagation constants allowed");
        }
        setPropagationBehavior(PROPAGATION_CONSTANTS.asNumber(propagationBehaviorName).intValue());
    }

    public LobHandler getLobHandler() {
        return lobHandler;
    }

    /**
     * Sets a custom LobHandler to use
     */
    public void setLobHandler(LobHandler lobHandler) {
        this.lobHandler = lobHandler;
    }

    public JdbcOptimisticLockingExceptionMapper getJdbcOptimisticLockingExceptionMapper() {
        return jdbcOptimisticLockingExceptionMapper;
    }

    public void setJdbcOptimisticLockingExceptionMapper(JdbcOptimisticLockingExceptionMapper jdbcOptimisticLockingExceptionMapper) {
        this.jdbcOptimisticLockingExceptionMapper = jdbcOptimisticLockingExceptionMapper;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getRepositoryNameCompleted() {
        return getRepositoryName() + "_completed";
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(repositoryName, "RepositoryName");
        ObjectHelper.notNull(transactionManager, "TransactionManager");
        ObjectHelper.notNull(dataSource, "DataSource");

        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(propagationBehavior);

        transactionTemplateReadOnly = new TransactionTemplate(transactionManager);
        transactionTemplateReadOnly.setPropagationBehavior(propagationBehavior);
        transactionTemplateReadOnly.setReadOnly(true);

        // log number of existing exchanges
        int current = getKeys().size();
        int completed = scan(null).size();

        if (current > 0) {
            LOG.info("On startup there are " + current + " aggregate exchanges (not completed) in repository: " + getRepositoryName());
        } else {
            LOG.info("On startup there are no existing aggregate exchanges (not completed) in repository: {}", getRepositoryName());
        }
        if (completed > 0) {
            LOG.warn("On startup there are " + completed + " completed exchanges to be recovered in repository: " + getRepositoryNameCompleted());
        } else {
            LOG.info("On startup there are no completed exchanges to be recovered in repository: {}", getRepositoryNameCompleted());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}

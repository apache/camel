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

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.AbstractLobCreatingPreparedStatementCallback;
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

/**
 * JDBC based {@link org.apache.camel.spi.AggregationRepository} JdbcAggregationRepository will only preserve any
 * Serializable compatible data types. If a data type is not such a type its dropped and a WARN is logged. And it only
 * persists the Message body and the Message headers. The Exchange properties are not persisted.
 */
public class JdbcAggregationRepository extends ServiceSupport
        implements RecoverableAggregationRepository, OptimisticLockingAggregationRepository {

    protected static final String EXCHANGE = "exchange";
    protected static final String ID = "id";
    protected static final String BODY = "body";

    // optimistic locking: version identifier needed to avoid the lost update problem
    protected static final String VERSION = "version";
    protected static final String VERSION_PROPERTY = "CamelOptimisticLockVersion";

    private static final Logger LOG = LoggerFactory.getLogger(JdbcAggregationRepository.class);
    private static final Constants PROPAGATION_CONSTANTS = new Constants(TransactionDefinition.class);

    protected JdbcCamelCodec codec = new JdbcCamelCodec();
    protected JdbcTemplate jdbcTemplate;
    protected TransactionTemplate transactionTemplate;
    protected TransactionTemplate transactionTemplateReadOnly;
    protected boolean allowSerializedHeaders;

    private JdbcOptimisticLockingExceptionMapper jdbcOptimisticLockingExceptionMapper
            = new DefaultJdbcOptimisticLockingExceptionMapper();
    private PlatformTransactionManager transactionManager;
    private DataSource dataSource;
    private int propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED;
    private LobHandler lobHandler = new DefaultLobHandler();
    private String repositoryName;
    private boolean returnOldExchange;
    private long recoveryInterval = 5000;
    private boolean useRecovery = true;
    private int maximumRedeliveries;
    private String deadLetterUri;
    private List<String> headersToStoreAsText;
    private boolean storeBodyAsText;

    /**
     * Creates an aggregation repository
     */
    public JdbcAggregationRepository() {
    }

    /**
     * Creates an aggregation repository with the three mandatory parameters
     */
    public JdbcAggregationRepository(PlatformTransactionManager transactionManager, String repositoryName,
                                     DataSource dataSource) {
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
    public Exchange add(
            final CamelContext camelContext, final String correlationId,
            final Exchange oldExchange, final Exchange newExchange)
            throws OptimisticLockingException {

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

                try {
                    LOG.debug("Adding exchange with key {}", correlationId);

                    boolean present = jdbcTemplate.queryForObject(
                            "SELECT COUNT(1) FROM " + getRepositoryName() + " WHERE " + ID + " = ?", Integer.class,
                            correlationId) != 0;

                    // Recover existing exchange with that ID
                    if (isReturnOldExchange() && present) {
                        result = get(correlationId, getRepositoryName(), camelContext);
                    }

                    if (present) {
                        Long versionLong = exchange.getProperty(VERSION_PROPERTY, Long.class);
                        if (versionLong == null) {
                            LOG.debug("Race while inserting record with key {}", correlationId);
                            throw new OptimisticLockingException();
                        } else {
                            long version = versionLong.longValue();
                            LOG.debug("Updating record with key {} and version {}", correlationId, version);
                            update(camelContext, correlationId, exchange, getRepositoryName(), version);
                        }
                    } else {
                        LOG.debug("Inserting record with key {}", correlationId);
                        insert(camelContext, correlationId, exchange, getRepositoryName(), 1L);
                    }

                } catch (Exception e) {
                    throw new RuntimeException(
                            "Error adding to repository " + repositoryName + " with key " + correlationId, e);
                }

                return result;
            }
        });
    }

    /**
     * Updates the current exchange details in the given repository table.
     *
     * @param camelContext   Current CamelContext
     * @param key            Correlation key
     * @param exchange       Aggregated exchange
     * @param repositoryName Table's name
     * @param version        Version identifier
     */
    protected void update(
            final CamelContext camelContext, final String key, final Exchange exchange, String repositoryName, Long version)
            throws Exception {
        StringBuilder queryBuilder = new StringBuilder()
                .append("UPDATE ").append(repositoryName)
                .append(" SET ")
                .append(EXCHANGE).append(" = ?")
                .append(", ")
                .append(VERSION).append(" = ?");
        if (storeBodyAsText) {
            queryBuilder.append(", ").append(BODY).append(" = ?");
        }

        if (hasHeadersToStoreAsText()) {
            for (String headerName : headersToStoreAsText) {
                queryBuilder.append(", ").append(headerName).append(" = ?");
            }
        }

        queryBuilder.append(" WHERE ")
                .append(ID).append(" = ?")
                .append(" AND ")
                .append(VERSION).append(" = ?");

        String sql = queryBuilder.toString();
        updateHelper(camelContext, key, exchange, sql, version);
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
            final CamelContext camelContext, final String correlationId, final Exchange exchange, String repositoryName,
            Long version)
            throws Exception {
        // The default totalParameterIndex is 3 for ID, Exchange and version. Depending on logic this will be increased.
        int totalParameterIndex = 3;
        StringBuilder queryBuilder = new StringBuilder()
                .append("INSERT INTO ").append(repositoryName)
                .append('(').append(EXCHANGE)
                .append(", ").append(ID)
                .append(", ").append(VERSION);

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

        queryBuilder.append("?, ".repeat(totalParameterIndex - 1));
        queryBuilder.append("?)");

        String sql = queryBuilder.toString();

        insertHelper(camelContext, correlationId, exchange, sql, version);
    }

    protected int insertHelper(
            final CamelContext camelContext, final String key, final Exchange exchange, String sql, final Long version)
            throws Exception {
        final byte[] data = codec.marshallExchange(exchange, allowSerializedHeaders);
        Integer insertCount = jdbcTemplate.execute(sql,
                new AbstractLobCreatingPreparedStatementCallback(getLobHandler()) {
                    @Override
                    protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                        int totalParameterIndex = 0;
                        lobCreator.setBlobAsBytes(ps, ++totalParameterIndex, data);
                        ps.setString(++totalParameterIndex, key);
                        ps.setLong(++totalParameterIndex, version);
                        if (storeBodyAsText) {
                            ps.setString(++totalParameterIndex, exchange.getIn().getBody(String.class));
                        }
                        if (hasHeadersToStoreAsText()) {
                            for (String headerName : headersToStoreAsText) {
                                String headerValue = exchange.getIn().getHeader(headerName, String.class);
                                ps.setString(++totalParameterIndex, headerValue);
                            }
                        }
                    }
                });
        return insertCount == null ? 0 : insertCount;
    }

    protected int updateHelper(
            final CamelContext camelContext, final String key, final Exchange exchange, String sql, final Long version)
            throws Exception {
        final byte[] data = codec.marshallExchange(exchange, allowSerializedHeaders);
        Integer updateCount = jdbcTemplate.execute(sql,
                new AbstractLobCreatingPreparedStatementCallback(getLobHandler()) {
                    @Override
                    protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                        int totalParameterIndex = 0;
                        lobCreator.setBlobAsBytes(ps, ++totalParameterIndex, data);
                        ps.setLong(++totalParameterIndex, version + 1);
                        if (storeBodyAsText) {
                            ps.setString(++totalParameterIndex, exchange.getIn().getBody(String.class));
                        }
                        if (hasHeadersToStoreAsText()) {
                            for (String headerName : headersToStoreAsText) {
                                String headerValue = exchange.getIn().getHeader(headerName, String.class);
                                ps.setString(++totalParameterIndex, headerValue);
                            }
                        }
                        ps.setString(++totalParameterIndex, key);
                        ps.setLong(++totalParameterIndex, version);
                    }
                });
        if (updateCount == 1) {
            return updateCount;
        } else {
            // Found stale version while updating record
            throw new OptimisticLockingException();
        }
    }

    @Override
    public Exchange get(final CamelContext camelContext, final String correlationId) {
        Exchange result = get(correlationId, getRepositoryName(), camelContext);
        LOG.debug("Getting key {} -> {}", correlationId, result);
        return result;
    }

    private Exchange get(final String key, final String repositoryName, final CamelContext camelContext) {
        return transactionTemplateReadOnly.execute(new TransactionCallback<Exchange>() {
            public Exchange doInTransaction(TransactionStatus status) {
                try {

                    Map<String, Object> columns = jdbcTemplate.queryForMap(
                            String.format("SELECT %1$s, %2$s FROM %3$s WHERE %4$s=?", EXCHANGE, VERSION, repositoryName, ID),
                            new Object[] { key }, new int[] { Types.VARCHAR });

                    byte[] marshalledExchange = (byte[]) columns.get(EXCHANGE);
                    long version;
                    Object versionObj = columns.get(VERSION);
                    if (versionObj instanceof BigDecimal) {
                        version = ((BigDecimal) versionObj).longValue();
                    } else {
                        version = (long) versionObj;
                    }

                    Exchange result = codec.unmarshallExchange(camelContext, marshalledExchange);
                    result.setProperty(VERSION_PROPERTY, version);
                    return result;

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
                final String confirmKey = exchange.getExchangeId();
                final long version = exchange.getProperty(VERSION_PROPERTY, Long.class);
                try {
                    LOG.debug("Removing key {}", correlationId);

                    jdbcTemplate.update("DELETE FROM " + getRepositoryName() + " WHERE " + ID + " = ? AND " + VERSION + " = ?",
                            correlationId, version);

                    insert(camelContext, confirmKey, exchange, getRepositoryNameCompleted(), version);
                    LOG.debug("Removed key {}", correlationId);

                } catch (Exception e) {
                    throw new RuntimeException("Error removing key " + correlationId + " from repository " + repositoryName, e);
                }
            }
        });
    }

    @Override
    public void confirm(final CamelContext camelContext, final String exchangeId) {
        confirmWithResult(camelContext, exchangeId);
    }

    @Override
    public boolean confirmWithResult(final CamelContext camelContext, final String exchangeId) {
        return transactionTemplate.execute(new TransactionCallback<Boolean>() {
            public Boolean doInTransaction(TransactionStatus status) {
                LOG.debug("Confirming exchangeId {}", exchangeId);
                final int mustBeOne = jdbcTemplate
                        .update("DELETE FROM " + getRepositoryNameCompleted() + " WHERE " + ID + " = ?", exchangeId);
                if (mustBeOne != 1) {
                    LOG.error("problem removing row {} from {} - DELETE statement did not return 1 but {}",
                            exchangeId, getRepositoryNameCompleted(), mustBeOne);
                    return false;
                }
                return true;
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
     * @param  repositoryName The name of the table
     * @return                Set of keys in the given repository name
     */
    protected Set<String> getKeys(final String repositoryName) {
        return transactionTemplateReadOnly.execute(new TransactionCallback<LinkedHashSet<String>>() {
            public LinkedHashSet<String> doInTransaction(TransactionStatus status) {
                List<String> keys = jdbcTemplate.query("SELECT " + ID + " FROM " + repositoryName,
                        new RowMapper<String>() {
                            public String mapRow(ResultSet rs, int rowNum) throws SQLException {
                                String id = rs.getString(ID);
                                LOG.trace("getKey {}", id);
                                return id;
                            }
                        });
                return new LinkedHashSet<>(keys);
            }
        });
    }

    @Override
    public Exchange recover(CamelContext camelContext, String exchangeId) {
        Exchange answer = get(exchangeId, getRepositoryNameCompleted(), camelContext);
        LOG.debug("Recovering exchangeId {} -> {}", exchangeId, answer);
        return answer;
    }

    /**
     * If recovery is enabled then a background task is run every x'th time to scan for failed exchanges to recover and
     * resubmit. By default this interval is 5000 millis.
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
     * Whether or not recovery is enabled. This option is by default true. When enabled the Camel Aggregator automatic
     * recover failed aggregated exchange and have them resubmitted.
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
     * An endpoint uri for a Dead Letter Channel where exhausted recovered Exchanges will be moved. If this option is
     * used then the maximumRedeliveries option must also be provided. Important note : if the deadletter route throws
     * an exception, it will be send again to DLQ until it succeed !
     */
    @Override
    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    public boolean isReturnOldExchange() {
        return returnOldExchange;
    }

    /**
     * Whether the get operation should return the old existing Exchange if any existed. By default this option is false
     * to optimize as we do not need the old exchange when aggregating.
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
     * Allows to store headers as String which is human readable. By default this option is disabled, storing the
     * headers in binary format.
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
     * Whether to store the message body as String which is human readable. By default this option is false storing the
     * body in binary format.
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
     * Sets propagation behavior to use with spring transaction templates which are used for database access. The
     * default is TransactionDefinition.PROPAGATION_REQUIRED.
     */
    public void setPropagationBehavior(int propagationBehavior) {
        this.propagationBehavior = propagationBehavior;
    }

    /**
     * Sets propagation behavior to use with spring transaction templates which are used for database access. The
     * default is TransactionDefinition.PROPAGATION_REQUIRED. This setter accepts names of the constants, like
     * "PROPAGATION_REQUIRED".
     *
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

    public void setJdbcOptimisticLockingExceptionMapper(
            JdbcOptimisticLockingExceptionMapper jdbcOptimisticLockingExceptionMapper) {
        this.jdbcOptimisticLockingExceptionMapper = jdbcOptimisticLockingExceptionMapper;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getRepositoryNameCompleted() {
        return getRepositoryName() + "_completed";
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        ObjectHelper.notNull(repositoryName, "RepositoryName");
        ObjectHelper.notNull(transactionManager, "TransactionManager");
        ObjectHelper.notNull(dataSource, "DataSource");

        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(propagationBehavior);

        transactionTemplateReadOnly = new TransactionTemplate(transactionManager);
        transactionTemplateReadOnly.setPropagationBehavior(propagationBehavior);
        transactionTemplateReadOnly.setReadOnly(true);
    }

    private int rowCount(final String repository) {
        return jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + repository, Integer.class);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // log number of existing exchanges
        final int current = rowCount(getRepositoryName());
        final int completed = rowCount(getRepositoryNameCompleted());

        if (current > 0) {
            LOG.info("On startup there are {} aggregate exchanges (not completed) in repository: {}", current,
                    getRepositoryName());
        } else {
            LOG.info("On startup there are no existing aggregate exchanges (not completed) in repository: {}",
                    getRepositoryName());
        }
        if (completed > 0) {
            LOG.warn("On startup there are {} completed exchanges to be recovered in repository: {}", completed,
                    getRepositoryNameCompleted());
        } else {
            LOG.info("On startup there are no completed exchanges to be recovered in repository: {}",
                    getRepositoryNameCompleted());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}

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
package org.apache.camel.component.jdbc.aggregationrepository;

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
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.AbstractLobCreatingPreparedStatementCallback;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobCreator;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC based {@link org.apache.camel.spi.AggregationRepository}
 */
public class JdbcAggregationRepository extends ServiceSupport implements RecoverableAggregationRepository {

    private static final transient Log LOG = LogFactory.getLog(JdbcAggregationRepository.class);
    private static final String ID = "id";
    private static final String EXCHANGE = "exchange";
    private PlatformTransactionManager transactionManager;
    private DataSource dataSource;
    private TransactionTemplate transactionTemplate;
    private TransactionTemplate transactionTemplateReadOnly;
    private JdbcTemplate jdbcTemplate;
    private LobHandler lobHandler = new DefaultLobHandler();
    private String repositoryName;
    private boolean returnOldExchange;
    private JdbcCamelCodec codec = new JdbcCamelCodec();
    private long recoveryInterval = 5000;
    private boolean useRecovery = true;
    private int maximumRedeliveries;
    private String deadLetterUri;

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
     * @param repositoryName the repositoryName to set
     */
    public final void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public final void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
        
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRED);

        transactionTemplateReadOnly = new TransactionTemplate(transactionManager);
        transactionTemplateReadOnly.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRED);
        transactionTemplateReadOnly.setReadOnly(true);
    }

    public final void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @SuppressWarnings("unchecked")
    public Exchange add(final CamelContext camelContext, final String correlationId, final Exchange exchange) {
        return (Exchange) transactionTemplate.execute(new TransactionCallback() {

            public Exchange doInTransaction(TransactionStatus status) {
                String sql;
                Exchange result = null;
                final String key = correlationId;

                try {
                    final byte[] data = codec.marshallExchange(camelContext, exchange);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Adding exchange with key: [" + key + "]");
                    }

                    String insert = "INSERT INTO " + getRepositoryName() + " (" + EXCHANGE + ", " + ID + ") VALUES (?, ?)";
                    String update = "UPDATE " + getRepositoryName() + " SET " + EXCHANGE + " = ? WHERE " + ID + " = ?";

                    boolean present = jdbcTemplate.queryForInt(
                            "SELECT COUNT (*) FROM " + getRepositoryName() + " WHERE " + ID + " = ?", key) != 0;
                    sql = present ? update : insert;

                    // Recover existing exchange with that ID
                    if (isReturnOldExchange() && present) {
                        result = get(key, getRepositoryName(), camelContext);
                    }

                    jdbcTemplate.execute(sql,
                            new AbstractLobCreatingPreparedStatementCallback(getLobHandler()) {
                                @Override
                                protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                                    lobCreator.setBlobAsBytes(ps, 1, data);
                                    ps.setString(2, key);
                                }
                            });

                } catch (IOException e) {
                    throw new RuntimeException("Error adding to repository " + repositoryName + " with key " + key, e);
                }

                return result;
            }
        });

    }

    public Exchange get(final CamelContext camelContext, final String correlationId) {
        final String key = correlationId;
        Exchange result = get(key, getRepositoryName(), camelContext);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Getting key  [" + key + "] -> " + result);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Exchange get(final String key, final String repositoryName, final CamelContext camelContext) {
        return (Exchange) transactionTemplateReadOnly.execute(new TransactionCallback() {
            public Exchange doInTransaction(TransactionStatus status) {
                try {
                    final byte[] data = jdbcTemplate.queryForObject(
                            "SELECT " + EXCHANGE + " FROM " + repositoryName + " WHERE " + ID + " = ?",
                            new Object[]{key}, byte[].class);
                    return codec.unmarshallExchange(camelContext, data);
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

    public void remove(final CamelContext camelContext, final String correlationId, final Exchange exchange) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                final String key = correlationId;
                final String confirmKey = exchange.getExchangeId();
                try {
                    final byte[] data = codec.marshallExchange(camelContext, exchange);

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Removing key [" + key + "]");
                    }

                    jdbcTemplate.update("DELETE FROM " + getRepositoryName() + " WHERE " + ID + " = ?",
                            new Object[]{key});

                    jdbcTemplate.execute("INSERT INTO " + getRepositoryNameCompleted() + " (" + EXCHANGE + ", " + ID + ") VALUES (?, ?)",
                            new AbstractLobCreatingPreparedStatementCallback(getLobHandler()) {
                                @Override
                                protected void setValues(PreparedStatement ps, LobCreator lobCreator) throws SQLException {
                                    lobCreator.setBlobAsBytes(ps, 1, data);
                                    ps.setString(2, confirmKey);
                                }
                            });
                } catch (IOException e) {
                    throw new RuntimeException("Error removing key " + key + " from repository " + repositoryName, e);
                }
            }
        });
    }

    public void confirm(final CamelContext camelContext, final String exchangeId) {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Confirming exchangeId [" + exchangeId + "]");
                }
                final String confirmKey = exchangeId;

                jdbcTemplate.update("DELETE FROM " + getRepositoryNameCompleted() + " WHERE " + ID + " = ?",
                        new Object[]{confirmKey});

            }
        });
    }

    @SuppressWarnings("unchecked")
    public Set<String> getKeys() {
        return (LinkedHashSet<String>) transactionTemplateReadOnly.execute(new TransactionCallback() {
            public LinkedHashSet<String> doInTransaction(TransactionStatus status) {
                List<String> keys = jdbcTemplate.query("SELECT " + ID + " FROM " + getRepositoryName(),
                        new RowMapper<String>() {
                            public String mapRow(ResultSet rs, int rowNum) throws SQLException {
                                String id = rs.getString(ID);
                                if (LOG.isTraceEnabled()) {
                                    LOG.trace("getKey [" + id + "]");
                                }
                                return id;
                            }
                        });
                return new LinkedHashSet<String>(keys);
            }
        });
    }

    @SuppressWarnings("unchecked")
    public Set<String> scan(CamelContext camelContext) {
        return (LinkedHashSet<String>) transactionTemplateReadOnly.execute(new TransactionCallback() {
            public LinkedHashSet<String> doInTransaction(TransactionStatus status) {
                List<String> keys = jdbcTemplate.query("SELECT " + ID + " FROM " + getRepositoryNameCompleted(),
                        new RowMapper<String>() {
                            public String mapRow(ResultSet rs, int rowNum) throws SQLException {
                                String id = rs.getString(ID);
                                if (LOG.isTraceEnabled()) {
                                    LOG.trace("getKey [" + id + "]");
                                }
                                return id;
                            }
                        });
                return new LinkedHashSet<String>(keys);
            }
        });
    }

    public Exchange recover(CamelContext camelContext, String exchangeId) {
        final String key = exchangeId;
        Exchange answer = get(key, getRepositoryNameCompleted(), camelContext);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Recovering exchangeId [" + key + "] -> " + answer);
        }

        return answer;
    }

    public void setRecoveryInterval(long interval, TimeUnit timeUnit) {
        this.recoveryInterval = timeUnit.toMillis(interval);
    }

    public void setRecoveryInterval(long interval) {
        this.recoveryInterval = interval;
    }

    public long getRecoveryIntervalInMillis() {
        return recoveryInterval;
    }

    public boolean isUseRecovery() {
        return useRecovery;
    }

    public void setUseRecovery(boolean useRecovery) {
        this.useRecovery = useRecovery;
    }

    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    public void setMaximumRedeliveries(int maximumRedeliveries) {
        this.maximumRedeliveries = maximumRedeliveries;
    }

    public String getDeadLetterUri() {
        return deadLetterUri;
    }

    public void setDeadLetterUri(String deadLetterUri) {
        this.deadLetterUri = deadLetterUri;
    }

    public boolean isReturnOldExchange() {
        return returnOldExchange;
    }

    public void setReturnOldExchange(boolean returnOldExchange) {
        this.returnOldExchange = returnOldExchange;
    }

    /**
     * @return the lobHandler
     */
    public LobHandler getLobHandler() {
        return lobHandler;
    }

    /**
     * @param lobHandler the lobHandler to set
     */
    public void setLobHandler(LobHandler lobHandler) {
        this.lobHandler = lobHandler;
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

        // log number of existing exchanges
        int current = getKeys().size();
        int completed = scan(null).size();

        if (current > 0) {
            LOG.info("On startup there are " + current + " aggregate exchanges (not completed) in repository: " + getRepositoryName());
        } else {
            LOG.info("On startup there are no existing aggregate exchanges (not completed) in repository: " + getRepositoryName());
        }
        if (completed > 0) {
            LOG.warn("On startup there are " + completed + " completed exchanges to be recovered in repository: " + getRepositoryNameCompleted());
        } else {
            LOG.info("On startup there are no completed exchanges to be recovered in repository: " + getRepositoryNameCompleted());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

}

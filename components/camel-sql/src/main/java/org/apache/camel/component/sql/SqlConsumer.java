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
package org.apache.camel.component.sql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import static org.springframework.jdbc.support.JdbcUtils.closeResultSet;

public class SqlConsumer extends ScheduledBatchPollingConsumer {

    private final String query;
    private final JdbcTemplate jdbcTemplate;
    private final SqlPrepareStatementStrategy sqlPrepareStatementStrategy;
    private final SqlProcessingStrategy sqlProcessingStrategy;

    @UriParam
    private String onConsume;
    @UriParam
    private String onConsumeFailed;
    @UriParam
    private String onConsumeBatchComplete;
    @UriParam
    private boolean useIterator = true;
    @UriParam
    private boolean routeEmptyResultSet;
    @UriParam
    private int expectedUpdateCount = -1;
    @UriParam
    private boolean breakBatchOnConsumeFail;

    private static final class DataHolder {
        private Exchange exchange;
        private Object data;

        private DataHolder() {
        }
    }

    public SqlConsumer(SqlEndpoint endpoint, Processor processor, JdbcTemplate jdbcTemplate, String query,
                       SqlPrepareStatementStrategy sqlPrepareStatementStrategy, SqlProcessingStrategy sqlProcessingStrategy) {
        super(endpoint, processor);
        this.jdbcTemplate = jdbcTemplate;
        this.query = query;
        this.sqlPrepareStatementStrategy = sqlPrepareStatementStrategy;
        this.sqlProcessingStrategy = sqlProcessingStrategy;
    }

    @Override
    public SqlEndpoint getEndpoint() {
        return (SqlEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        // must reset for each poll
        shutdownRunningTask = null;
        pendingExchanges = 0;

        final String preparedQuery = sqlPrepareStatementStrategy.prepareQuery(query, getEndpoint().isAllowNamedParameters());

        Integer messagePolled = jdbcTemplate.execute(preparedQuery, new PreparedStatementCallback<Integer>() {
            @Override
            public Integer doInPreparedStatement(PreparedStatement preparedStatement) throws SQLException, DataAccessException {
                Queue<DataHolder> answer = new LinkedList<DataHolder>();

                log.debug("Executing query: {}", preparedQuery);
                ResultSet rs = preparedStatement.executeQuery();
                SqlOutputType outputType = getEndpoint().getOutputType();
                try {
                    log.trace("Got result list from query: {}, outputType={}", rs, outputType);
                    if (outputType == SqlOutputType.SelectList) {
                        List<?> data = getEndpoint().queryForList(rs, true);
                        addListToQueue(data, answer);
                    } else if (outputType == SqlOutputType.SelectOne) {
                        Object data = getEndpoint().queryForObject(rs);
                        if (data != null) {
                            addListToQueue(data, answer);
                        }
                    } else {
                        throw new IllegalArgumentException("Invalid outputType=" + outputType);
                    }
                } finally {
                    closeResultSet(rs);
                }

                // process all the exchanges in this batch
                try {
                    int rows = processBatch(CastUtils.cast(answer));
                    return rows;
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }
        });

        return messagePolled;
    }

    private void addListToQueue(Object data, Queue<DataHolder> answer) {
        if (data instanceof List) {
            // create a list of exchange objects with the data
            List<?> list = (List)data;
            if (useIterator) {
                for (Object item : list) {
                    addItemToQueue(item, answer);
                }
            } else if (!list.isEmpty() || routeEmptyResultSet) {
                addItemToQueue(list, answer);
            }
        } else {
            // create single object as data
            addItemToQueue(data, answer);
        }
    }
    private void addItemToQueue(Object item, Queue<DataHolder> answer) {
        Exchange exchange = createExchange(item);
        DataHolder holder = new DataHolder();
        holder.exchange = exchange;
        holder.data = item;
        answer.add(holder);
    }

    protected Exchange createExchange(Object data) {
        final Exchange exchange = getEndpoint().createExchange(ExchangePattern.InOnly);
        Message msg = exchange.getIn();
        if (getEndpoint().getOutputHeader() != null) {
            msg.setHeader(getEndpoint().getOutputHeader(), data);
        } else {
            msg.setBody(data);
        }
        return exchange;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        // limit if needed
        if (maxMessagesPerPoll > 0 && total == maxMessagesPerPoll) {
            log.debug("Limiting to maximum messages to poll " + maxMessagesPerPoll + " as there was more messages in this poll.");
        }

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            DataHolder holder = ObjectHelper.cast(DataHolder.class, exchanges.poll());
            Exchange exchange = holder.exchange;
            Object data = holder.data;

            // add current index and total as properties
            exchange.setProperty(Exchange.BATCH_INDEX, index);
            exchange.setProperty(Exchange.BATCH_SIZE, total);
            exchange.setProperty(Exchange.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            // process the current exchange
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            // pick the on consume to use
            String sql = exchange.isFailed() ? onConsumeFailed : onConsume;
            try {
                // we can only run on consume if there was data
                if (data != null && sql != null) {
                    int updateCount = sqlProcessingStrategy.commit(getEndpoint(), exchange, data, jdbcTemplate, sql);
                    if (expectedUpdateCount > -1 && updateCount != expectedUpdateCount) {
                        String msg = "Expected update count " + expectedUpdateCount + " but was " + updateCount + " executing query: " + sql;
                        throw new SQLException(msg);
                    }
                }
            } catch (Exception e) {
                if (breakBatchOnConsumeFail) {
                    throw e;
                } else {
                    handleException("Error executing onConsume/onConsumeFailed query " + sql, e);
                }
            }
        }

        try {
            if (onConsumeBatchComplete != null) {
                int updateCount = sqlProcessingStrategy.commitBatchComplete(getEndpoint(), jdbcTemplate, onConsumeBatchComplete);
                log.debug("onConsumeBatchComplete update count {}", updateCount);
            }
        } catch (Exception e) {
            if (breakBatchOnConsumeFail) {
                throw e;
            } else {
                handleException("Error executing onConsumeBatchComplete query " + onConsumeBatchComplete, e);
            }
        }

        return total;
    }

    public String getOnConsume() {
        return onConsume;
    }

    /**
     * Sets a SQL to execute when the row has been successfully processed.
     */
    public void setOnConsume(String onConsume) {
        this.onConsume = onConsume;
    }

    public String getOnConsumeFailed() {
        return onConsumeFailed;
    }

    /**
     * Sets a SQL to execute when the row failed being processed.
     */
    public void setOnConsumeFailed(String onConsumeFailed) {
        this.onConsumeFailed = onConsumeFailed;
    }

    public String getOnConsumeBatchComplete() {
        return onConsumeBatchComplete;
    }

    public void setOnConsumeBatchComplete(String onConsumeBatchComplete) {
        this.onConsumeBatchComplete = onConsumeBatchComplete;
    }

    /**
     * Indicates how resultset should be delivered to the route
     */
    public boolean isUseIterator() {
        return useIterator;
    }

    /**
     * Sets how resultset should be delivered to route.
     * Indicates delivery as either a list or individual object.
     * defaults to true.
     */
    public void setUseIterator(boolean useIterator) {
        this.useIterator = useIterator;
    }

    /**
     * Indicates whether empty resultset should be allowed to be sent to the next hop or not
     */
    public boolean isRouteEmptyResultSet() {
        return routeEmptyResultSet;
    }

    /**
     * Sets whether empty resultset should be allowed to be sent to the next hop.
     * defaults to false. So the empty resultset will be filtered out.
     */
    public void setRouteEmptyResultSet(boolean routeEmptyResultSet) {
        this.routeEmptyResultSet = routeEmptyResultSet;
    }

    public int getExpectedUpdateCount() {
        return expectedUpdateCount;
    }

    /**
     * Sets an expected update count to validate when using onConsume.
     *
     * @param expectedUpdateCount typically set this value to <tt>1</tt> to expect 1 row updated.
     */
    public void setExpectedUpdateCount(int expectedUpdateCount) {
        this.expectedUpdateCount = expectedUpdateCount;
    }

    public boolean isBreakBatchOnConsumeFail() {
        return breakBatchOnConsumeFail;
    }

    /**
     * Sets whether to break batch if onConsume failed.
     */
    public void setBreakBatchOnConsumeFail(boolean breakBatchOnConsumeFail) {
        this.breakBatchOnConsumeFail = breakBatchOnConsumeFail;
    }

    @Override
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        super.setMaxMessagesPerPoll(maxMessagesPerPoll);

        if (jdbcTemplate != null) {
            jdbcTemplate.setMaxRows(maxMessagesPerPoll);
        }
    }
}

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
import java.util.Map;
import java.util.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledBatchPollingConsumer;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.ObjectHelper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;

/**
 *
 */
public class SqlConsumer extends ScheduledBatchPollingConsumer {

    private final String query;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Statement to run after data has been processed in the route
     */
    private String onConsume;

    /**
     * Process resultset individually or as a list
     */
    private boolean useIterator = true;

    /**
     * Whether allow empty resultset to be routed to the next hop
     */
    private boolean routeEmptyResultSet;

    private static final class DataHolder {
        private Exchange exchange;
        private Object data;

        private DataHolder() {
        }
    }

    public SqlConsumer(SqlEndpoint endpoint, Processor processor, JdbcTemplate jdbcTemplate, String query) {
        super(endpoint, processor);
        this.jdbcTemplate = jdbcTemplate;
        this.query = query;
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

        final String preparedQuery = getEndpoint().getPrepareStatementStrategy().prepareQuery(query, getEndpoint().isAllowNamedParameters());

        Integer messagePolled = jdbcTemplate.execute(preparedQuery, new PreparedStatementCallback<Integer>() {
            @Override
            public Integer doInPreparedStatement(PreparedStatement preparedStatement) throws SQLException, DataAccessException {
                Queue<DataHolder> answer = new LinkedList<DataHolder>();

                ResultSet rs = preparedStatement.executeQuery();
                try {
                    log.trace("Got result list from query {}", rs);

                    RowMapperResultSetExtractor<Map<String, Object>> mapper = new RowMapperResultSetExtractor<Map<String, Object>>(new ColumnMapRowMapper());
                    List<Map<String, Object>> data = mapper.extractData(rs);

                    // create a list of exchange objects with the data
                    if (useIterator) {
                        for (Map<String, Object> item : data) {
                            Exchange exchange = createExchange(item);
                            DataHolder holder = new DataHolder();
                            holder.exchange = exchange;
                            holder.data = item;
                            answer.add(holder);
                        }
                    } else {
                        if (!data.isEmpty() || routeEmptyResultSet) {
                            Exchange exchange = createExchange(data);
                            DataHolder holder = new DataHolder();
                            holder.exchange = exchange;
                            holder.data = data;
                            answer.add(holder);
                        }
                    }
                } finally {
                    rs.close();
                }

                // process all the exchanges in this batch
                try {
                    int rows = processBatch(CastUtils.cast(answer));
                    return Integer.valueOf(rows);
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            }
        });

        return messagePolled;
    }

    protected Exchange createExchange(Object data) {
        final Exchange exchange = getEndpoint().createExchange(ExchangePattern.InOnly);
        Message msg = exchange.getIn();
        msg.setBody(data);
        return exchange;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();

        // limit if needed
        if (maxMessagesPerPoll > 0 && total > maxMessagesPerPoll) {
            log.debug("Limiting to maximum messages to poll " + maxMessagesPerPoll + " as there was " + total + " messages in this poll.");
            total = maxMessagesPerPoll;
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
            log.debug("Processing exchange: {} with properties: {}", exchange, exchange.getProperties());
            getProcessor().process(exchange);

            // TODO: support when with CAMEL-5977
            /*
            try {
                if (onConsume != null) {
                    SqlEndpoint endpoint = (SqlEndpoint) getEndpoint();
                    endpoint.getProcessingStrategy().commit(endpoint, exchange, data, jdbcTemplate, onConsume);
                }
            } catch (Exception e) {
                handleException(e);
            }*/
        }

        return total;
    }

    /**
     * Gets the statement(s) to run after successful processing.
     * Use comma to separate multiple statements.
     */
    public String getOnConsume() {
        return onConsume;
    }

    /**
     * Sets the statement to run after successful processing.
     * Use comma to separate multiple statements.
     */
    public void setOnConsume(String onConsume) {
        this.onConsume = onConsume;
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

}


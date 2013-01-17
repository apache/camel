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

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SQL Endpoint. Endpoint URI should contain valid SQL statement, but instead of
 * question marks (that are parameter placeholders), sharp signs should be used.
 * This is because in camel question mark has other meaning.
 */
public class SqlEndpoint extends DefaultPollingEndpoint {
    private JdbcTemplate jdbcTemplate;
    private String query;
    private boolean batch;
    private int maxMessagesPerPoll;
    private SqlProcessingStrategy processingStrategy = new DefaultSqlProcessingStrategy();
    private SqlPrepareStatementStrategy prepareStatementStrategy = new DefaultSqlPrepareStatementStrategy();
    private String onConsume;
    private boolean allowNamedParameters = true;

    // TODO: onConsumeBatchDone to execute a query when batch done

    public SqlEndpoint() {
    }

    public SqlEndpoint(String uri, Component component, JdbcTemplate jdbcTemplate, String query) {
        super(uri, component);
        this.jdbcTemplate = jdbcTemplate;
        this.query = query;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        SqlConsumer consumer = new SqlConsumer(this, processor, jdbcTemplate, query);
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        consumer.setOnConsume(getOnConsume());
        configureConsumer(consumer);
        return consumer;
    }

    public Producer createProducer() throws Exception {
        return new SqlProducer(this, query, jdbcTemplate, batch);
    }

    public boolean isSingleton() {
        return true;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isBatch() {
        return batch;
    }

    public void setBatch(boolean batch) {
        this.batch = batch;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    public SqlProcessingStrategy getProcessingStrategy() {
        return processingStrategy;
    }

    public void setProcessingStrategy(SqlProcessingStrategy processingStrategy) {
        this.processingStrategy = processingStrategy;
    }

    public SqlPrepareStatementStrategy getPrepareStatementStrategy() {
        return prepareStatementStrategy;
    }

    public void setPrepareStatementStrategy(SqlPrepareStatementStrategy prepareStatementStrategy) {
        this.prepareStatementStrategy = prepareStatementStrategy;
    }

    public String getOnConsume() {
        return onConsume;
    }

    public void setOnConsume(String onConsume) {
        this.onConsume = onConsume;
    }

    public boolean isAllowNamedParameters() {
        return allowNamedParameters;
    }

    public void setAllowNamedParameters(boolean allowNamedParameters) {
        this.allowNamedParameters = allowNamedParameters;
    }

    @Override
    protected String createEndpointUri() {
        // Make sure it's properly encoded
        return "sql:" + UnsafeUriCharactersEncoder.encode(query);
    }
}

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
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * SQL Endpoint. Endpoint URI should contain valid SQL statement, but instead of
 * question marks (that are parameter placeholders), sharp signs should be used.
 * This is because in camel question mark has other meaning.
 */
@UriEndpoint(scheme = "sql", consumerClass = SqlConsumer.class)
public class SqlEndpoint extends DefaultPollingEndpoint {
    private JdbcTemplate jdbcTemplate;
    @UriPath
    private String query;
    @UriParam
    private boolean batch;
    @UriParam
    private int maxMessagesPerPoll;
    @UriParam
    private SqlProcessingStrategy processingStrategy;
    @UriParam
    private SqlPrepareStatementStrategy prepareStatementStrategy;
    @UriParam
    private String onConsume;
    @UriParam
    private String onConsumeFailed;
    @UriParam
    private String onConsumeBatchComplete;
    @UriParam
    private boolean allowNamedParameters = true;
    @UriParam
    private boolean alwaysPopulateStatement;
    @UriParam
    private char separator = ',';

    public SqlEndpoint() {
    }

    public SqlEndpoint(String uri, Component component, JdbcTemplate jdbcTemplate, String query) {
        super(uri, component);
        this.jdbcTemplate = jdbcTemplate;
        this.query = query;
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        SqlPrepareStatementStrategy prepareStrategy = prepareStatementStrategy != null ? prepareStatementStrategy : new DefaultSqlPrepareStatementStrategy(separator);
        SqlProcessingStrategy proStrategy = processingStrategy != null ? processingStrategy : new DefaultSqlProcessingStrategy(prepareStrategy);
        SqlConsumer consumer = new SqlConsumer(this, processor, jdbcTemplate, query, prepareStrategy, proStrategy);
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        consumer.setOnConsume(getOnConsume());
        consumer.setOnConsumeFailed(getOnConsumeFailed());
        consumer.setOnConsumeBatchComplete(getOnConsumeBatchComplete());
        configureConsumer(consumer);
        return consumer;
    }

    public Producer createProducer() throws Exception {
        SqlPrepareStatementStrategy prepareStrategy = prepareStatementStrategy != null ? prepareStatementStrategy : new DefaultSqlPrepareStatementStrategy(separator);
        return new SqlProducer(this, query, jdbcTemplate, prepareStrategy, batch, alwaysPopulateStatement);
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

    /**
     * Sets the SQL query to perform
     */
    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isBatch() {
        return batch;
    }

    /**
     * Enables or disables batch mode
     */
    public void setBatch(boolean batch) {
        this.batch = batch;
    }

    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    /**
     * Sets the maximum number of messages to poll
     */
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

    public String getOnConsumeFailed() {
        return onConsumeFailed;
    }

    public void setOnConsumeFailed(String onConsumeFailed) {
        this.onConsumeFailed = onConsumeFailed;
    }

    public String getOnConsumeBatchComplete() {
        return onConsumeBatchComplete;
    }

    public void setOnConsumeBatchComplete(String onConsumeBatchComplete) {
        this.onConsumeBatchComplete = onConsumeBatchComplete;
    }

    public boolean isAllowNamedParameters() {
        return allowNamedParameters;
    }

    public void setAllowNamedParameters(boolean allowNamedParameters) {
        this.allowNamedParameters = allowNamedParameters;
    }

    public boolean isAlwaysPopulateStatement() {
        return alwaysPopulateStatement;
    }

    public void setAlwaysPopulateStatement(boolean alwaysPopulateStatement) {
        this.alwaysPopulateStatement = alwaysPopulateStatement;
    }

    public char getSeparator() {
        return separator;
    }

    public void setSeparator(char separator) {
        this.separator = separator;
    }

    @Override
    protected String createEndpointUri() {
        // Make sure it's properly encoded
        return "sql:" + UnsafeUriCharactersEncoder.encode(query);
    }
}

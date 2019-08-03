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
package org.apache.camel.component.sql;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The sql component allows you to work with databases using JDBC SQL queries.
 */
@UriEndpoint(firstVersion = "1.4.0", scheme = "sql", title = "SQL", syntax = "sql:query", label = "database,sql")
public class SqlEndpoint extends DefaultSqlEndpoint {

    @UriPath(description = "Sets the SQL query to perform. You can externalize the query by using file: or classpath: as prefix and specify the location of the file.")
    @Metadata(required = true)
    private String query;

    public SqlEndpoint() {
    }

    public SqlEndpoint(String uri, Component component, JdbcTemplate jdbcTemplate, String query) {
        super(uri, component, jdbcTemplate);
        this.query = query;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        SqlPrepareStatementStrategy prepareStrategy = getPrepareStatementStrategy() != null ? getPrepareStatementStrategy() : new DefaultSqlPrepareStatementStrategy(getSeparator());
        SqlProcessingStrategy proStrategy = getProcessingStrategy() != null ? getProcessingStrategy() : new DefaultSqlProcessingStrategy(prepareStrategy);
        SqlConsumer consumer = new SqlConsumer(this, processor, getJdbcTemplate(), query, prepareStrategy, proStrategy);
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());
        consumer.setOnConsume(getOnConsume());
        consumer.setOnConsumeFailed(getOnConsumeFailed());
        consumer.setOnConsumeBatchComplete(getOnConsumeBatchComplete());
        consumer.setBreakBatchOnConsumeFail(isBreakBatchOnConsumeFail());
        consumer.setExpectedUpdateCount(getExpectedUpdateCount());
        consumer.setUseIterator(isUseIterator());
        consumer.setRouteEmptyResultSet(isRouteEmptyResultSet());
        consumer.setParametersCount(getParametersCount());
        consumer.setAlwaysPopulateStatement(isAlwaysPopulateStatement());
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        SqlPrepareStatementStrategy prepareStrategy = getPrepareStatementStrategy() != null ? getPrepareStatementStrategy() : new DefaultSqlPrepareStatementStrategy(getSeparator());
        SqlProducer result = new SqlProducer(this, query, getJdbcTemplate(), prepareStrategy, isBatch(),
                isAlwaysPopulateStatement(), isUseMessageBodyForSql());
        result.setParametersCount(getParametersCount());
        return result;
    }

    @Override
    protected String createEndpointUri() {
        // Make sure it's properly encoded
        return "sql:" + UnsafeUriCharactersEncoder.encode(query);
    }

    public String getQuery() {
        return query;
    }

    /**
     * Sets the SQL query to perform. You can externalize the query by using file: or classpath: as prefix and specify the location of the file.
     */
    public void setQuery(String query) {
        this.query = query;
    }

}

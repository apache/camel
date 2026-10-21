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

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.UnsafeUriCharactersEncoder;

/**
 * Perform SQL queries using Spring JDBC.
 */
@UriEndpoint(firstVersion = "1.4.0", scheme = "sql", title = "SQL", syntax = "sql:query",
             category = { Category.DATABASE }, headersClass = SqlConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.MAP, javaType = "java.util.Map<String, Object>",
                                   dependsOn = { "outputClass", "outputHeader" },
                                   byOption = {
                                           @BodyType.When(option = "useIterator", value = "false", carrier = Carrier.LIST),
                                           @BodyType.When(option = "outputType", value = "SelectOne",
                                                          carrier = { Carrier.MAP, Carrier.VALUE }),
                                           @BodyType.When(option = "outputType", value = "StreamList",
                                                          carrier = Carrier.ITERATOR) },
                                   description = "A row of the query: a Map of the column names and their values"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The parameters of the query: a Map for the named ones, a List or an array"
                                                + " for the # ones; the query itself with useMessageBodyForSql"),
           producerOut = @BodyType(value = { Carrier.LIST, Carrier.UNCHANGED },
                                   dependsOn = { "outputClass", "outputHeader", "noop" },
                                   byOption = {
                                           @BodyType.When(option = "outputType", value = "SelectOne",
                                                          carrier = { Carrier.MAP, Carrier.VALUE, Carrier.NULL }),
                                           @BodyType.When(option = "outputType", value = "StreamList",
                                                          carrier = Carrier.ITERATOR) },
                                   description = "The rows of a select: a List of Maps of the column names and their values."
                                                 + " A query that is not a select leaves the body unchanged, with the count"
                                                 + " in the CamelSqlUpdateCount header"))
public class SqlEndpoint extends DefaultSqlEndpoint {

    @UriPath(description = "Sets the SQL query to perform. You can externalize the query by using file: or classpath: as prefix and specify the location of the file.")
    @Metadata(required = true, supportFileReference = true, largeInput = true, inputLanguage = "sql")
    private String query;
    @UriParam(label = "producer", defaultValue = "true",
              description = "Whether to optimize batch by turning off auto-commit which can dramatic improve performance, and instead execute as a manual commit after the entire batch operation is complete")
    private boolean batchAutoCommitDisabled = true;

    public SqlEndpoint() {
    }

    public SqlEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        SqlPrepareStatementStrategy prepareStrategy = getPrepareStatementStrategy() != null
                ? getPrepareStatementStrategy() : new DefaultSqlPrepareStatementStrategy(getSeparator());
        SqlProcessingStrategy proStrategy
                = getProcessingStrategy() != null ? getProcessingStrategy() : new DefaultSqlProcessingStrategy(prepareStrategy);
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
        SqlPrepareStatementStrategy prepareStrategy = getPrepareStatementStrategy() != null
                ? getPrepareStatementStrategy() : new DefaultSqlPrepareStatementStrategy(getSeparator());
        SqlProducer result = new SqlProducer(
                this, query, getJdbcTemplate(), prepareStrategy, isBatch(),
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
     * Sets the SQL query to perform. You can externalize the query by using file: or classpath: as prefix and specify
     * the location of the file.
     */
    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isBatchAutoCommitDisabled() {
        return batchAutoCommitDisabled;
    }

    /**
     * Whether to optimize batch by turning off auto-commit which can dramatic improve performance, and instead execute
     * as a manual commit after the entire batch operation is complete
     */
    public void setBatchAutoCommitDisabled(boolean batchAutoCommitDisabled) {
        this.batchAutoCommitDisabled = batchAutoCommitDisabled;
    }
}

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

import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.UnsafeUriCharactersEncoder;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;

/**
 * SQL Endpoint. Endpoint URI should contain valid SQL statement, but instead of
 * question marks (that are parameter placeholders), sharp signs should be used.
 * This is because in camel question mark has other meaning.
 */
@UriEndpoint(scheme = "sql", title = "SQL", syntax = "sql:query", consumerClass = SqlConsumer.class, label = "database,sql")
public class SqlEndpoint extends DefaultPollingEndpoint {
    private JdbcTemplate jdbcTemplate;

    @UriPath @Metadata(required = "true")
    private String query;
    @UriParam
    @Deprecated
    private String dataSourceRef;
    @UriParam
    private DataSource dataSource;
    @UriParam(label = "producer")
    private boolean batch;
    @UriParam(label = "consumer")
    private int maxMessagesPerPoll;
    @UriParam(label = "consumer,advanced")
    private SqlProcessingStrategy processingStrategy;
    @UriParam(label = "advanced")
    private SqlPrepareStatementStrategy prepareStatementStrategy;
    @UriParam(label = "consumer")
    private String onConsume;
    @UriParam(label = "consumer")
    private String onConsumeFailed;
    @UriParam(label = "consumer")
    private String onConsumeBatchComplete;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean useIterator = true;
    @UriParam(label = "consumer")
    private boolean routeEmptyResultSet;
    @UriParam(label = "consumer", defaultValue = "-1")
    private int expectedUpdateCount = -1;
    @UriParam(label = "consumer")
    private boolean breakBatchOnConsumeFail;
    @UriParam(defaultValue = "true")
    private boolean allowNamedParameters = true;
    @UriParam(label = "producer,advanced")
    private boolean alwaysPopulateStatement;
    @UriParam(defaultValue = ",")
    private char separator = ',';
    @UriParam(defaultValue = "SelectList")
    private SqlOutputType outputType = SqlOutputType.SelectList;
    @UriParam
    private String outputClass;
    @UriParam(label = "producer,advanced")
    private int parametersCount;
    @UriParam(label = "producer")
    private boolean noop;
    @UriParam
    private String outputHeader;
    @UriParam(label = "producer")
    private boolean useMessageBodyForSql;

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
        consumer.setBreakBatchOnConsumeFail(isBreakBatchOnConsumeFail());
        consumer.setExpectedUpdateCount(getExpectedUpdateCount());
        consumer.setUseIterator(isUseIterator());
        consumer.setRouteEmptyResultSet(isRouteEmptyResultSet());
        configureConsumer(consumer);
        return consumer;
    }

    public Producer createProducer() throws Exception {
        SqlPrepareStatementStrategy prepareStrategy = prepareStatementStrategy != null ? prepareStatementStrategy : new DefaultSqlPrepareStatementStrategy(separator);
        SqlProducer result = new SqlProducer(this, query, jdbcTemplate, prepareStrategy, batch, alwaysPopulateStatement, useMessageBodyForSql);
        result.setParametersCount(parametersCount);
        return result;
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

    /**
     * Allows to plugin to use a custom org.apache.camel.component.sql.SqlProcessingStrategy to execute queries when the consumer has processed the rows/batch.
     */
    public void setProcessingStrategy(SqlProcessingStrategy processingStrategy) {
        this.processingStrategy = processingStrategy;
    }

    public SqlPrepareStatementStrategy getPrepareStatementStrategy() {
        return prepareStatementStrategy;
    }

    /**
     * Allows to plugin to use a custom org.apache.camel.component.sql.SqlPrepareStatementStrategy to control preparation of the query and prepared statement.
     */
    public void setPrepareStatementStrategy(SqlPrepareStatementStrategy prepareStatementStrategy) {
        this.prepareStatementStrategy = prepareStatementStrategy;
    }

    public String getOnConsume() {
        return onConsume;
    }

    /**
     * After processing each row then this query can be executed, if the Exchange was processed successfully, for example to mark the row as processed. The query can have parameter.
     */
    public void setOnConsume(String onConsume) {
        this.onConsume = onConsume;
    }

    public String getOnConsumeFailed() {
        return onConsumeFailed;
    }

    /**
     * After processing each row then this query can be executed, if the Exchange failed, for example to mark the row as failed. The query can have parameter.
     */
    public void setOnConsumeFailed(String onConsumeFailed) {
        this.onConsumeFailed = onConsumeFailed;
    }

    public String getOnConsumeBatchComplete() {
        return onConsumeBatchComplete;
    }

    /**
     * After processing the entire batch, this query can be executed to bulk update rows etc. The query cannot have parameters.
     */
    public void setOnConsumeBatchComplete(String onConsumeBatchComplete) {
        this.onConsumeBatchComplete = onConsumeBatchComplete;
    }

    public boolean isAllowNamedParameters() {
        return allowNamedParameters;
    }

    /**
     * Whether to allow using named parameters in the queries.
     */
    public void setAllowNamedParameters(boolean allowNamedParameters) {
        this.allowNamedParameters = allowNamedParameters;
    }

    public boolean isAlwaysPopulateStatement() {
        return alwaysPopulateStatement;
    }

    /**
     * If enabled then the populateStatement method from org.apache.camel.component.sql.SqlPrepareStatementStrategy is always invoked,
     * also if there is no expected parameters to be prepared. When this is false then the populateStatement is only invoked if there
     * is 1 or more expected parameters to be set; for example this avoids reading the message body/headers for SQL queries with no parameters.
     */
    public void setAlwaysPopulateStatement(boolean alwaysPopulateStatement) {
        this.alwaysPopulateStatement = alwaysPopulateStatement;
    }

    public char getSeparator() {
        return separator;
    }

    /**
     * The separator to use when parameter values is taken from message body (if the body is a String type), to be inserted at # placeholders.
     * Notice if you use named parameters, then a Map type is used instead.
     * <p/>
     * The default value is ,
     */
    public void setSeparator(char separator) {
        this.separator = separator;
    }

    public SqlOutputType getOutputType() {
        return outputType;
    }

    /**
     * Make the output of consumer or producer to SelectList as List of Map, or SelectOne as single Java object in the following way:
     * a) If the query has only single column, then that JDBC Column object is returned. (such as SELECT COUNT( * ) FROM PROJECT will return a Long object.
     * b) If the query has more than one column, then it will return a Map of that result.
     * c) If the outputClass is set, then it will convert the query result into an Java bean object by calling all the setters that match the column names. 
     * It will assume your class has a default constructor to create instance with.
     * d) If the query resulted in more than one rows, it throws an non-unique result exception.
     */
    public void setOutputType(SqlOutputType outputType) {
        this.outputType = outputType;
    }

    public String getOutputClass() {
        return outputClass;
    }

    /**
     * Specify the full package and class name to use as conversion when outputType=SelectOne.
     */
    public void setOutputClass(String outputClass) {
        this.outputClass = outputClass;
    }

    public int getParametersCount() {
        return parametersCount;
    }

    /**
     * If set greater than zero, then Camel will use this count value of parameters to replace instead of querying via JDBC metadata API.
     * This is useful if the JDBC vendor could not return correct parameters count, then user may override instead.
     */
    public void setParametersCount(int parametersCount) {
        this.parametersCount = parametersCount;
    }

    public boolean isNoop() {
        return noop;
    }

    /**
     * If set, will ignore the results of the SQL query and use the existing IN message as the OUT message for the continuation of processing
     */
    public void setNoop(boolean noop) {
        this.noop = noop;
    }

    public String getOutputHeader() {
        return outputHeader;
    }

    /**
     * Store the query result in a header instead of the message body.
     * By default, outputHeader == null and the query result is stored in the message body,
     * any existing content in the message body is discarded.
     * If outputHeader is set, the value is used as the name of the header to store the
     * query result and the original message body is preserved.
     */
    public void setOutputHeader(String outputHeader) {
        this.outputHeader = outputHeader;
    }

    public boolean isUseMessageBodyForSql() {
        return useMessageBodyForSql;
    }

    /**
     * Whether to use the message body as the SQL and then headers for parameters.
     * <p/>
     * If this option is enabled then the SQL in the uri is not used.
     */
    public void setUseMessageBodyForSql(boolean useMessageBodyForSql) {
        this.useMessageBodyForSql = useMessageBodyForSql;
    }

    public String getDataSourceRef() {
        return dataSourceRef;
    }

    /**
     * Sets the reference to a DataSource to lookup from the registry, to use for communicating with the database.
     */
    public void setDataSourceRef(String dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Sets the DataSource to use to communicate with the database.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

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
    protected String createEndpointUri() {
        // Make sure it's properly encoded
        return "sql:" + UnsafeUriCharactersEncoder.encode(query);
    }

    @SuppressWarnings("unchecked")
    protected List<?> queryForList(ResultSet rs, boolean allowMapToClass) throws SQLException {
        if (allowMapToClass && outputClass != null) {
            Class<?> outputClazz = getCamelContext().getClassResolver().resolveClass(outputClass);
            RowMapper rowMapper = new BeanPropertyRowMapper(outputClazz);
            RowMapperResultSetExtractor<?> mapper = new RowMapperResultSetExtractor(rowMapper);
            List<?> data = mapper.extractData(rs);
            return data;
        } else {
            ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();
            RowMapperResultSetExtractor<Map<String, Object>> mapper = new RowMapperResultSetExtractor<Map<String, Object>>(rowMapper);
            List<Map<String, Object>> data = mapper.extractData(rs);
            return data;
        }
    }

    @SuppressWarnings("unchecked")
    protected Object queryForObject(ResultSet rs) throws SQLException {
        Object result = null;
        if (outputClass == null) {
            RowMapper rowMapper = new ColumnMapRowMapper();
            RowMapperResultSetExtractor<Map<String, Object>> mapper = new RowMapperResultSetExtractor<Map<String, Object>>(rowMapper);
            List<Map<String, Object>> data = mapper.extractData(rs);
            if (data.size() > 1) {
                throw new SQLDataException("Query result not unique for outputType=SelectOne. Got " + data.size() +  " count instead.");
            } else if (data.size() == 1) {
                // Set content depend on number of column from query result
                Map<String, Object> row = data.get(0);
                if (row.size() == 1) {
                    result = row.values().iterator().next();
                } else {
                    result = row;
                }
            }
        } else {
            Class<?> outputClzz = getCamelContext().getClassResolver().resolveClass(outputClass);
            RowMapper rowMapper = new BeanPropertyRowMapper(outputClzz);
            RowMapperResultSetExtractor<?> mapper = new RowMapperResultSetExtractor(rowMapper);
            List<?> data = mapper.extractData(rs);
            if (data.size() > 1) {
                throw new SQLDataException("Query result not unique for outputType=SelectOne. Got " + data.size() +  " count instead.");
            } else if (data.size() == 1) {
                result = data.get(0);
            }
        }

        // If data.size is zero, let result be null.
        return result;
    }

}

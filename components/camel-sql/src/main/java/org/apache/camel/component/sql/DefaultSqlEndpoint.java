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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.Component;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultPollingEndpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;

/**
 * Base class for SQL endpoints.
 */
public abstract class DefaultSqlEndpoint extends DefaultPollingEndpoint {
    private JdbcTemplate jdbcTemplate;

    @Metadata(autowired = true)
    @UriParam(description = "Sets the DataSource to use to communicate with the database at endpoint level.")
    private DataSource dataSource;
    @UriParam(label = "consumer",
              description = "Enables or disables transaction. If enabled then if processing an exchange failed then the consumer"
                            + " breaks out processing any further exchanges to cause a rollback eager.")
    private boolean transacted;
    @UriParam(label = "producer", description = "Enables or disables batch mode")
    private boolean batch;
    @UriParam(label = "consumer", description = "Sets the maximum number of messages to poll")
    private int maxMessagesPerPoll;
    @UriParam(label = "consumer,advanced",
              description = "Allows to plugin to use a custom org.apache.camel.component.sql.SqlProcessingStrategy to execute queries when the consumer has processed the rows/batch.")
    private SqlProcessingStrategy processingStrategy;
    @UriParam(label = "advanced",
              description = "Allows to plugin to use a custom org.apache.camel.component.sql.SqlPrepareStatementStrategy to control preparation of the query and prepared statement.")
    private SqlPrepareStatementStrategy prepareStatementStrategy;
    @UriParam(label = "consumer",
              description = "After processing each row then this query can be executed, if the Exchange was processed successfully, for example to mark the row as processed. The query can have"
                            + " parameter.")
    private String onConsume;
    @UriParam(label = "consumer",
              description = "After processing each row then this query can be executed, if the Exchange failed, for example to mark the row as failed. The query can have parameter.")
    private String onConsumeFailed;
    @UriParam(label = "consumer",
              description = "After processing the entire batch, this query can be executed to bulk update rows etc. The query cannot have parameters.")
    private String onConsumeBatchComplete;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Sets how resultset should be delivered to route. Indicates delivery as either a list or individual object. defaults to true.")
    private boolean useIterator = true;
    @UriParam(label = "consumer",
              description = "Sets whether empty resultset should be allowed to be sent to the next hop. Defaults to false. So the empty resultset will be filtered out.")
    private boolean routeEmptyResultSet;
    @UriParam(label = "consumer", defaultValue = "-1",
              description = "Sets an expected update count to validate when using onConsume.")
    private int expectedUpdateCount = -1;
    @UriParam(label = "consumer", description = "Sets whether to break batch if onConsume failed.")
    private boolean breakBatchOnConsumeFail;
    @UriParam(defaultValue = "true", description = "Whether to allow using named parameters in the queries.")
    private boolean allowNamedParameters = true;
    @UriParam(label = "advanced",
              description = "If enabled then the populateStatement method from org.apache.camel.component.sql.SqlPrepareStatementStrategy is always invoked, "
                            + "also if there is no expected parameters to be prepared. When this is false then the populateStatement is only invoked if there is 1"
                            + " or more expected parameters to be set; for example this avoids reading the message body/headers for SQL queries with no parameters.")
    private boolean alwaysPopulateStatement;
    @UriParam(defaultValue = ",",
              description = "The separator to use when parameter values is taken from message body (if the body is a String type), to be inserted at # placeholders."
                            + " Notice if you use named parameters, then a Map type is used instead. The default value is comma")
    private char separator = ',';
    @UriParam(defaultValue = "SelectList",
              description = "Make the output of consumer or producer to SelectList as List of Map, or SelectOne as single Java object in the following way:"
                            + " a) If the query has only single column, then that JDBC Column object is returned. (such as SELECT COUNT( * ) FROM PROJECT will return a Long object."
                            + " b) If the query has more than one column, then it will return a Map of that result."
                            + " c) If the outputClass is set, then it will convert the query result into an Java bean object by calling all the setters that match the column names."
                            + " It will assume your class has a default constructor to create instance with."
                            + " d) If the query resulted in more than one rows, it throws an non-unique result exception."
                            + " StreamList streams the result of the query using an Iterator. This can be used with the Splitter EIP in streaming mode to process the ResultSet in streaming fashion.")
    private SqlOutputType outputType = SqlOutputType.SelectList;
    @UriParam(description = "Specify the full package and class name to use as conversion when outputType=SelectOne.")
    private String outputClass;
    @UriParam(label = "advanced",
              description = "If set greater than zero, then Camel will use this count value of parameters to replace instead of"
                            + " querying via JDBC metadata API. This is useful if the JDBC vendor could not return correct parameters count, then user may override instead.")
    private int parametersCount;
    @UriParam(label = "producer",
              description = "If set, will ignore the results of the SQL query and use the existing IN message as the OUT message for the continuation of processing")
    private boolean noop;
    @UriParam(description = "Store the query result in a header instead of the message body. By default, outputHeader == null and the query result is stored"
                            + " in the message body, any existing content in the message body is discarded. If outputHeader is set, the value is used as the name of the header"
                            + " to store the query result and the original message body is preserved.")
    private String outputHeader;
    @UriParam(label = "producer",
              description = "Whether to use the message body as the SQL and then headers for parameters. If this option is enabled then the SQL in the uri is not used."
                            + " Note that query parameters in the message body are represented by a question mark instead of a <tt>#</tt> symbol.")
    private boolean useMessageBodyForSql;
    @UriParam(label = "advanced", defaultValue = "#",
              description = "Specifies a character that will be replaced to ? in SQL query."
                            + " Notice, that it is simple String.replaceAll() operation and no SQL parsing is involved (quoted strings will also change).")
    private String placeholder = "#";
    @UriParam(label = "advanced", defaultValue = "true",
              description = "Sets whether to use placeholder and replace all placeholder characters with ? sign in the SQL queries.")
    private boolean usePlaceholder = true;
    @UriParam(label = "advanced", prefix = "template.", multiValue = true,
              description = "Configures the Spring JdbcTemplate with the key/values from the Map")
    private Map<String, Object> templateOptions;
    @UriParam(label = "advanced",
              description = "Factory for creating RowMapper")
    private RowMapperFactory rowMapperFactory;

    public DefaultSqlEndpoint() {
    }

    public DefaultSqlEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isTransacted() {
        return transacted;
    }

    /**
     * Enables or disables transaction. If enabled, then if processing an exchange failed, then the consumer + break out
     * processing any further exchanges to cause a rollback eager
     */
    public void setTransacted(boolean transacted) {
        this.transacted = transacted;
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
     * Allows plugging in a custom org.apache.camel.component.sql.SqlProcessingStrategy to execute queries when the
     * consumer has processed the rows/batch.
     */
    public void setProcessingStrategy(SqlProcessingStrategy processingStrategy) {
        this.processingStrategy = processingStrategy;
    }

    public SqlPrepareStatementStrategy getPrepareStatementStrategy() {
        return prepareStatementStrategy;
    }

    /**
     * Allows plugging in a custom org.apache.camel.component.sql.SqlPrepareStatementStrategy to control preparation of
     * the query and prepared statement.
     */
    public void setPrepareStatementStrategy(SqlPrepareStatementStrategy prepareStatementStrategy) {
        this.prepareStatementStrategy = prepareStatementStrategy;
    }

    public String getOnConsume() {
        return onConsume;
    }

    /**
     * After processing each row, then this query can be executed, if the Exchange was processed successfully, for
     * example, to mark the row as processed. The query can have parameter.
     */
    public void setOnConsume(String onConsume) {
        this.onConsume = onConsume;
    }

    public String getOnConsumeFailed() {
        return onConsumeFailed;
    }

    /**
     * After processing each row, then this query can be executed, if the Exchange failed, for example, to mark the row
     * as failed. The query can have parameter.
     */
    public void setOnConsumeFailed(String onConsumeFailed) {
        this.onConsumeFailed = onConsumeFailed;
    }

    public String getOnConsumeBatchComplete() {
        return onConsumeBatchComplete;
    }

    /**
     * After processing the entire batch, this query can be executed to bulk update rows etc. The query cannot have
     * parameters.
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
     * If enabled, then the populateStatement method from org.apache.camel.component.sql.SqlPrepareStatementStrategy is
     * always invoked, also if there are no expected parameters to be prepared. When this is false, then the
     * populateStatement is only invoked if there are one or more expected parameters to be set; for example, this
     * avoids reading the message body/headers for SQL queries with no parameters.
     */
    public void setAlwaysPopulateStatement(boolean alwaysPopulateStatement) {
        this.alwaysPopulateStatement = alwaysPopulateStatement;
    }

    public char getSeparator() {
        return separator;
    }

    /**
     * The separator to use when parameter values are taken from message body (if the body is a String type), to be
     * inserted at # placeholders. Notice if you use named parameters, then a Map type is used instead.
     * <p/>
     * The default value is comma.
     */
    public void setSeparator(char separator) {
        this.separator = separator;
    }

    public SqlOutputType getOutputType() {
        return outputType;
    }

    /**
     * Make the output of consumer or producer to SelectList as List of Map, or SelectOne as a single Java object in the
     * following way: a) If the query has only a single column, then that JDBC Column object is returned. (such as
     * SELECT COUNT( * ) FROM PROJECT will return a Long object. b) If the query has more than one column, then it will
     * return a Map of that result. c) If the outputClass is set, then it will convert the query result into a Java bean
     * object by calling all the setters that match the column names. It will assume your class has a default
     * constructor to create instance with. d) If the query resulted in more than one rows, it throws a non-unique
     * result exception.
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
     * If set greater than zero, then Camel will use this count value of parameters to replace instead of querying via
     * JDBC metadata API. This is useful if the JDBC vendor could not return the correct parameters count, then the user
     * may override instead.
     */
    public void setParametersCount(int parametersCount) {
        this.parametersCount = parametersCount;
    }

    public boolean isNoop() {
        return noop;
    }

    /**
     * If set, will ignore the results of the SQL query and use the existing IN message as the OUT message for the
     * continuation of processing
     */
    public void setNoop(boolean noop) {
        this.noop = noop;
    }

    public String getOutputHeader() {
        return outputHeader;
    }

    /**
     * Store the query result in a header instead of the message body. By default, outputHeader is null, and the query
     * result is stored in the message body, any existing content in the message body is discarded. If outputHeader is
     * set, the value is used as the name of the header to store the query result and the original message body is
     * preserved.
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
     * If this option is enabled, then the SQL in the uri is not used.
     */
    public void setUseMessageBodyForSql(boolean useMessageBodyForSql) {
        this.useMessageBodyForSql = useMessageBodyForSql;
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
     * Sets how resultset should be delivered to route. Indicates delivery as either a list or individual object.
     * defaults to true.
     */
    public void setUseIterator(boolean useIterator) {
        this.useIterator = useIterator;
    }

    public boolean isRouteEmptyResultSet() {
        return routeEmptyResultSet;
    }

    /**
     * Sets whether empty resultset should be allowed to be sent to the next hop. Defaults to false. So the empty
     * resultset will be filtered out.
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

    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * Specifies a character that will be replaced to ? in SQL query. Notice that it is a simple String.replaceAll()
     * operation and no SQL parsing is involved (quoted strings will also change).
     */
    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public boolean isUsePlaceholder() {
        return usePlaceholder;
    }

    /**
     * Sets whether to use placeholder and replace all placeholder characters with ? sign in the SQL queries.
     * <p/>
     * This option is default <tt>true</tt>
     */
    public void setUsePlaceholder(boolean usePlaceholder) {
        this.usePlaceholder = usePlaceholder;
    }

    public Map<String, Object> getTemplateOptions() {
        return templateOptions;
    }

    /**
     * Configures the Spring JdbcTemplate with the key/values from the Map
     */
    public void setTemplateOptions(Map<String, Object> templateOptions) {
        this.templateOptions = templateOptions;
    }

    public RowMapperFactory getRowMapperFactory() {
        return rowMapperFactory;
    }

    /**
     * Factory for creating RowMapper
     */
    public void setRowMapperFactory(RowMapperFactory rowMapperFactory) {
        this.rowMapperFactory = rowMapperFactory;
    }

    @SuppressWarnings("unchecked")
    public List<?> queryForList(ResultSet rs, boolean allowMapToClass) throws SQLException {
        if (allowMapToClass && outputClass != null) {
            Class<?> outputClazz = getCamelContext().getClassResolver().resolveClass(outputClass);
            RowMapper<?> rowMapper = rowMapperFactory.newBeanRowMapper(outputClazz);
            RowMapperResultSetExtractor<?> mapper = new RowMapperResultSetExtractor<>(rowMapper);
            return mapper.extractData(rs);
        } else {
            RowMapper rowMapper = rowMapperFactory.newColumnRowMapper();
            RowMapperResultSetExtractor<Map<String, Object>> mapper = new RowMapperResultSetExtractor<>(rowMapper);
            return mapper.extractData(rs);
        }
    }

    public Object queryForObject(ResultSet rs) throws SQLException {
        Object result = null;
        if (outputClass == null) {
            RowMapper rowMapper = rowMapperFactory.newColumnRowMapper();
            RowMapperResultSetExtractor<Map<String, Object>> mapper
                    = new RowMapperResultSetExtractor<Map<String, Object>>(rowMapper);
            List<Map<String, Object>> data = mapper.extractData(rs);
            if (data.size() > 1) {
                throw new SQLDataException(
                        "Query result not unique for outputType=SelectOne. Got " + data.size() + " count instead.");
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
            Class<?> outputClazz = getCamelContext().getClassResolver().resolveClass(outputClass);
            RowMapper<?> rowMapper = rowMapperFactory.newBeanRowMapper(outputClazz);
            RowMapperResultSetExtractor<?> mapper = new RowMapperResultSetExtractor<>(rowMapper);
            List<?> data = mapper.extractData(rs);
            if (data.size() > 1) {
                throw new SQLDataException(
                        "Query result not unique for outputType=SelectOne. Got " + data.size() + " count instead.");
            } else if (data.size() == 1) {
                result = data.get(0);
            }
        }

        // If data.size is zero, let result be null.
        return result;
    }

    public ResultSetIterator queryForStreamList(Connection connection, Statement statement, ResultSet rs) throws SQLException {
        if (outputClass == null) {
            RowMapper<?> rowMapper = rowMapperFactory.newColumnRowMapper();
            return new ResultSetIterator(connection, statement, rs, rowMapper);
        } else {
            Class<?> outputClazz = getCamelContext().getClassResolver().resolveClass(outputClass);
            RowMapper<?> rowMapper = rowMapperFactory.newBeanRowMapper(outputClazz);
            return new ResultSetIterator(connection, statement, rs, rowMapper);
        }
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (rowMapperFactory == null) {
            rowMapperFactory = new DefaultRowMapperFactory();
        }
    }
}
